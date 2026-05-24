package com.chorus.observe.persistence;

import com.chorus.observe.model.Run;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * JDBC repository for runs with filtering, sorting, and pagination.
 */
public class RunRepository {

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final RowMapper<Run> rowMapper;

    public RunRepository(@NonNull DataSource dataSource, @NonNull ObjectMapper mapper) {
        this.jdbc = dataSource != null ? new JdbcTemplate(dataSource) : null;
        this.mapper = Objects.requireNonNull(mapper);
        this.rowMapper = new RunRowMapper(mapper);
    }

    public void save(@NonNull Run run) {
        String sql = """
            INSERT INTO runs (run_id, tenant_id, framework, agent_id, model, start_time, end_time, status, tags, metadata, total_tokens, total_cost, latency_ms)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?, ?)
            ON CONFLICT (run_id) DO UPDATE SET
                tenant_id = EXCLUDED.tenant_id,
                framework = EXCLUDED.framework,
                agent_id = EXCLUDED.agent_id,
                model = EXCLUDED.model,
                start_time = EXCLUDED.start_time,
                end_time = EXCLUDED.end_time,
                status = EXCLUDED.status,
                tags = EXCLUDED.tags,
                metadata = EXCLUDED.metadata,
                total_tokens = EXCLUDED.total_tokens,
                total_cost = EXCLUDED.total_cost,
                latency_ms = EXCLUDED.latency_ms
            """;
        jdbc.update(sql,
            run.runId(), run.tenantId(), run.framework(), run.agentId(), run.model(),
            Timestamp.from(run.startTime()),
            run.endTime() != null ? Timestamp.from(run.endTime()) : null,
            run.status().name(),
            toJson(run.tags()),
            toJson(run.metadata()),
            run.totalTokens(),
            run.totalCost(),
            run.latencyMs()
        );
    }

    public @NonNull Optional<Run> findById(@NonNull String runId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                "SELECT * FROM runs WHERE run_id = ?", rowMapper, runId));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public @NonNull Optional<Run> findByIdAndTenantId(@NonNull String runId, @NonNull String tenantId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                "SELECT * FROM runs WHERE run_id = ? AND tenant_id = ?", rowMapper, runId, tenantId));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public @NonNull List<Run> findAll(@NonNull RunQuery query) {
        StringBuilder sql = new StringBuilder("SELECT * FROM runs WHERE 1=1");
        List<Object> args = new ArrayList<>();

        if (query.tenantId() != null) {
            sql.append(" AND tenant_id = ?");
            args.add(query.tenantId());
        }
        if (query.framework() != null) {
            sql.append(" AND framework = ?");
            args.add(query.framework());
        }
        if (query.agentId() != null) {
            sql.append(" AND agent_id = ?");
            args.add(query.agentId());
        }
        if (query.status() != null) {
            sql.append(" AND status = ?");
            args.add(query.status().name());
        }
        if (query.from() != null) {
            sql.append(" AND start_time >= ?");
            args.add(Timestamp.from(query.from()));
        }
        if (query.to() != null) {
            sql.append(" AND start_time <= ?");
            args.add(Timestamp.from(query.to()));
        }
        if (query.tagKey() != null && query.tagValue() != null) {
            sql.append(" AND tags ->> ? = ?");
            args.add(query.tagKey());
            args.add(query.tagValue());
        }
        if (query.model() != null) {
            sql.append(" AND model = ?");
            args.add(query.model());
        }
        if (query.search() != null && !query.search().isBlank()) {
            sql.append(" AND (run_id ILIKE ? OR agent_id ILIKE ? OR framework ILIKE ?)");
            String pattern = "%" + query.search() + "%";
            args.add(pattern);
            args.add(pattern);
            args.add(pattern);
        }

        String sortCol = switch (query.sortBy()) {
            case "run_id" -> "run_id";
            case "agent_id" -> "agent_id";
            case "framework" -> "framework";
            case "model" -> "model";
            case "status" -> "status";
            case "total_tokens" -> "total_tokens";
            case "total_cost" -> "total_cost";
            case "latency_ms" -> "latency_ms";
            default -> "start_time";
        };
        sql.append(" ORDER BY ").append(sortCol).append(" ").append(query.sortOrder());
        sql.append(" LIMIT ? OFFSET ?");
        args.add(query.limit());
        args.add(query.offset());

        return jdbc.query(sql.toString(), rowMapper, args.toArray());
    }

    public long count(@NonNull RunQuery query) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM runs WHERE 1=1");
        List<Object> args = new ArrayList<>();

        if (query.tenantId() != null) {
            sql.append(" AND tenant_id = ?");
            args.add(query.tenantId());
        }
        if (query.framework() != null) {
            sql.append(" AND framework = ?");
            args.add(query.framework());
        }
        if (query.agentId() != null) {
            sql.append(" AND agent_id = ?");
            args.add(query.agentId());
        }
        if (query.model() != null) {
            sql.append(" AND model = ?");
            args.add(query.model());
        }
        if (query.status() != null) {
            sql.append(" AND status = ?");
            args.add(query.status().name());
        }
        if (query.from() != null) {
            sql.append(" AND start_time >= ?");
            args.add(Timestamp.from(query.from()));
        }
        if (query.to() != null) {
            sql.append(" AND start_time <= ?");
            args.add(Timestamp.from(query.to()));
        }
        if (query.search() != null && !query.search().isBlank()) {
            sql.append(" AND (run_id ILIKE ? OR agent_id ILIKE ? OR framework ILIKE ?)");
            String pattern = "%" + query.search() + "%";
            args.add(pattern);
            args.add(pattern);
            args.add(pattern);
        }

        Long count = jdbc.queryForObject(sql.toString(), Long.class, args.toArray());
        return count != null ? count : 0L;
    }

    public boolean exists(@NonNull String runId) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM runs WHERE run_id = ?", Long.class, runId);
        return count != null && count > 0;
    }

    public boolean existsForTenant(@NonNull String runId, @NonNull String tenantId) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM runs WHERE run_id = ? AND tenant_id = ?", Long.class, runId, tenantId);
        return count != null && count > 0;
    }

    public @NonNull List<Run> findByTenantId(@NonNull String tenantId, int limit, int offset) {
        return jdbc.query(
            "SELECT * FROM runs WHERE tenant_id = ? ORDER BY start_time DESC LIMIT ? OFFSET ?",
            rowMapper, tenantId, limit, offset);
    }

    public long countByTenantId(@NonNull String tenantId) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM runs WHERE tenant_id = ?", Long.class, tenantId);
        return count != null ? count : 0L;
    }

    private @NonNull String toJson(@NonNull Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize to JSON", e);
        }
    }

    public record RunQuery(
        @Nullable String tenantId,
        @Nullable String framework,
        @Nullable String agentId,
        @Nullable String model,
        Run.@Nullable Status status,
        @Nullable Instant from,
        @Nullable Instant to,
        @Nullable String tagKey,
        @Nullable String tagValue,
        @Nullable String search,
        @NonNull String sortBy,
        @NonNull String sortOrder,
        int limit,
        int offset
    ) {
        public RunQuery {
            sortBy = sortBy != null ? sortBy : "start_time";
            sortOrder = sortOrder != null && sortOrder.equalsIgnoreCase("ASC") ? "ASC" : "DESC";
            if (limit < 1) limit = 20;
            if (limit > 1000) limit = 1000;
            if (offset < 0) offset = 0;
        }
    }

    private static final class RunRowMapper implements RowMapper<Run> {
        private final ObjectMapper mapper;

        RunRowMapper(ObjectMapper mapper) {
            this.mapper = mapper;
        }

        @Override
        public Run mapRow(ResultSet rs, int rowNum) throws SQLException {
            try {
                return new Run(
                    rs.getString("run_id"),
                    rs.getString("tenant_id"),
                    rs.getString("framework"),
                    rs.getString("agent_id"),
                    rs.getString("model"),
                    rs.getTimestamp("start_time").toInstant(),
                    rs.getTimestamp("end_time") != null ? rs.getTimestamp("end_time").toInstant() : null,
                    Run.Status.valueOf(rs.getString("status")),
                    mapper.readValue(rs.getString("tags"), new TypeReference<Map<String, String>>() {}),
                    mapper.readValue(rs.getString("metadata"), new TypeReference<Map<String, Object>>() {}),
                    rs.getInt("total_tokens"),
                    rs.getBigDecimal("total_cost"),
                    rs.getLong("latency_ms")
                );
            } catch (JsonProcessingException e) {
                throw new SQLException("Failed to deserialize JSON", e);
            }
        }
    }
}
