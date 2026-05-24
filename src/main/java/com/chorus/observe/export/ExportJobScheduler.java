package com.chorus.observe.export;

import com.chorus.observe.model.ExportJob;
import com.chorus.observe.persistence.ExportJobRepository;
import jakarta.annotation.PostConstruct;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Persistent polling scheduler for export jobs.
 * Claims jobs via row-level locking and executes them.
 */
public class ExportJobScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(ExportJobScheduler.class);
    private static final long ORPHAN_AGE_MINUTES = 5;

    private final ExportJobRepository exportJobRepository;
    private final ExportService exportService;

    public ExportJobScheduler(@NonNull ExportJobRepository exportJobRepository, @NonNull ExportService exportService) {
        this.exportJobRepository = Objects.requireNonNull(exportJobRepository);
        this.exportService = Objects.requireNonNull(exportService);
    }

    @PostConstruct
    public void recoverOrphans() {
        Instant cutoff = Instant.now().minus(ORPHAN_AGE_MINUTES, ChronoUnit.MINUTES);
        List<ExportJob> orphans = exportJobRepository.findOrphanedJobs(cutoff);
        if (!orphans.isEmpty()) {
            LOG.info("Recovering {} orphaned export jobs older than {} minutes", orphans.size(), ORPHAN_AGE_MINUTES);
            for (ExportJob job : orphans) {
                LOG.info("Resetting orphan job {} to PENDING", job.jobId());
                ExportJob reset = new ExportJob(
                    job.jobId(), job.tenantId(), job.userId(), job.name(), job.resourceType(),
                    job.queryFilter(), job.format(), job.destination(), job.destinationPath(),
                    ExportJob.Status.PENDING, job.totalRecords(), job.fileSizeBytes(), job.errorMessage(),
                    job.retryCount(), job.nextRetryAt(), job.parentJobId(), null, job.finishedAt(), job.createdAt()
                );
                exportJobRepository.save(reset);
            }
        }
    }

    @Scheduled(fixedDelay = 30_000)
    public void pollAndExecute() {
        Optional<ExportJob> claimed = exportJobRepository.claimPendingJob();
        if (claimed.isEmpty()) {
            return;
        }

        ExportJob job = claimed.get();
        LOG.info("Claimed export job {} (tenant={}, type={})", job.jobId(), job.tenantId(), job.resourceType());

        exportJobRepository.updateRunning(job.jobId(), Instant.now());
        ExportJob running = new ExportJob(
            job.jobId(), job.tenantId(), job.userId(), job.name(), job.resourceType(),
            job.queryFilter(), job.format(), job.destination(), job.destinationPath(),
            ExportJob.Status.RUNNING, job.totalRecords(), job.fileSizeBytes(), job.errorMessage(),
            job.retryCount(), job.nextRetryAt(), job.parentJobId(), Instant.now(), job.finishedAt(), job.createdAt()
        );

        try {
            exportService.executeExport(running);
        } catch (Exception e) {
            LOG.error("Unexpected error executing export job {} in scheduler", job.jobId(), e);
            ExportJob failed = new ExportJob(
                job.jobId(), job.tenantId(), job.userId(), job.name(), job.resourceType(),
                job.queryFilter(), job.format(), job.destination(), job.destinationPath(),
                ExportJob.Status.FAILED, null, null, e.getMessage(),
                job.retryCount() + 1, null, job.parentJobId(), running.startedAt(), Instant.now(), job.createdAt()
            );
            exportJobRepository.save(failed);
        }
    }
}
