package com.chorus.observe.persistence;

import org.jspecify.annotations.NonNull;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.Objects;
import java.util.Optional;

public class TenantSettingsRepository {

    private final JdbcTemplate jdbc;

    public TenantSettingsRepository(@NonNull DataSource dataSource) {
        this.jdbc = new JdbcTemplate(Objects.requireNonNull(dataSource));
    }

    public @NonNull Optional<String> find(@NonNull String tenantId, @NonNull String key) {
        var rows = jdbc.queryForList(
            "SELECT value FROM tenant_settings WHERE tenant_id = ? AND key = ?",
            String.class, tenantId, key);
        return rows.isEmpty() ? Optional.empty() : Optional.ofNullable(rows.get(0));
    }

    public void upsert(@NonNull String tenantId, @NonNull String key, @NonNull String value) {
        jdbc.update("""
            INSERT INTO tenant_settings (tenant_id, key, value, updated_at)
            VALUES (?, ?, ?, NOW())
            ON CONFLICT (tenant_id, key) DO UPDATE SET value = EXCLUDED.value, updated_at = NOW()
            """, tenantId, key, value);
    }
}
