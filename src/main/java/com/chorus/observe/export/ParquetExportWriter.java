package com.chorus.observe.export;

import com.jerolba.carpet.CarpetWriter;
import org.apache.parquet.hadoop.metadata.CompressionCodecName;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Writes Java records to Apache Parquet files using carpet-record.
 */
public class ParquetExportWriter {

    private static final Logger LOG = LoggerFactory.getLogger(ParquetExportWriter.class);

    private final SchemaRegistry schemaRegistry;

    public ParquetExportWriter(@NonNull SchemaRegistry schemaRegistry) {
        this.schemaRegistry = Objects.requireNonNull(schemaRegistry);
    }

    public <T> @NonNull Path write(@NonNull Path outputPath, @NonNull String schemaName, @NonNull List<T> records) throws IOException {
        String schemaVersion = schemaRegistry.schemaVersionKey(schemaName);

        Files.createDirectories(outputPath.getParent());

        try (OutputStream out = Files.newOutputStream(outputPath)) {
            CarpetWriter.Builder<T> builder = new CarpetWriter.Builder<>(out, inferClass(records));

            try (CarpetWriter<T> writer = builder.build()) {
                writer.write(records);
            }
        }

        // Write sidecar metadata file for schema_version tracking
        Path metaPath = outputPath.resolveSibling(outputPath.getFileName() + ".meta.json");
        String metaJson = "{\"schema_version\":\"" + schemaVersion + "\",\"record_count\":" + records.size() + "}";
        Files.writeString(metaPath, metaJson);

        LOG.info("Wrote Parquet file: {} (schema: {}, records: {})", outputPath, schemaVersion, records.size());
        return outputPath;
    }

    @SuppressWarnings("unchecked")
    private <T> @NonNull Class<T> inferClass(@NonNull List<T> records) {
        if (records.isEmpty()) {
            throw new IllegalArgumentException("Cannot infer class from empty record list");
        }
        return (Class<T>) records.get(0).getClass();
    }
}
