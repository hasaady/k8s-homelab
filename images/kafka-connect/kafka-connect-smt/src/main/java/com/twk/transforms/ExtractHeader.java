package com.twk.transforms;

import org.apache.kafka.common.config.ConfigDef;
import org.apache.kafka.connect.connector.ConnectRecord;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.SchemaBuilder;
import org.apache.kafka.connect.data.Struct;
import org.apache.kafka.connect.transforms.Transformation;
import org.apache.kafka.connect.transforms.util.SimpleConfig;

import java.util.Map;

public class ExtractHeader<R extends ConnectRecord<R>> implements Transformation<R> {
    private static final String HEADER_NAME_CONFIG = "header.name";
    private static final String TARGET_FIELD_CONFIG = "target.field";

    private static final ConfigDef CONFIG_DEF = new ConfigDef()
            .define(HEADER_NAME_CONFIG, ConfigDef.Type.STRING, ConfigDef.Importance.HIGH, "Kafka header name to extract")
            .define(TARGET_FIELD_CONFIG, ConfigDef.Type.STRING, ConfigDef.Importance.HIGH, "Target field name in the record");

    private String headerName;
    private String targetField;

    @Override
    public void configure(Map<String, ?> configs) {
        SimpleConfig config = new SimpleConfig(CONFIG_DEF, configs);
        headerName = config.getString(HEADER_NAME_CONFIG);
        targetField = config.getString(TARGET_FIELD_CONFIG);
    }

    @Override
    public R apply(R record) {
        if (record.headers().lastWithName(headerName) == null) {
            return record; // No header found, return as-is
        }

        String headerValue = record.headers().lastWithName(headerName).value().toString();

        // Add extracted header to the record value
        Schema updatedSchema = SchemaBuilder.struct()
                .name(record.valueSchema().name())
                .field(targetField, Schema.STRING_SCHEMA) // Set dynamically named field
                .build();

        Struct updatedValue = new Struct(updatedSchema)
                .put(targetField, headerValue); // Assign extracted header value

        return record.newRecord(
                record.topic(), record.kafkaPartition(),
                record.keySchema(), record.key(),
                updatedSchema, updatedValue,
                record.timestamp(), record.headers()
        );
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