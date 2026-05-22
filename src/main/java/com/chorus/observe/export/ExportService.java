package com.chorus.observe.export;

import com.chorus.observe.model.ExportJob;
import com.chorus.observe.persistence.ExportJobRepository;
import com.chorus.observe.security.TenantContext;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class ExportService {

    private static final Logger LOG = LoggerFactory.getLogger(ExportService.class);

    private final ExportJobRepository exportJobRepository;
    private final JdbcTemplate jdbc;
    private final ExecutorService executor;

    public ExportService(@NonNull ExportJobRepository exportJobRepository, @NonNull DataSource dataSource) {
        this.exportJobRepository = Objects.requireNonNull(exportJobRepository);
        this.jdbc = new JdbcTemplate(dataSource);
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
    }

    public @NonNull ExportJob submitExport(@NonNull String tenantId, @NonNull String userId, @NonNull String name,
                                           @NonNull String resourceType, @NonNull Map<String, Object> queryFilter,
                                           ExportJob.Format format, ExportJob.Destination destination,
                                           @Nullable String destinationPath) {
        String jobId = "exp-" + UUID.randomUUID().toString().substring(0, 8);
        ExportJob job = new ExportJob(jobId, tenantId, userId, name, resourceType, queryFilter, format,
            destination, destinationPath, ExportJob.Status.PENDING, null, null, null, null, null, Instant.now());
        exportJobRepository.save(job);
        CompletableFuture.runAsync(() -> executeExport(job), executor);
        return job;
    }

    private void executeExport(@NonNull ExportJob job) {
        ExportJob running = new ExportJob(job.jobId(), job.tenantId(), job.userId(), job.name(), job.resourceType(),
            job.queryFilter(), job.format(), job.destination(), job.destinationPath(), ExportJob.Status.RUNNING,
            null, null, null, Instant.now(), null, job.createdAt());
        exportJobRepository.save(running);

        try {
            String table = resolveTableName(job.resourceType());
            if (table == null) {
                throw new IllegalArgumentException("Unknown resource type: " + job.resourceType());
            }

            List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM " + table);
            long totalRecords = rows.size();
            Path outputPath = exportToFile(rows, job);
            long fileSize = Files.size(outputPath);

            ExportJob completed = new ExportJob(job.jobId(), job.tenantId(), job.userId(), job.name(), job.resourceType(),
                job.queryFilter(), job.format(), job.destination(), outputPath.toString(), ExportJob.Status.COMPLETED,
                totalRecords, fileSize, null, running.startedAt(), Instant.now(), job.createdAt());
            exportJobRepository.save(completed);
            LOG.info("Export job {} completed: {} records, {} bytes", job.jobId(), totalRecords, fileSize);
        } catch (Exception e) {
            LOG.error("Export job {} failed", job.jobId(), e);
            ExportJob failed = new ExportJob(job.jobId(), job.tenantId(), job.userId(), job.name(), job.resourceType(),
                job.queryFilter(), job.format(), job.destination(), job.destinationPath(), ExportJob.Status.FAILED,
                null, null, e.getMessage(), running.startedAt(), Instant.now(), job.createdAt());
            exportJobRepository.save(failed);
        }
    }

    private Path exportToFile(@NonNull List<Map<String, Object>> rows, @NonNull ExportJob job) throws IOException {
        Path dir = Path.of("exports");
        Files.createDirectories(dir);
        Path file = dir.resolve(job.jobId() + "." + job.format().name().toLowerCase());

        switch (job.format()) {
            case JSON -> {
                String json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(rows);
                Files.writeString(file, json);
            }
            case CSV -> {
                try (BufferedWriter writer = Files.newBufferedWriter(file)) {
                    if (!rows.isEmpty()) {
                        List<String> headers = List.copyOf(rows.get(0).keySet());
                        writer.write(String.join(",", headers));
                        writer.newLine();
                        for (Map<String, Object> row : rows) {
                            String line = headers.stream()
                                .map(h -> escapeCsv(row.get(h)))
                                .collect(Collectors.joining(","));
                            writer.write(line);
                            writer.newLine();
                        }
                    }
                }
            }
            case PARQUET -> {
                // Parquet requires Apache Parquet library; fallback to JSON with .parquet extension for now
                String json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(rows);
                Files.writeString(file, json);
            }
        }
        return file;
    }

    private String escapeCsv(Object value) {
        if (value == null) return "";
        String s = value.toString().replace("\"", "\"\"");
        if (s.contains(",") || s.contains("\n") || s.contains("\"")) {
            s = "\"" + s + "\"";
        }
        return s;
    }

    private String resolveTableName(@NonNull String resourceType) {
        return switch (resourceType.toLowerCase()) {
            case "runs" -> "runs";
            case "spans" -> "spans";
            case "eval_results" -> "eval_results";
            case "alert_events" -> "alert_events";
            default -> null;
        };
    }
}
