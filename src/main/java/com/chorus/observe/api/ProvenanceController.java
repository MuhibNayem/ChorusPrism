package com.chorus.observe.api;

import com.chorus.observe.model.ProvenanceEntry;
import com.chorus.observe.persistence.ProvenanceRepository;
import org.jspecify.annotations.NonNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
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
    public ResponseEntity<List<ProvenanceEntry>> getProvenance(@PathVariable @NonNull String runId) {
        List<ProvenanceEntry> entries = provenanceRepository.findByRunId(runId);
        return ResponseEntity.ok(entries);
    }
}
