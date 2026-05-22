package com.chorus.observe.api;

import com.chorus.observe.export.ExportService;
import com.chorus.observe.model.ExportJob;
import com.chorus.observe.model.PagedResult;
import com.chorus.observe.persistence.ExportJobRepository;
import com.chorus.observe.security.TenantContext;
import org.jspecify.annotations.NonNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/exports")
public class ExportController {

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
}
