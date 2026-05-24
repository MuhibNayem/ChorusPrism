package com.chorus.observe.api;

import com.chorus.observe.model.GeneratedEvalCase;
import com.chorus.observe.security.TenantContext;
import com.chorus.observe.service.EvalReviewService;
import org.jspecify.annotations.NonNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * API for the human review gate of generated eval cases.
 */
@RestController
@RequestMapping("/api/v1/eval-review")
public class EvalReviewController {

    private final EvalReviewService evalReviewService;

    public EvalReviewController(@NonNull EvalReviewService evalReviewService) {
        this.evalReviewService = evalReviewService;
    }

    @GetMapping("/pending")
    public ResponseEntity<?> listPendingReview(
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        List<GeneratedEvalCase> cases = evalReviewService.listPendingReview(limit, offset);
        long total = evalReviewService.countPendingReview();
        return ResponseEntity.ok(Map.of(
            "cases", cases,
            "total", total,
            "limit", limit,
            "offset", offset
        ));
    }

    @PostMapping("/{caseId}/approve")
    public ResponseEntity<?> approveCase(
            @PathVariable String caseId,
            @RequestBody Map<String, Object> request) {
        String reviewedBy = TenantContext.getUserId() != null ? TenantContext.getUserId() : "system";
        String reviewNotes = (String) request.get("reviewNotes");
        String datasetId = (String) request.get("datasetId");

        try {
            GeneratedEvalCase approved = evalReviewService.approveCase(caseId, reviewedBy, reviewNotes, datasetId);
            return ResponseEntity.ok(Map.of(
                "caseId", approved.caseId(),
                "status", approved.status().name(),
                "datasetId", approved.datasetId()
            ));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/{caseId}/reject")
    public ResponseEntity<?> rejectCase(
            @PathVariable String caseId,
            @RequestBody Map<String, Object> request) {
        String reviewedBy = TenantContext.getUserId() != null ? TenantContext.getUserId() : "system";
        String reviewNotes = (String) request.get("reviewNotes");

        try {
            GeneratedEvalCase rejected = evalReviewService.rejectCase(caseId, reviewedBy, reviewNotes);
            return ResponseEntity.ok(Map.of(
                "caseId", rejected.caseId(),
                "status", rejected.status().name()
            ));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/{caseId}/submit")
    public ResponseEntity<?> submitForReview(@PathVariable String caseId) {
        try {
            GeneratedEvalCase submitted = evalReviewService.submitForReview(caseId);
            return ResponseEntity.ok(Map.of(
                "caseId", submitted.caseId(),
                "status", submitted.status().name()
            ));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
