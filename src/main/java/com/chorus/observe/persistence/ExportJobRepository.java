package com.chorus.observe.persistence;

import com.chorus.observe.model.ExportJob;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class ExportJobRepository {

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final RowMapper<ExportJob> rowMapper;
    private final boolean isPostgreSql;

    public ExportJobRepository(@NonNull DataSource dataSource, @NonNull ObjectMapper mapper) {
        this.jdbc = dataSource != null ? new JdbcTemplate(dataSource) : null;
        this.mapper = Objects.requireNonNull(mapper);
        this.rowMapper = new ExportJobRowMapper(mapper);
        this.isPostgreSql = detectPostgreSql(dataSource);
    }

    private boolean detectPostgreSql(DataSource dataSource) {
        if (dataSource == null) return false;
        try (Connection conn = dataSource.getConnection()) {
            String product = conn.getMetaData().getDatabaseProductName();
            return product != null && product.toLowerCase().contains("postgresql");
        } catch (SQLException e) {
            return false;
        }
    }

    public void save(@NonNull ExportJob job) {
        String queryFilterCast = isPostgreSql ? "?::jsonb" : "?";
        String sql;
        if (isPostgreSql) {
            sql = "INSERT INTO export_jobs (job_id, tenant_id, user_id, name, resource_type, query_filter, format, destination, destination_path, status, total_records, file_size_bytes, error_message, retry_count, next_retry_at, parent_job_id, started_at, finished_at, created_at) " +
                "VALUES (?, ?, ?, ?, ?, " + queryFilterCast + ", ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                "ON CONFLICT (job_id) DO UPDATE SET " +
                "tenant_id = EXCLUDED.tenant_id, user_id = EXCLUDED.user_id, name = EXCLUDED.name, " +
                "resource_type = EXCLUDED.resource_type, query_filter = EXCLUDED.query_filter, " +
                "format = EXCLUDED.format, destination = EXCLUDED.destination, " +
                "destination_path = EXCLUDED.destination_path, status = EXCLUDED.status, " +
                "total_records = EXCLUDED.total_records, file_size_bytes = EXCLUDED.file_size_bytes, " +
                "error_message = EXCLUDED.error_message, retry_count = EXCLUDED.retry_count, " +
                "next_retry_at = EXCLUDED.next_retry_at, parent_job_id = EXCLUDED.parent_job_id, " +
                "started_at = EXCLUDED.started_at, finished_at = EXCLUDED.finished_at";
        } else {
            sql = "MERGE INTO export_jobs (job_id, tenant_id, user_id, name, resource_type, query_filter, format, destination, destination_path, status, total_records, file_size_bytes, error_message, retry_count, next_retry_at, parent_job_id, started_at, finished_at, created_at) " +
                "KEY (job_id) VALUES (?, ?, ?, ?, ?, " + queryFilterCast + ", ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        }
        try {
            jdbc.update(sql,
                job.jobId(), job.tenantId(), job.userId(), job.name(), job.resourceType(),
                mapper.writeValueAsString(job.queryFilter()),
                job.format().name(), job.destination().name(), job.destinationPath(),
                job.status().name(), job.totalRecords(), job.fileSizeBytes(), job.errorMessage(),
                job.retryCount(),
                job.nextRetryAt() != null ? Timestamp.from(job.nextRetryAt()) : null,
                job.parentJobId(),
                job.startedAt() != null ? Timestamp.from(job.startedAt()) : null,
                job.finishedAt() != null ? Timestamp.from(job.finishedAt()) : null,
                Timestamp.from(job.createdAt()));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize query_filter", e);
        }
    }

    public @NonNull Optional<ExportJob> findById(@NonNull String jobId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                "SELECT * FROM export_jobs WHERE job_id = ?", rowMapper, jobId));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public @NonNull List<ExportJob> findByTenant(@NonNull String tenantId, int limit, int offset) {
        return jdbc.query("SELECT * FROM export_jobs WHERE tenant_id = ? ORDER BY created_at DESC LIMIT ? OFFSET ?", rowMapper, tenantId, limit, offset);
    }

    public @NonNull List<ExportJob> findPending() {
        return jdbc.query("SELECT * FROM export_jobs WHERE status = 'PENDING' ORDER BY created_at ASC", rowMapper);
    }

    public @NonNull Optional<ExportJob> claimPendingJob() {
        String sql;
        if (isPostgreSql) {
            sql = "SELECT * FROM export_jobs WHERE status = 'PENDING' OR (status = 'FAILED' AND retry_count < 3 AND next_retry_at <= NOW()) ORDER BY created_at ASC FOR UPDATE SKIP LOCKED LIMIT 1";
        } else {
            sql = "SELECT * FROM export_jobs WHERE status = 'PENDING' OR (status = 'FAILED' AND retry_count < 3 AND next_retry_at <= NOW()) ORDER BY created_at ASC LIMIT 1 FOR UPDATE";
        }
        try {
            return Optional.ofNullable(jdbc.queryForObject(sql, rowMapper));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public @NonNull List<ExportJob> findOrphanedJobs(@NonNull Instant olderThan) {
        return jdbc.query(
            "SELECT * FROM export_jobs WHERE status IN ('PENDING', 'RUNNING') AND (started_at < ? OR (started_at IS NULL AND created_at < ?)) ORDER BY created_at ASC",
            rowMapper, Timestamp.from(olderThan), Timestamp.from(olderThan));
    }

    public @NonNull List<ExportJob> findByParentJobId(@NonNull String parentJobId) {
        return jdbc.query("SELECT * FROM export_jobs WHERE parent_job_id = ? ORDER BY created_at ASC", rowMapper, parentJobId);
    }

    public void updateRunning(@NonNull String jobId, @NonNull Instant startedAt) {
        jdbc.update("UPDATE export_jobs SET status = 'RUNNING', started_at = ? WHERE job_id = ?",
            Timestamp.from(startedAt), jobId);
    }

    public void updateStatus(@NonNull String jobId, ExportJob.@NonNull Status status, @Nullable String errorMessage,
                             @Nullable Integer retryCount, @Nullable Instant nextRetryAt) {
        jdbc.update(
            "UPDATE export_jobs SET status = ?, error_message = ?, retry_count = COALESCE(?, retry_count), next_retry_at = ?, finished_at = ? WHERE job_id = ?",
            status.name(), errorMessage, retryCount,
            nextRetryAt != null ? Timestamp.from(nextRetryAt) : null,
            status == ExportJob.Status.COMPLETED || status == ExportJob.Status.FAILED ? Timestamp.from(Instant.now()) : null,
            jobId);
    }

    public long countByTenant(@NonNull String tenantId) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM export_jobs WHERE tenant_id = ?", Long.class, tenantId);
        return count != null ? count : 0L;
    }

    private static final class ExportJobRowMapper implements RowMapper<ExportJob> {
        private final ObjectMapper mapper;

        ExportJobRowMapper(ObjectMapper mapper) {
            this.mapper = mapper;
        }

        @Override
        public ExportJob mapRow(ResultSet rs, int rowNum) throws SQLException {
            try {
                return new ExportJob(
                    rs.getString("job_id"),
                    rs.getString("tenant_id"),
                    rs.getString("user_id"),
                    rs.getString("name"),
                    rs.getString("resource_type"),
                    mapper.readValue(rs.getString("query_filter"), new TypeReference<Map<String, Object>>() {}),
                    ExportJob.Format.valueOf(rs.getString("format")),
                    ExportJob.Destination.valueOf(rs.getString("destination")),
                    rs.getString("destination_path"),
                    ExportJob.Status.valueOf(rs.getString("status")),
                    rs.getLong("total_records"),
                    rs.getLong("file_size_bytes"),
                    rs.getString("error_message"),
                    rs.getInt("retry_count"),
                    rs.getTimestamp("next_retry_at") != null ? rs.getTimestamp("next_retry_at").toInstant() : null,
                    rs.getString("parent_job_id"),
                    rs.getTimestamp("started_at") != null ? rs.getTimestamp("started_at").toInstant() : null,
                    rs.getTimestamp("finished_at") != null ? rs.getTimestamp("finished_at").toInstant() : null,
                    rs.getTimestamp("created_at").toInstant()
                );
            } catch (JsonProcessingException e) {
                throw new SQLException("Failed to deserialize JSON", e);
            }
        }
    }
}
