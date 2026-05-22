package com.chorus.observe.api;

import com.chorus.observe.model.PagedResult;
import com.chorus.observe.model.ProvenanceEntry;
import com.chorus.observe.persistence.ProvenanceRepository;
import org.jspecify.annotations.NonNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;

/**
 * REST API v1 for provenance (causal DAG) — Chorus runs only.
 */
@RestController
@RequestMapping("/api/v1/runs/{runId}")
public class ProvenanceController {

    private final ProvenanceRepository provenanceRepository;

    public ProvenanceController(@NonNull ProvenanceRepository provenanceRepository) {
        this.provenanceRepository = Objects.requireNonNull(provenanceRepository);
    }

    @GetMapping("/provenance")
    public ResponseEntity<PagedResult<ProvenanceEntry>> getProvenance(
            @PathVariable @NonNull String runId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int offset = page * size;
        List<ProvenanceEntry> entries = provenanceRepository.findByRunId(runId, size, offset);
        long total = provenanceRepository.countByRunId(runId);
        return ResponseEntity.ok(new PagedResult<>(entries, total, page, size));
    }
}
