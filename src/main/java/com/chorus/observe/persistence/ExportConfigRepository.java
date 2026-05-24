package com.chorus.observe.persistence;

import com.chorus.observe.model.ExportConfig;
import org.jspecify.annotations.NonNull;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Objects;
import java.util.Optional;

public class ExportConfigRepository {

    private final JdbcTemplate jdbc;
    private final RowMapper<ExportConfig> rowMapper = new ExportConfigRowMapper();

    public ExportConfigRepository(@NonNull DataSource dataSource) {
        this.jdbc = dataSource != null ? new JdbcTemplate(dataSource) : null;
    }

    public void save(@NonNull ExportConfig config) {
        String sql = """
            INSERT INTO export_configs (config_id, tenant_id, destination_type, endpoint_url, region, bucket_name, access_key_id, secret_access_key, path_prefix, enabled, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (tenant_id, destination_type) DO UPDATE SET
                endpoint_url = EXCLUDED.endpoint_url,
                region = EXCLUDED.region,
                bucket_name = EXCLUDED.bucket_name,
                access_key_id = EXCLUDED.access_key_id,
                secret_access_key = EXCLUDED.secret_access_key,
                path_prefix = EXCLUDED.path_prefix,
                enabled = EXCLUDED.enabled,
                updated_at = EXCLUDED.updated_at
            """;
        jdbc.update(sql,
            config.configId(), config.tenantId(), config.destinationType().name(),
            config.endpointUrl(), config.region(), config.bucketName(),
            config.accessKeyId(), config.secretAccessKey(), config.pathPrefix(),
            config.enabled(), Timestamp.from(config.createdAt()), Timestamp.from(config.updatedAt()));
    }

    public @NonNull Optional<ExportConfig> findByTenantAndType(@NonNull String tenantId, ExportConfig.@NonNull DestinationType type) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                "SELECT * FROM export_configs WHERE tenant_id = ? AND destination_type = ?",
                rowMapper, tenantId, type.name()));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    private static final class ExportConfigRowMapper implements RowMapper<ExportConfig> {
        @Override
        public ExportConfig mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new ExportConfig(
                rs.getString("config_id"),
                rs.getString("tenant_id"),
                ExportConfig.DestinationType.valueOf(rs.getString("destination_type")),
                rs.getString("endpoint_url"),
                rs.getString("region"),
                rs.getString("bucket_name"),
                rs.getString("access_key_id"),
                rs.getString("secret_access_key"),
                rs.getString("path_prefix") != null ? rs.getString("path_prefix") : "",
                rs.getBoolean("enabled"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant()
            );
        }
    }
}
