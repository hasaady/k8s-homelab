package com.twk.transforms;

import org.apache.kafka.common.config.ConfigDef;
import org.apache.kafka.connect.connector.ConnectRecord;
import org.apache.kafka.connect.data.*;
import org.apache.kafka.connect.header.Header;
import org.apache.kafka.connect.transforms.Transformation;
import org.apache.kafka.connect.transforms.util.SimpleConfig;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.*;
import java.util.stream.Collectors;

public class ExtractMultipleHeaders<R extends ConnectRecord<R>> implements Transformation<R> {

    private static final String HEADERS_TO_EXTRACT_CONFIG = "headers.to.extract";
    private static final String HEADER_RENAME_MAPPING_CONFIG = "headers.rename.mapping";
    private static final String WRAP_VALUE_CONFIG = "wrap.value";
    private static final String PAYLOAD_FIELD_NAME_CONFIG = "payload.field.name";

    private static final ConfigDef CONFIG_DEF = new ConfigDef()
            .define(HEADERS_TO_EXTRACT_CONFIG, ConfigDef.Type.LIST, ConfigDef.Importance.HIGH, "Comma-separated list of headers to extract")
            .define(HEADER_RENAME_MAPPING_CONFIG, ConfigDef.Type.STRING, "", ConfigDef.Importance.MEDIUM, "Mapping of header names (e.g., 'id:EventId,source:SourceTopic')")
            .define(WRAP_VALUE_CONFIG, ConfigDef.Type.BOOLEAN, false, ConfigDef.Importance.MEDIUM, "Wrap original value inside a custom field (true/false)")
            .define(PAYLOAD_FIELD_NAME_CONFIG, ConfigDef.Type.STRING, "Payload", ConfigDef.Importance.MEDIUM, "Custom name for the wrapped payload field");

    private List<String> headersToExtract;
    private Map<String, String> headerRenameMap;
    private boolean wrapValue;
    private String payloadFieldName;
    private Schema cachedSchema = null; // Schema caching for performance
    private static final ObjectMapper objectMapper = new ObjectMapper(); // JSON Serializer

    @Override
    public void configure(Map<String, ?> configs) {
        SimpleConfig config = new SimpleConfig(CONFIG_DEF, configs);
        headersToExtract = config.getList(HEADERS_TO_EXTRACT_CONFIG);
        wrapValue = config.getBoolean(WRAP_VALUE_CONFIG);
        payloadFieldName = config.getString(PAYLOAD_FIELD_NAME_CONFIG);

        // Parse header rename mappings (e.g., "id:EventId,source:SourceTopic")
        String renameMappings = config.getString(HEADER_RENAME_MAPPING_CONFIG);
        headerRenameMap = new HashMap<>();
        if (!renameMappings.isEmpty()) {
            headerRenameMap = parseRenameMappings(renameMappings);
        }
    }

    @Override
    public R apply(R record) {
        if (record == null || record.value() == null) {
            return record;
        }

        // Extract specified headers dynamically and apply renaming if configured
        Map<String, String> extractedHeaders = new HashMap<>();
        for (String headerName : headersToExtract) {
            String fieldName = headerRenameMap.getOrDefault(headerName, headerName); // Use mapped name if available
            extractedHeaders.put(fieldName, extractHeaderValue(record, headerName));
        }

        // Convert Struct (Payload) to JSON if needed
        String payloadString;
        if (record.value() instanceof Struct) {
            try {
                payloadString = objectMapper.writeValueAsString(structToMap((Struct) record.value()));
            } catch (Exception e) {
                throw new RuntimeException("Failed to convert Struct to JSON", e);
            }
        } else {
            payloadString = record.value().toString();
        }

        // Cache schema if structure is unchanged
        if (cachedSchema == null) {
            SchemaBuilder schemaBuilder = SchemaBuilder.struct().name("UpdatedRecord");

            // Add extracted headers
            extractedHeaders.forEach((key, value) -> schemaBuilder.field(key, Schema.STRING_SCHEMA));

            // Store Payload as JSON string
            schemaBuilder.field(payloadFieldName, Schema.STRING_SCHEMA);

            cachedSchema = schemaBuilder.build();
        }

        // Build new Struct
        Struct updatedValue = new Struct(cachedSchema);
        extractedHeaders.forEach(updatedValue::put); // Add extracted headers
        updatedValue.put(payloadFieldName, payloadString); // Store the converted value as a JSON string

        return record.newRecord(
                record.topic(),
                record.kafkaPartition(),
                record.keySchema(), record.key(),
                cachedSchema, updatedValue,
                record.timestamp(), record.headers()
        );
    }

    private String extractHeaderValue(R record, String headerName) {
        Header header = record.headers().lastWithName(headerName);
        return (header != null && header.value() != null) ? header.value().toString() : "";
    }

    private Map<String, Object> structToMap(Struct struct) {
        Map<String, Object> map = new HashMap<>();
        for (Field field : struct.schema().fields()) {
            map.put(field.name(), struct.get(field));
        }
        return map;
    }

    private Map<String, String> parseRenameMappings(String mappings) {
        return Arrays.stream(mappings.split(","))
                .map(entry -> entry.split(":"))
                .filter(pair -> pair.length == 2)
                .collect(Collectors.toMap(pair -> pair[0].trim(), pair -> pair[1].trim()));
    }

    @Override
    public ConfigDef config() {
        return CONFIG_DEF;
    }

    @Override
    public void close() {
        // No-op
    }
}
