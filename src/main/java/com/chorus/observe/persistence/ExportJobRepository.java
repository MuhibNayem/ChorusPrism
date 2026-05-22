package com.chorus.observe.persistence;

import com.chorus.observe.model.ExportJob;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jspecify.annotations.NonNull;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import javax.sql.DataSource;
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

    public ExportJobRepository(@NonNull DataSource dataSource, @NonNull ObjectMapper mapper) {
        this.jdbc = dataSource != null ? new JdbcTemplate(dataSource) : null;
        this.mapper = Objects.requireNonNull(mapper);
        this.rowMapper = new ExportJobRowMapper(mapper);
    }

    public void save(@NonNull ExportJob job) {
        String sql = """
            INSERT INTO export_jobs (job_id, tenant_id, user_id, name, resource_type, query_filter, format, destination, destination_path, status, total_records, file_size_bytes, error_message, started_at, finished_at, created_at)
            VALUES (?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (job_id) DO UPDATE SET
                tenant_id = EXCLUDED.tenant_id,
                user_id = EXCLUDED.user_id,
                name = EXCLUDED.name,
                resource_type = EXCLUDED.resource_type,
                query_filter = EXCLUDED.query_filter,
                format = EXCLUDED.format,
                destination = EXCLUDED.destination,
                destination_path = EXCLUDED.destination_path,
                status = EXCLUDED.status,
                total_records = EXCLUDED.total_records,
                file_size_bytes = EXCLUDED.file_size_bytes,
                error_message = EXCLUDED.error_message,
                started_at = EXCLUDED.started_at,
                finished_at = EXCLUDED.finished_at
            """;
        try {
            jdbc.update(sql,
                job.jobId(), job.tenantId(), job.userId(), job.name(), job.resourceType(),
                mapper.writeValueAsString(job.queryFilter()),
                job.format().name(), job.destination().name(), job.destinationPath(),
                job.status().name(), job.totalRecords(), job.fileSizeBytes(), job.errorMessage(),
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

    public long countByTenant(@NonNull String tenantId) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM export_jobs WHERE tenant_id = ?", Long.class, tenantId);
        return count != null ? count : 0L;
    }

    private static final class ExportJobRowMapper implements RowMapper<ExportJob> {
        private final ObjectMapper mapper;

        ExportJobRowMapper(ObjectMapper mapper) { this.mapper = mapper; }

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
