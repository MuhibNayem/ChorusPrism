package com.chorus.observe.service;

import com.chorus.observe.model.*;
import com.chorus.observe.persistence.*;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;

/**
 * Generates evaluation cases from production traces (runs).
 * Extracts input/output from LLM calls and creates cases in PENDING_REVIEW status.
 */
public class EvalGenerationService {

    private static final Logger LOG = LoggerFactory.getLogger(EvalGenerationService.class);

    private final RunRepository runRepository;
    private final LlmCallRepository llmCallRepository;
    private final SpanRepository spanRepository;
    private final GeneratedEvalCaseRepository generatedEvalCaseRepository;

    public EvalGenerationService(@NonNull RunRepository runRepository,
                                 @NonNull LlmCallRepository llmCallRepository,
                                 @NonNull SpanRepository spanRepository,
                                 @NonNull GeneratedEvalCaseRepository generatedEvalCaseRepository) {
        this.runRepository = Objects.requireNonNull(runRepository);
        this.llmCallRepository = Objects.requireNonNull(llmCallRepository);
        this.spanRepository = Objects.requireNonNull(spanRepository);
        this.generatedEvalCaseRepository = Objects.requireNonNull(generatedEvalCaseRepository);
    }

    /**
     * Generates eval cases from the given run IDs.
     *
     * @param runIds  production run IDs to extract cases from
     * @param tags    optional tags to attach to generated cases
     * @return list of generated case IDs
     */
    public @NonNull List<String> generateCasesFromRuns(@NonNull List<String> runIds,
                                                        @Nullable Map<String, String> tags) {
        List<String> generatedCaseIds = new ArrayList<>();
        for (String runId : runIds) {
            Optional<Run> runOpt = runRepository.findById(runId);
            if (runOpt.isEmpty()) {
                LOG.warn("Run {} not found, skipping", runId);
                continue;
            }

            List<LlmCall> calls = llmCallRepository.findByRunId(runId);
            if (calls.isEmpty()) {
                LOG.warn("Run {} has no LLM calls, skipping", runId);
                continue;
            }

            for (LlmCall call : calls) {
                String input = extractInput(call);
                String expectedOutput = call.completion();
                if (input == null || input.isBlank()) continue;

                String caseId = "gec-" + UUID.randomUUID().toString().substring(0, 8);
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("source", "generated");
                metadata.put("model", call.model());
                metadata.put("provider", call.provider());
                if (tags != null) metadata.put("tags", tags);

                GeneratedEvalCase evalCase = new GeneratedEvalCase(
                    caseId, runId, call.spanId(), input, expectedOutput,
                    metadata, GeneratedEvalCase.Status.PENDING_REVIEW,
                    null, null, null, null, Instant.now()
                );
                generatedEvalCaseRepository.save(evalCase);
                generatedCaseIds.add(caseId);
            }
        }
        LOG.info("Generated {} eval cases from {} runs", generatedCaseIds.size(), runIds.size());
        return generatedCaseIds;
    }

    private @Nullable String extractInput(@NonNull LlmCall call) {
        if (call.prompt() != null && !call.prompt().isBlank()) {
            return call.prompt();
        }
        if (call.messages() != null && !call.messages().isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (LlmCall.LlmMessage msg : call.messages()) {
                sb.append(msg.role()).append(": ").append(msg.text()).append("\n");
            }
            return sb.toString().trim();
        }
        return null;
    }
}
