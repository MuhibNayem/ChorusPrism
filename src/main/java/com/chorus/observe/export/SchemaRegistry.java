package com.chorus.observe.export;

import org.jspecify.annotations.NonNull;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for export Parquet schemas with compatibility tracking.
 */
public class SchemaRegistry {

    private final Map<String, RegisteredSchema> schemas = new ConcurrentHashMap<>();

    public void register(@NonNull String schemaName, int version, @NonNull Class<?> recordClass, @NonNull Compatibility compatibility) {
        schemas.put(schemaName, new RegisteredSchema(schemaName, version, recordClass.getName(), compatibility));
    }

    public @NonNull String schemaVersionKey(@NonNull String schemaName) {
        RegisteredSchema rs = schemas.get(schemaName);
        if (rs == null) {
            throw new IllegalArgumentException("Unknown schema: " + schemaName);
        }
        return schemaName + "V" + rs.version;
    }

    public @NonNull RegisteredSchema get(@NonNull String schemaName) {
        return Objects.requireNonNull(schemas.get(schemaName), "Unknown schema: " + schemaName);
    }

    public enum Compatibility { FULL, BACKWARD, NONE }

    public record RegisteredSchema(
        @NonNull String name,
        int version,
        @NonNull String recordClassName,
        @NonNull Compatibility compatibility
    ) {}
}
