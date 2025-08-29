package com.twk.transforms;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.confluent.connect.avro.AvroData;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.kafka.common.config.ConfigDef;
import org.apache.kafka.connect.connector.ConnectRecord;
import org.apache.kafka.connect.data.Field;
import org.apache.kafka.connect.data.Struct;
import org.apache.kafka.connect.errors.ConnectException;
import org.apache.kafka.connect.header.ConnectHeaders;
import org.apache.kafka.connect.transforms.Transformation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.rest.exceptions.RestClientException;
import org.apache.kafka.connect.transforms.util.SimpleConfig;

public class OutboxEventRouter<R extends ConnectRecord<R>> implements Transformation<R> {

    private static final Logger log = LoggerFactory.getLogger(OutboxEventRouter.class);

    private static final String ID_FIELD = "Id";
    private static final String TOPIC_FIELD = "Topic";
    private static final String PAYLOAD_FIELD = "Payload";
    private static final String KEY_FIELD = "Key";
    private static final String PAYLOAD_TYPE_FIELD = "PayloadType";
    private static final String TRACE_FIELD = "Trace";

    private static final String SCHEMA_REGISTRY_URL_CONFIG = "schema.registry.url";
    private static final String SCHEMA_REGISTRY_USERNAME = "schema.registry.username";
    private static final String SCHEMA_REGISTRY_PASSWORD = "schema.registry.password";
    private static final String SCHEMA_REGISTRY_TRUSTSTORE_LOCATION = "schema.registry.ssl.truststore.location";
    private static final String SCHEMA_REGISTRY_TRUSTSTORE_PASSWORD = "schema.registry.ssl.truststore.password";

    public static final ConfigDef CONFIG_DEF = new ConfigDef()
            .define(SCHEMA_REGISTRY_URL_CONFIG, ConfigDef.Type.STRING, ConfigDef.Importance.HIGH, "Schema Registry URL")
            .define(SCHEMA_REGISTRY_USERNAME, ConfigDef.Type.STRING, null, ConfigDef.Importance.MEDIUM, "Schema Registry username")
            .define(SCHEMA_REGISTRY_PASSWORD, ConfigDef.Type.STRING, null, ConfigDef.Importance.MEDIUM, "Schema Registry password")
            .define(SCHEMA_REGISTRY_TRUSTSTORE_LOCATION, ConfigDef.Type.STRING, null, ConfigDef.Importance.MEDIUM, "Schema Registry SSL Truststore Location")
            .define(SCHEMA_REGISTRY_TRUSTSTORE_PASSWORD, ConfigDef.Type.STRING, null, ConfigDef.Importance.MEDIUM, "Schema Registry SSL Truststore Password");

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AvroData avroData = new AvroData(1000);
    private final Map<String, Schema> schemaCache = new ConcurrentHashMap<>();

    private SchemaRegistryClient schemaRegistryClient;

    @Override
    public void configure(Map<String, ?> props) {
        SimpleConfig config = new SimpleConfig(CONFIG_DEF, props);
        String registryUrl = config.getString(SCHEMA_REGISTRY_URL_CONFIG);

        Map<String, Object> clientConfig = new HashMap<>();

        String username = config.getString(SCHEMA_REGISTRY_USERNAME);
        String password = config.getString(SCHEMA_REGISTRY_PASSWORD);

        if (username != null && password != null) {
            clientConfig.put("basic.auth.credentials.source", "USER_INFO");
            clientConfig.put("basic.auth.user.info", username + ":" + password);
        }

        String truststoreLocation = config.getString(SCHEMA_REGISTRY_TRUSTSTORE_LOCATION);
        if (truststoreLocation != null) {
            clientConfig.put("schema.registry.ssl.truststore.location", truststoreLocation);
        }

        String truststorePassword = config.getString(SCHEMA_REGISTRY_TRUSTSTORE_PASSWORD);
        if (truststorePassword != null) {
            clientConfig.put("schema.registry.ssl.truststore.password", truststorePassword);
        }

        this.schemaRegistryClient = new CachedSchemaRegistryClient(registryUrl, 1000, clientConfig);

        log.info("OutboxEventRouter configured with schema registry URL: {}", registryUrl);
    }

    @Override
    public R apply(R record) {
        if (record.value() == null) {
            return record;
        }

        try {
            Struct outboxStruct = (Struct) record.value();

            String topic = getFieldValue(outboxStruct, TOPIC_FIELD);
            String id = getFieldValue(outboxStruct, ID_FIELD);
            String payloadType = getFieldValue(outboxStruct, PAYLOAD_TYPE_FIELD);
            String payload = getFieldValue(outboxStruct, PAYLOAD_FIELD);
            String trace = getFieldValue(outboxStruct, TRACE_FIELD);
            String key = getFieldValue(outboxStruct, KEY_FIELD);

            if (topic == null || payloadType == null || payload == null) {
                log.warn("Skipping record with missing required fields: topic={}, payloadType={}, payload={}", topic, payloadType, payload != null ? "present" : "null");
                return record;
            }

            Schema avroSchema = fetchAvroSchema(topic, payloadType);
            Struct payloadStruct = jsonToStruct(payload, avroSchema);

            ConnectHeaders headers = new ConnectHeaders();
            if (id != null) headers.addString("id", id);
            if (trace != null) headers.addString("trace", trace);
            if (payloadType != null) headers.addString("payload-type", payloadType);

            return record.newRecord(
                    topic,
                    record.kafkaPartition(),
                    org.apache.kafka.connect.data.Schema.OPTIONAL_STRING_SCHEMA,
                    key,
                    payloadStruct.schema(),
                    payloadStruct,
                    record.timestamp(),
                    headers
            );

        } catch (Exception e) {
            log.error("Error processing outbox record", e);
            throw new ConnectException("Failed to process outbox record", e);
        }
    }

    private String getFieldValue(Struct struct, String fieldName) {
        Field field = struct.schema().field(fieldName);
        if (field == null) return null;
        Object value = struct.get(field);
        return value != null ? value.toString() : null;
    }

    private Schema fetchAvroSchema(String topic, String payloadType) {
        String subject = topic + "-value"; // TopicNameStrategy
        return schemaCache.computeIfAbsent(subject, key -> {
            try {
                String schemaStr = schemaRegistryClient.getLatestSchemaMetadata(key).getSchema();
                return new Schema.Parser().parse(schemaStr);
            } catch (IOException | RestClientException e) {
                throw new ConnectException("Failed to fetch Avro schema for subject: " + key, e);
            }
        });
    }

    private Struct jsonToStruct(String jsonString, Schema avroSchema) {
        try {
            JsonNode jsonNode = objectMapper.readTree(jsonString);

            GenericRecord avroRecord = new GenericData.Record(avroSchema);
            for (Schema.Field field : avroSchema.getFields()) {
                JsonNode fieldValue = jsonNode.get(field.name());
                avroRecord.put(field.name(), parseAvroValue(field.schema(), fieldValue));
            }

            // Convert Avro GenericRecord to Kafka Connect Struct
            return (Struct) avroData.toConnectData(avroSchema, avroRecord).value();

        } catch (Exception e) {
            throw new ConnectException("Failed to convert JSON to Struct using Avro schema", e);
        }
    }

    private Object parseAvroValue(Schema fieldSchema, JsonNode jsonNode) {
        Schema.Type type = fieldSchema.getType();

        if (type == Schema.Type.UNION) {
            for (Schema subSchema : fieldSchema.getTypes()) {
                if (subSchema.getType() == Schema.Type.NULL) {
                    if (jsonNode == null || jsonNode.isNull()) {
                        return null;
                    }
                } else {
                    return parseAvroValue(subSchema, jsonNode);
                }
            }
            throw new ConnectException("Could not resolve UNION schema for value: " + jsonNode);
        }

        if (jsonNode == null || jsonNode.isNull()) return null;

        switch (type) {
            case STRING: return jsonNode.asText();
            case INT: return jsonNode.asInt();
            case LONG: return jsonNode.asLong();
            case FLOAT: return (float) jsonNode.asDouble();
            case DOUBLE: return jsonNode.asDouble();
            case BOOLEAN: return jsonNode.asBoolean();
            case BYTES:
                try {
                    return jsonNode.binaryValue();
                } catch (IOException e) {
                    throw new RuntimeException("Invalid byte field", e);
                }
            default:
                throw new UnsupportedOperationException("Unsupported Avro type: " + type);
        }
    }

    @Override
    public ConfigDef config() {
        return CONFIG_DEF;
    }

    @Override
    public void close() {
        schemaCache.clear();
        log.info("OutboxEventRouter closed and schema cache cleared");
    }
}