package com.chorus.observe.export;

import com.chorus.observe.model.ExportJob;
import com.chorus.observe.persistence.ExportConfigRepository;
import com.chorus.observe.persistence.ExportJobRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ExportIntegrationTest {

    private DataSource dataSource;
    private ObjectMapper mapper;
    private ExportJobRepository exportJobRepository;
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("org.h2.Driver");
        ds.setUrl("jdbc:h2:mem:export_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL");
        ds.setUsername("sa");
        ds.setPassword("");
        this.dataSource = ds;
        this.mapper = new ObjectMapper();
        this.mapper.registerModule(new JavaTimeModule());
        this.exportJobRepository = new ExportJobRepository(ds, mapper);
        this.jdbc = new JdbcTemplate(ds);

        // Create minimal schema
        jdbc.execute("DROP TABLE IF EXISTS tenants");
        jdbc.execute("""
            CREATE TABLE tenants (
                tenant_id VARCHAR(64) PRIMARY KEY,
                name VARCHAR(256) NOT NULL,
                config VARCHAR(512) DEFAULT '{}',
                status VARCHAR(16) DEFAULT 'ACTIVE',
                created_at TIMESTAMP DEFAULT NOW()
            )
            """);
        jdbc.execute("DROP TABLE IF EXISTS runs");
        jdbc.execute("""
            CREATE TABLE runs (
                run_id VARCHAR(64) PRIMARY KEY,
                tenant_id VARCHAR(64) NOT NULL,
                framework VARCHAR(64) NOT NULL,
                agent_id VARCHAR(256) NOT NULL,
                model VARCHAR(128),
                start_time TIMESTAMP NOT NULL,
                end_time TIMESTAMP,
                status VARCHAR(16) DEFAULT 'RUNNING',
                tags VARCHAR(512) DEFAULT '{}',
                metadata VARCHAR(512) DEFAULT '{}',
                total_tokens INT DEFAULT 0,
                total_cost DECIMAL(18, 8) DEFAULT 0,
                latency_ms BIGINT DEFAULT 0,
                created_at TIMESTAMP DEFAULT NOW()
            )
            """);
        jdbc.execute("DROP TABLE IF EXISTS spans");
        jdbc.execute("""
            CREATE TABLE spans (
                span_id VARCHAR(64) PRIMARY KEY,
                run_id VARCHAR(64) NOT NULL,
                parent_span_id VARCHAR(64),
                span_name VARCHAR(512) NOT NULL,
                kind VARCHAR(16) DEFAULT 'INTERNAL',
                start_time TIMESTAMP NOT NULL,
                end_time TIMESTAMP,
                attributes VARCHAR(512) DEFAULT '{}',
                events VARCHAR(512) DEFAULT '[]',
                status VARCHAR(16) DEFAULT 'UNSET',
                span_type VARCHAR(64),
                first_token_at TIMESTAMP,
                created_at TIMESTAMP DEFAULT NOW()
            )
            """);
        jdbc.execute("DROP TABLE IF EXISTS export_jobs");
        jdbc.execute("""
            CREATE TABLE export_jobs (
                job_id VARCHAR(64) PRIMARY KEY,
                tenant_id VARCHAR(64) NOT NULL,
                user_id VARCHAR(64) NOT NULL,
                name VARCHAR(256) NOT NULL,
                resource_type VARCHAR(64) NOT NULL,
                query_filter VARCHAR(512) DEFAULT '{}',
                format VARCHAR(16) NOT NULL,
                destination VARCHAR(16) NOT NULL,
                destination_path VARCHAR(512),
                status VARCHAR(16) DEFAULT 'PENDING',
                total_records BIGINT,
                file_size_bytes BIGINT,
                error_message TEXT,
                retry_count INT DEFAULT 0,
                next_retry_at TIMESTAMP,
                parent_job_id VARCHAR(64),
                started_at TIMESTAMP,
                finished_at TIMESTAMP,
                created_at TIMESTAMP DEFAULT NOW()
            )
            """);
    }

    @Test
    void exportSpansForTenantAContainsNoTenantBData() throws Exception {
        // Insert tenants
        jdbc.update("INSERT INTO tenants (tenant_id, name) VALUES (?, ?)", "tenant-a", "Tenant A");
        jdbc.update("INSERT INTO tenants (tenant_id, name) VALUES (?, ?)", "tenant-b", "Tenant B");

        // Insert runs with tenant_id
        jdbc.update("INSERT INTO runs (run_id, tenant_id, framework, agent_id, start_time, status) VALUES (?, ?, 'test', 'agent1', ?, 'SUCCESS')",
            "run-a", "tenant-a", Timestamp.from(Instant.now()));
        jdbc.update("INSERT INTO runs (run_id, tenant_id, framework, agent_id, start_time, status) VALUES (?, ?, 'test', 'agent1', ?, 'SUCCESS')",
            "run-b", "tenant-b", Timestamp.from(Instant.now()));

        // Insert spans
        jdbc.update("INSERT INTO spans (span_id, run_id, span_name, kind, start_time, attributes, events, status, span_type) VALUES (?, ?, 'span-a', 'INTERNAL', ?, '{}', '[]', 'OK', null)",
            "span-a", "run-a", Timestamp.from(Instant.now()));
        jdbc.update("INSERT INTO spans (span_id, run_id, span_name, kind, start_time, attributes, events, status, span_type) VALUES (?, ?, 'span-b', 'INTERNAL', ?, '{}', '[]', 'OK', null)",
            "span-b", "run-b", Timestamp.from(Instant.now()));

        // Create and execute export for tenant-a
        SchemaRegistry registry = new SchemaRegistry();
        registry.register("SpanExport", 1, SpanExportRecord.class, SchemaRegistry.Compatibility.BACKWARD);
        ParquetExportWriter parquetWriter = new ParquetExportWriter(registry);
        ExportQueryBuilder queryBuilder = new ExportQueryBuilder();
        ExportConfigRepository configRepo = new ExportConfigRepository(dataSource);
        CredentialEncryptionService encryptionService = new CredentialEncryptionService(null);
        S3ExportClient s3Client = new S3ExportClient(encryptionService);

        ExportService exportService = new ExportService(
            exportJobRepository, dataSource, null,
            parquetWriter, queryBuilder, configRepo, s3Client, mapper
        );

        ExportJob job = exportService.submitExport("tenant-a", "user-1", "test-export",
            "spans", Map.of(), ExportJob.Format.JSON, ExportJob.Destination.FILE, null);
        exportService.executeExport(job);

        Optional<ExportJob> completed = exportJobRepository.findById(job.jobId());
        assertThat(completed).isPresent();
        assertThat(completed.get().status()).isEqualTo(ExportJob.Status.COMPLETED);
        assertThat(completed.get().totalRecords()).isEqualTo(1L);

        // Verify file content contains span-a but NOT span-b
        String json = Files.readString(Path.of(completed.get().destinationPath()));
        assertThat(json).contains("span-a");
        assertThat(json).doesNotContain("span-b");
    }
}
