package com.chorus.observe.export;

import com.chorus.observe.model.ExportJob;
import com.chorus.observe.persistence.ExportConfigRepository;
import com.chorus.observe.persistence.ExportJobRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ExportJobSchedulerTest {

    private DataSource dataSource;
    private ObjectMapper mapper;
    private ExportJobRepository exportJobRepository;
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("org.h2.Driver");
        ds.setUrl("jdbc:h2:mem:scheduler_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL");
        ds.setUsername("sa");
        ds.setPassword("");
        this.dataSource = ds;
        this.mapper = new ObjectMapper();
        this.exportJobRepository = new ExportJobRepository(ds, mapper);
        this.jdbc = new JdbcTemplate(ds);

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
    void orphanRecoveryResetsStaleRunningJobs() {
        // Insert an old RUNNING job
        String jobId = "exp-orphan-1";
        jdbc.update("""
            INSERT INTO export_jobs (job_id, tenant_id, user_id, name, resource_type, query_filter, format, destination, status, started_at, created_at)
            VALUES (?, 'tenant-1', 'user-1', 'test', 'spans', '{}', 'JSON', 'FILE', 'RUNNING', ?, ?)
            """, jobId, Timestamp.from(Instant.now().minus(10, ChronoUnit.MINUTES)), Timestamp.from(Instant.now()));

        FakeExportService fakeService = new FakeExportService(dataSource, exportJobRepository);
        ExportJobScheduler scheduler = new ExportJobScheduler(exportJobRepository, fakeService);
        scheduler.recoverOrphans();

        Optional<ExportJob> recovered = exportJobRepository.findById(jobId);
        assertThat(recovered).isPresent();
        assertThat(recovered.get().status()).isEqualTo(ExportJob.Status.PENDING);
        assertThat(recovered.get().startedAt()).isNull();
    }

    @Test
    void schedulerClaimsAndExecutesPendingJob() {
        String jobId = "exp-pending-1";
        jdbc.update("""
            INSERT INTO export_jobs (job_id, tenant_id, user_id, name, resource_type, query_filter, format, destination, status, created_at)
            VALUES (?, 'tenant-1', 'user-1', 'test', 'spans', '{}', 'JSON', 'FILE', 'PENDING', ?)
            """, jobId, Timestamp.from(Instant.now()));

        FakeExportService fakeService = new FakeExportService(dataSource, exportJobRepository);
        ExportJobScheduler scheduler = new ExportJobScheduler(exportJobRepository, fakeService);
        scheduler.pollAndExecute();

        Optional<ExportJob> job = exportJobRepository.findById(jobId);
        assertThat(job).isPresent();
        assertThat(job.get().status()).isEqualTo(ExportJob.Status.COMPLETED);
        assertThat(fakeService.lastJob).isNotNull();
        assertThat(fakeService.lastJob.jobId()).isEqualTo(jobId);
    }

    static class FakeExportService extends ExportService {
        ExportJob lastJob;

        FakeExportService(DataSource dataSource, ExportJobRepository repo) {
            super(repo, null, null,
                new ParquetExportWriter(new SchemaRegistry()),
                new ExportQueryBuilder(),
                new ExportConfigRepository(dataSource),
                new S3ExportClient(new CredentialEncryptionService(null)),
                new ObjectMapper());
        }

        @Override
        public void executeExport(ExportJob job) {
            lastJob = job;
            // Mark as completed in the repository for test verification
            ExportJob completed = new ExportJob(job.jobId(), job.tenantId(), job.userId(), job.name(), job.resourceType(),
                job.queryFilter(), job.format(), job.destination(), job.destinationPath(),
                ExportJob.Status.COMPLETED, 0L, 0L, null,
                0, null, job.parentJobId(), job.startedAt(), Instant.now(), job.createdAt());
            try {
                var field = ExportService.class.getDeclaredField("exportJobRepository");
                field.setAccessible(true);
                ExportJobRepository repo = (ExportJobRepository) field.get(this);
                repo.save(completed);
            } catch (Exception e) {
                // ignore
            }
        }
    }
}
