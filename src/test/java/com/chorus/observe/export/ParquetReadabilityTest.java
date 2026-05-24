package com.chorus.observe.export;

import com.jerolba.carpet.CarpetReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ParquetReadabilityTest {

    @TempDir Path tempDir;

    @Test
    void parquetFileIsReadableAndContainsSchemaVersion() throws Exception {
        SchemaRegistry registry = new SchemaRegistry();
        registry.register("SpanExport", 1, SpanExportRecord.class, SchemaRegistry.Compatibility.BACKWARD);
        ParquetExportWriter writer = new ParquetExportWriter(registry);

        List<SpanExportRecord> records = List.of(
            new SpanExportRecord("span-1", "run-1", null, "test-span", "INTERNAL",
                Instant.parse("2024-01-01T00:00:00Z"), null, "{}", "[]", "OK", null, null, "tenant-1"),
            new SpanExportRecord("span-2", "run-1", "span-1", "child-span", "INTERNAL",
                Instant.parse("2024-01-01T00:00:01Z"), null, "{}", "[]", "OK", null, null, "tenant-1")
        );

        Path parquetFile = tempDir.resolve("test.parquet");
        writer.write(parquetFile, "SpanExport", records);

        // Read back with carpet-record (proves Java readability)
        List<SpanExportRecord> read = new CarpetReader<>(parquetFile.toFile(), SpanExportRecord.class).toList();
        assertThat(read).hasSize(2);
        assertThat(read.get(0).spanId()).isEqualTo("span-1");
        assertThat(read.get(1).spanId()).isEqualTo("span-2");

        // Verify sidecar metadata file exists and contains schema_version
        Path metaFile = tempDir.resolve("test.parquet.meta.json");
        assertThat(metaFile).exists();
        String meta = Files.readString(metaFile);
        assertThat(meta).contains("\"schema_version\":\"SpanExportV1\"");
        assertThat(meta).contains("\"record_count\":2");
    }
}
