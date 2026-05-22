package com.chorus.observe.service;

import com.chorus.observe.model.EvalResultRecord;
import com.chorus.observe.model.Run;
import com.chorus.observe.persistence.EvalResultRepository;
import com.chorus.observe.persistence.RunRepository;
import com.chorus.observe.persistence.RunRepository.RunQuery;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service layer for run operations.
 */
public class RunService {

    private final RunRepository runRepository;
    private final EvalResultRepository evalResultRepository;

    public RunService(@NonNull RunRepository runRepository, @NonNull EvalResultRepository evalResultRepository) {
        this.runRepository = Objects.requireNonNull(runRepository);
        this.evalResultRepository = Objects.requireNonNull(evalResultRepository);
    }

    public @NonNull Optional<Run> getRun(@NonNull String runId) {
        return runRepository.findById(runId);
    }

    public @NonNull List<Run> listRuns(@NonNull RunQuery query) {
        return runRepository.findAll(query);
    }

    public long countRuns(@NonNull RunQuery query) {
        return runRepository.count(query);
    }

    public boolean runExists(@NonNull String runId) {
        return runRepository.exists(runId);
    }

    public @NonNull List<RunEvalSummary> getEvalSummariesForRuns(@NonNull List<String> runIds) {
        List<EvalResultRecord> results = evalResultRepository.findByRunIds(runIds);
        Map<String, List<EvalResultRecord>> byRunId = results.stream()
            .filter(r -> r.runId() != null)
            .collect(Collectors.groupingBy(EvalResultRecord::runId));

        return byRunId.entrySet().stream()
            .map(entry -> {
                String runId = entry.getKey();
                List<EvalResultRecord> records = entry.getValue();
                String evalRunId = records.isEmpty() ? "" : records.get(0).evalRunId();
                double avgScore = records.stream().mapToDouble(EvalResultRecord::score).average().orElse(0.0);
                long passedCount = records.stream().filter(EvalResultRecord::passed).count();
                return new RunEvalSummary(runId, evalRunId, avgScore, (int) passedCount, records.size());
            })
            .toList();
    }

    public @Nullable RunEvalSummary getEvalSummaryForRun(@NonNull String runId) {
        List<RunEvalSummary> summaries = getEvalSummariesForRuns(List.of(runId));
        return summaries.isEmpty() ? null : summaries.get(0);
    }

    public record RunEvalSummary(
        @NonNull String runId,
        @NonNull String evalRunId,
        double avgScore,
        int passedCount,
        int totalCount
    ) {}
}
