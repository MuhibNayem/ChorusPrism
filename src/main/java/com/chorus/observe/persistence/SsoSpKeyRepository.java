package com.chorus.observe.persistence;

import com.chorus.observe.model.SsoSpKey;
import org.jspecify.annotations.NonNull;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;

public class SsoSpKeyRepository {

    private final JdbcTemplate jdbc;

    public SsoSpKeyRepository(@NonNull DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    public void save(@NonNull SsoSpKey key) {
        String sql = """
            INSERT INTO sso_sp_keys (id, tenant_id, private_key_encrypted, cert_pem, algorithm, key_size_bits, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (tenant_id) DO UPDATE SET
                private_key_encrypted = EXCLUDED.private_key_encrypted,
                cert_pem              = EXCLUDED.cert_pem,
                algorithm             = EXCLUDED.algorithm,
                key_size_bits         = EXCLUDED.key_size_bits
            """;
        jdbc.update(sql,
            key.id() != null ? key.id() : UUID.randomUUID(),
            key.tenantId(), key.privateKeyEncrypted(), key.certPem(),
            key.algorithm(), key.keySizeBits(),
            Timestamp.from(key.createdAt()));
    }

    public @NonNull Optional<SsoSpKey> findByTenantId(@NonNull String tenantId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                "SELECT * FROM sso_sp_keys WHERE tenant_id = ?", ROW_MAPPER, tenantId));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    private static final RowMapper<SsoSpKey> ROW_MAPPER = (ResultSet rs, int rowNum) -> new SsoSpKey(
        rs.getObject("id", UUID.class),
        rs.getString("tenant_id"),
        rs.getString("private_key_encrypted"),
        rs.getString("cert_pem"),
        rs.getString("algorithm"),
        rs.getInt("key_size_bits"),
        rs.getTimestamp("created_at").toInstant()
    );
}
