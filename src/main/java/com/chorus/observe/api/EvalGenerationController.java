package com.chorus.observe.api;

import com.chorus.observe.security.TenantContext;
import com.chorus.observe.service.EvalGenerationService;
import org.jspecify.annotations.NonNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * API for generating evaluation cases from production traces.
 */
@RestController
@RequestMapping("/api/v1/eval-generation")
public class EvalGenerationController {

    private final EvalGenerationService evalGenerationService;

    public EvalGenerationController(@NonNull EvalGenerationService evalGenerationService) {
        this.evalGenerationService = evalGenerationService;
    }

    @PostMapping
    public ResponseEntity<?> generateCases(@RequestBody Map<String, Object> request) {
        @SuppressWarnings("unchecked")
        List<String> runIds = (List<String>) request.get("runIds");
        @SuppressWarnings("unchecked")
        Map<String, String> tags = (Map<String, String>) request.getOrDefault("tags", Map.of());

        if (runIds == null || runIds.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "runIds is required and must not be empty"));
        }

        List<String> caseIds = evalGenerationService.generateCasesFromRuns(runIds, tags);
        return ResponseEntity.ok(Map.of(
            "generatedCaseIds", caseIds,
            "count", caseIds.size()
        ));
    }
}
