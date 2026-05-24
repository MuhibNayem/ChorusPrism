package com.chorus.observe.api;

import com.chorus.observe.export.ExportService;
import com.chorus.observe.model.ExportJob;
import com.chorus.observe.model.PagedResult;
import com.chorus.observe.persistence.ExportJobRepository;
import com.chorus.observe.security.TenantContext;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

@PreAuthorize("hasAuthority('admin')")
@RestController
@RequestMapping("/api/v1/exports")
public class ExportController {

    private static final Logger LOG = LoggerFactory.getLogger(ExportController.class);

    private final ExportService exportService;
    private final ExportJobRepository exportJobRepository;

    public ExportController(@NonNull ExportService exportService, @NonNull ExportJobRepository exportJobRepository) {
        this.exportService = exportService;
        this.exportJobRepository = exportJobRepository;
    }

    @PostMapping
    public ResponseEntity<?> submitExport(@RequestBody Map<String, Object> request) {
        String tenantId = TenantContext.getTenantId();
        String userId = TenantContext.getUserId();
        if (userId == null) userId = "system";
        String name = (String) request.getOrDefault("name", "export");
        String resourceType = (String) request.get("resourceType");
        @SuppressWarnings("unchecked")
        Map<String, Object> queryFilter = (Map<String, Object>) request.getOrDefault("queryFilter", Map.of());
        String formatStr = (String) request.getOrDefault("format", "JSON");
        String destStr = (String) request.getOrDefault("destination", "FILE");
        String destPath = (String) request.get("destinationPath");

        ExportJob job = exportService.submitExport(tenantId, userId, name, resourceType, queryFilter,
            ExportJob.Format.valueOf(formatStr), ExportJob.Destination.valueOf(destStr), destPath);
        return ResponseEntity.ok(Map.of("jobId", job.jobId(), "status", job.status()));
    }

    @GetMapping
    public ResponseEntity<PagedResult<ExportJob>> listExports(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        String tenantId = TenantContext.getTenantId();
        int offset = page * size;
        List<ExportJob> jobs = exportJobRepository.findByTenant(tenantId, size, offset);
        long total = exportJobRepository.countByTenant(tenantId);
        return ResponseEntity.ok(new PagedResult<>(jobs, total, page, size));
    }

    @GetMapping("/{jobId}")
    public ResponseEntity<?> getExport(@PathVariable String jobId) {
        return exportJobRepository.findById(jobId)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    private static final Path EXPORT_BASE_DIR = Paths.get(System.getProperty("java.io.tmpdir"), "chorus-exports").toAbsolutePath().normalize();

    @GetMapping("/{jobId}/download")
    public ResponseEntity<?> downloadExport(@PathVariable String jobId) {
        String tenantId = TenantContext.getTenantId();
        return exportJobRepository.findById(jobId)
            .filter(job -> job.tenantId().equals(tenantId))
            .filter(job -> job.status() == ExportJob.Status.COMPLETED)
            .filter(job -> job.destination() == ExportJob.Destination.FILE)
            .map(job -> {
                try {
                    Path file = Path.of(job.destinationPath()).toAbsolutePath().normalize();
                    // Path traversal prevention: ensure the resolved path stays within the export base directory
                    if (!file.startsWith(EXPORT_BASE_DIR)) {
                        LOG.error("Path traversal attempt blocked: {} (base: {})", file, EXPORT_BASE_DIR);
                        return ResponseEntity.status(403).body(Map.of("error", "Invalid export file path"));
                    }
                    byte[] bytes = Files.readAllBytes(file);
                    String ext = job.format().name().toLowerCase();
                    String contentType = switch (job.format()) {
                        case JSON -> "application/json";
                        case CSV -> "text/csv";
                        case PARQUET -> "application/octet-stream";
                    };
                    return ResponseEntity.ok()
                        .header("Content-Disposition", "attachment; filename=\"" + job.name() + "." + ext + "\"")
                        .header("Content-Type", contentType)
                        .body(bytes);
                } catch (IOException e) {
                    return ResponseEntity.internalServerError().body(Map.of("error", "Failed to read export file"));
                }
            })
            .orElse(ResponseEntity.notFound().build());
    }
}
