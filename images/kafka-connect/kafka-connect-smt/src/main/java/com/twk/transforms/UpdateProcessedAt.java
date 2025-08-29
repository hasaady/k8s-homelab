package com.twk.transforms;

import org.apache.kafka.common.config.ConfigDef;
import org.apache.kafka.connect.connector.ConnectRecord;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.SchemaBuilder;
import org.apache.kafka.connect.data.Struct;
import org.apache.kafka.connect.header.Header;
import org.apache.kafka.connect.transforms.Transformation;
import org.apache.kafka.connect.errors.ConnectException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Map;

public class UpdateProcessedAt<R extends ConnectRecord<R>> implements Transformation<R> {

    private static final Logger log = LoggerFactory.getLogger(UpdateProcessedAt.class);

    // Hard-coded header name
    private static final String ID_HEADER_NAME = "id";

    // Configuration definition (empty since we don't need any config)
    public static final ConfigDef CONFIG_DEF = new ConfigDef();

    private Schema updateSchema;

    @Override
    public void configure(Map<String, ?> props) {
        // Create schema for the update record
        this.updateSchema = SchemaBuilder.struct()
                .name("UpdateProcessedAt")
                .field("Id", Schema.STRING_SCHEMA)
                .field("ProcessedAt", Schema.STRING_SCHEMA)  // Using string for DATETIME2 compatibility
                .build();
    }

    @Override
    public R apply(R record) {

        if (record == null) {
            return record;
        }

        try {
            // Extract ID from header
            String id = getIdFromHeader(record);
            if (id == null || id.trim().isEmpty()) {
                log.debug("No ID found in header 'id', skipping record");
                return null; // Skip this record
            }

            // Create new record with only ID and ProcessedAt
            Struct updateStruct = new Struct(updateSchema);
            updateStruct.put("Id", id);
            updateStruct.put("ProcessedAt", Instant.now().toString()); // ISO-8601 format

            // Return new record with update data
            return record.newRecord(
                    record.topic(),
                    record.kafkaPartition(),
                    record.keySchema(),
                    record.key(),
                    updateSchema,
                    updateStruct,
                    record.timestamp()
            );

        } catch (Exception e) {
            log.error("Error creating ProcessedAt update record", e);
            throw new ConnectException("Failed to create ProcessedAt update record", e);
        }
    }

    private String getIdFromHeader(R record) {
        if (record.headers() == null) {
            return null;
        }

        Header idHeader = record.headers().lastWithName(ID_HEADER_NAME);
        if (idHeader == null) {
            return null;
        }

        Object value = idHeader.value();
        return value != null ? value.toString() : null;
    }

    @Override
    public ConfigDef config() {
        return CONFIG_DEF;
    }

    @Override
    public void close() {
        log.info("UpdateProcessedAt closed");
    }
}