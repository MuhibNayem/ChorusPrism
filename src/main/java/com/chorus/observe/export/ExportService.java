package com.chorus.observe.export;

import com.chorus.observe.model.ExportConfig;
import com.chorus.observe.model.ExportJob;
import com.chorus.observe.persistence.ExportConfigRepository;
import com.chorus.observe.persistence.ExportJobRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.stream.Collectors;

public class ExportService {

    private static final Logger LOG = LoggerFactory.getLogger(ExportService.class);

    private final ExportJobRepository exportJobRepository;
    private final JdbcTemplate pgJdbc;
    private final JdbcTemplate chJdbc;
    private final ParquetExportWriter parquetWriter;
    private final ExportQueryBuilder queryBuilder;
    private final ExportConfigRepository exportConfigRepository;
    private final S3ExportClient s3ExportClient;
    private final ObjectMapper mapper;

    public ExportService(
            @NonNull ExportJobRepository exportJobRepository,
            @Nullable DataSource pgDataSource,
            @Nullable DataSource chDataSource,
            @NonNull ParquetExportWriter parquetWriter,
            @NonNull ExportQueryBuilder queryBuilder,
            @NonNull ExportConfigRepository exportConfigRepository,
            @NonNull S3ExportClient s3ExportClient,
            @NonNull ObjectMapper mapper) {
        this.exportJobRepository = Objects.requireNonNull(exportJobRepository);
        this.pgJdbc = pgDataSource != null ? new JdbcTemplate(pgDataSource) : null;
        this.chJdbc = chDataSource != null ? new JdbcTemplate(chDataSource) : null;
        this.parquetWriter = Objects.requireNonNull(parquetWriter);
        this.queryBuilder = Objects.requireNonNull(queryBuilder);
        this.exportConfigRepository = Objects.requireNonNull(exportConfigRepository);
        this.s3ExportClient = Objects.requireNonNull(s3ExportClient);
        this.mapper = Objects.requireNonNull(mapper);
    }

    public @NonNull ExportJob submitExport(@NonNull String tenantId, @NonNull String userId, @NonNull String name,
                                           @NonNull String resourceType, @NonNull Map<String, Object> queryFilter,
                                           ExportJob.Format format, ExportJob.Destination destination,
                                           @Nullable String destinationPath) {
        String jobId = "exp-" + UUID.randomUUID().toString().substring(0, 8);
        ExportJob job = new ExportJob(jobId, tenantId, userId, name, resourceType, queryFilter, format,
            destination, destinationPath, ExportJob.Status.PENDING, null, null, null,
            0, null, null, null, null, Instant.now());
        exportJobRepository.save(job);
        return job;
    }

    public void executeExport(@NonNull ExportJob job) {
        LOG.info("Executing export job {} for tenant {} type {}", job.jobId(), job.tenantId(), job.resourceType());

        try {
            Path outputPath = switch (job.resourceType().toLowerCase()) {
                case "spans" -> exportSpans(job);
                case "metrics" -> exportMetrics(job);
                case "runs" -> exportRuns(job);
                default -> throw new IllegalArgumentException("Unknown resource type: " + job.resourceType());
            };

            long fileSize = Files.size(outputPath);
            long totalRecords = countRecords(job);

            // Upload to S3 if destination is S3
            String finalDestinationPath = outputPath.toString();
            if (job.destination() == ExportJob.Destination.S3) {
                ExportConfig config = exportConfigRepository.findByTenantAndType(job.tenantId(), ExportConfig.DestinationType.S3)
                    .orElseThrow(() -> new IllegalStateException("No S3 export config found for tenant " + job.tenantId()));
                String s3Key = job.tenantId() + "/" + job.jobId() + "." + job.format().name().toLowerCase();
                s3ExportClient.upload(config, outputPath, s3Key);
                finalDestinationPath = "s3://" + config.bucketName() + "/" + s3Key;
            }

            ExportJob completed = new ExportJob(job.jobId(), job.tenantId(), job.userId(), job.name(), job.resourceType(),
                job.queryFilter(), job.format(), job.destination(), finalDestinationPath,
                ExportJob.Status.COMPLETED, totalRecords, fileSize, null,
                0, null, job.parentJobId(), job.startedAt(), Instant.now(), job.createdAt());
            exportJobRepository.save(completed);
            LOG.info("Export job {} completed: {} records, {} bytes", job.jobId(), totalRecords, fileSize);

        } catch (Exception e) {
            LOG.error("Export job {} failed", job.jobId(), e);
            int newRetryCount = job.retryCount() + 1;
            ExportJob.Status newStatus = newRetryCount >= 3 ? ExportJob.Status.FAILED : ExportJob.Status.PENDING;
            Instant nextRetryAt = newStatus == ExportJob.Status.PENDING
                ? Instant.now().plusSeconds((long) Math.pow(4, newRetryCount) * 5)
                : null;

            ExportJob failed = new ExportJob(job.jobId(), job.tenantId(), job.userId(), job.name(), job.resourceType(),
                job.queryFilter(), job.format(), job.destination(), job.destinationPath(),
                newStatus, null, null, e.getMessage(),
                newRetryCount, nextRetryAt, job.parentJobId(), job.startedAt(),
                newStatus == ExportJob.Status.FAILED ? Instant.now() : null, job.createdAt());
            exportJobRepository.save(failed);
        }
    }

    private @NonNull Path exportSpans(@NonNull ExportJob job) throws IOException {
        boolean useCh = chJdbc != null;

        ExportQueryBuilder.QueryAndArgs q = queryBuilder.buildSpanQuery(job.tenantId(), job.queryFilter(), useCh);
        List<SpanExportRecord> records = pgJdbc.query(q.sql(), (rs, rowNum) -> new SpanExportRecord(
            rs.getString("span_id"), rs.getString("run_id"), rs.getString("parent_span_id"),
            rs.getString("span_name"), rs.getString("kind"),
            rs.getTimestamp("start_time").toInstant(),
            rs.getTimestamp("end_time") != null ? rs.getTimestamp("end_time").toInstant() : null,
            rs.getString("attributes"), rs.getString("events"),
            rs.getString("status"), rs.getString("span_type"),
            rs.getTimestamp("first_token_at") != null ? rs.getTimestamp("first_token_at").toInstant() : null,
            rs.getString("tenant_id")
        ), q.args().toArray());

        Path file = resolveOutputPath(job);
        return switch (job.format()) {
            case PARQUET -> parquetWriter.write(file, "SpanExport", records);
            case JSON -> writeJson(file, records);
            case CSV -> writeCsv(file, records);
        };
    }

    private @NonNull Path exportMetrics(@NonNull ExportJob job) throws IOException {
        ExportQueryBuilder.QueryAndArgs q = queryBuilder.buildMetricQuery(job.tenantId(), job.queryFilter());
        List<MetricExportRecord> records = pgJdbc.query(q.sql(), (rs, rowNum) -> new MetricExportRecord(
            rs.getString("snapshot_id"), rs.getString("tenant_id"),
            rs.getString("metric_name"), rs.getDouble("value"),
            rs.getString("tags"), rs.getTimestamp("timestamp").toInstant()
        ), q.args().toArray());

        Path file = resolveOutputPath(job);
        return switch (job.format()) {
            case PARQUET -> parquetWriter.write(file, "MetricExport", records);
            case JSON -> writeJson(file, records);
            case CSV -> writeCsv(file, records);
        };
    }

    private @NonNull Path exportRuns(@NonNull ExportJob job) throws IOException {
        ExportQueryBuilder.QueryAndArgs q = queryBuilder.buildRunIdsQuery(job.tenantId(), job.queryFilter());
        List<Map<String, Object>> rows = pgJdbc.queryForList(
            "SELECT * FROM runs WHERE tenant_id = ?" + q.sql().substring(q.sql().indexOf("AND")),
            q.args().toArray());

        Path file = resolveOutputPath(job);
        return switch (job.format()) {
            case JSON -> writeJson(file, rows);
            case CSV -> writeCsvFromMaps(file, rows);
            case PARQUET -> {
                String json = mapper.writeValueAsString(rows);
                Files.writeString(file, json);
                yield file;
            }
        };
    }

    private @NonNull Path resolveOutputPath(@NonNull ExportJob job) {
        Path dir = Path.of("exports", job.tenantId());
        String ext = job.format().name().toLowerCase();
        return dir.resolve(job.jobId() + "." + ext);
    }

    private <T> @NonNull Path writeJson(@NonNull Path file, @NonNull List<T> records) throws IOException {
        Files.createDirectories(file.getParent());
        String json = mapper.writeValueAsString(records);
        Files.writeString(file, json);
        return file;
    }

    private <T> @NonNull Path writeCsv(@NonNull Path file, @NonNull List<T> records) throws IOException {
        Files.createDirectories(file.getParent());
        try (BufferedWriter writer = Files.newBufferedWriter(file)) {
            if (!records.isEmpty()) {
                var fields = records.get(0).getClass().getRecordComponents();
                writer.write(java.util.Arrays.stream(fields).map(java.lang.reflect.RecordComponent::getName).collect(Collectors.joining(",")));
                writer.newLine();
                for (T record : records) {
                    String line = java.util.Arrays.stream(fields)
                        .map(f -> {
                            try { return escapeCsv(f.getAccessor().invoke(record)); }
                            catch (Exception e) { return ""; }
                        })
                        .collect(Collectors.joining(","));
                    writer.write(line);
                    writer.newLine();
                }
            }
        }
        return file;
    }

    private @NonNull Path writeCsvFromMaps(@NonNull Path file, @NonNull List<Map<String, Object>> rows) throws IOException {
        Files.createDirectories(file.getParent());
        try (BufferedWriter writer = Files.newBufferedWriter(file)) {
            if (!rows.isEmpty()) {
                List<String> headers = List.copyOf(rows.get(0).keySet());
                writer.write(String.join(",", headers));
                writer.newLine();
                for (Map<String, Object> row : rows) {
                    String line = headers.stream().map(h -> escapeCsv(row.get(h))).collect(Collectors.joining(","));
                    writer.write(line);
                    writer.newLine();
                }
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

    private long countRecords(@NonNull ExportJob job) {
        return switch (job.resourceType().toLowerCase()) {
            case "spans" -> {
                ExportQueryBuilder.QueryAndArgs q = queryBuilder.buildSpanQuery(job.tenantId(), job.queryFilter(), false);
                Long count = pgJdbc.queryForObject("SELECT COUNT(*) FROM (" + q.sql() + ") t", Long.class, q.args().toArray());
                yield count != null ? count : 0L;
            }
            case "metrics" -> {
                ExportQueryBuilder.QueryAndArgs q = queryBuilder.buildMetricQuery(job.tenantId(), job.queryFilter());
                Long count = pgJdbc.queryForObject("SELECT COUNT(*) FROM metric_snapshots WHERE tenant_id = ?" + q.sql().substring(q.sql().indexOf("AND")), Long.class, q.args().toArray());
                yield count != null ? count : 0L;
            }
            default -> 0L;
        };
    }
}
