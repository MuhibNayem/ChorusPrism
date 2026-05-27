package com.chorus.observe.persistence;

import org.jspecify.annotations.NonNull;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class SmtpConfigRepository {

    public record SmtpRow(
        String host, int port, String username, String password,
        String fromAddress, boolean useTls, boolean enabled
    ) {}

    private final JdbcTemplate jdbc;

    public SmtpConfigRepository(@NonNull DataSource dataSource) {
        this.jdbc = new JdbcTemplate(Objects.requireNonNull(dataSource));
    }

    public Optional<SmtpRow> findByTenantId(@NonNull String tenantId) {
        List<SmtpRow> rows = jdbc.query(
            "SELECT host, port, username, password, from_address, use_tls, enabled FROM smtp_configs WHERE tenant_id = ?",
            (rs, i) -> new SmtpRow(
                rs.getString("host"),
                rs.getInt("port"),
                rs.getString("username"),
                rs.getString("password"),
                rs.getString("from_address"),
                rs.getBoolean("use_tls"),
                rs.getBoolean("enabled")
            ),
            tenantId);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    public void save(@NonNull String tenantId, @NonNull SmtpRow row) {
        jdbc.update("""
            INSERT INTO smtp_configs (tenant_id, host, port, username, password, from_address, use_tls, enabled, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW())
            ON CONFLICT (tenant_id) DO UPDATE SET
                host         = EXCLUDED.host,
                port         = EXCLUDED.port,
                username     = EXCLUDED.username,
                password     = EXCLUDED.password,
                from_address = EXCLUDED.from_address,
                use_tls      = EXCLUDED.use_tls,
                enabled      = EXCLUDED.enabled,
                updated_at   = NOW()
            """,
            tenantId, row.host(), row.port(), row.username(),
            row.password(), row.fromAddress(), row.useTls(), row.enabled());
    }
}
