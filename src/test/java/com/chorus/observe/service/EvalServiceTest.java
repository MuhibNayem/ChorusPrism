package com.chorus.observe.service;

import com.chorus.engine.evals.EvalCase;
import com.chorus.engine.evals.EvalDataset;
import com.chorus.engine.evals.EvalReport;
import com.chorus.engine.evals.EvalRunner;
import com.chorus.engine.evals.ExactMatchScorer;
import com.chorus.observe.model.*;
import com.chorus.observe.persistence.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class EvalServiceTest {

    private EvalService evalService;
    private DatasetRepository datasetRepository;
    private DatasetItemRepository datasetItemRepository;
    private EvalRunRepository evalRunRepository;
    private EvalResultRepository evalResultRepository;

    @BeforeEach
    void setUp() {
        datasetRepository = new InMemoryDatasetRepository();
        datasetItemRepository = new InMemoryDatasetItemRepository();
        evalRunRepository = new InMemoryEvalRunRepository();
        evalResultRepository = new InMemoryEvalResultRepository();
        AgentInvoker fakeInvoker = (config, input) -> input.toUpperCase();
        evalService = new EvalService(datasetRepository, datasetItemRepository, evalRunRepository, evalResultRepository, fakeInvoker, new ObjectMapper());
    }

    @Test
    void shouldSubmitEvalRun() {
        datasetRepository.save(new Dataset("ds-1", "Test", null, Map.of(), "manual", Map.of(), null, null));
        datasetItemRepository.save(new DatasetItem("item-1", "ds-1", "hello", "HELLO", Map.of(), Map.of(), null));

        EvalRun run = evalService.submitEvalRun("ds-1", "My Eval", Map.of(), Map.of("scorers", List.of("exact_match")), 2);
        assertThat(run.evalRunId()).isNotBlank();
        assertThat(run.status()).isEqualTo(EvalRun.Status.PENDING);
    }

    @Test
    void shouldExecuteEvalAndPersistResults() throws Exception {
        datasetRepository.save(new Dataset("ds-1", "Test", null, Map.of(), "manual", Map.of(), null, null));
        datasetItemRepository.save(new DatasetItem("item-1", "ds-1", "hello", "HELLO", Map.of(), Map.of(), null));
        datasetItemRepository.save(new DatasetItem("item-2", "ds-1", "world", "WORLD", Map.of(), Map.of(), null));

        EvalRun run = evalService.submitEvalRun("ds-1", "My Eval", Map.of(), Map.of("scorers", List.of("exact_match")), 2);
        evalService.startEvalRun(run.evalRunId());

        // Wait for async execution
        TimeUnit.MILLISECONDS.sleep(500);

        EvalRun completed = evalRunRepository.findById(run.evalRunId()).orElseThrow();
        assertThat(completed.status()).isEqualTo(EvalRun.Status.COMPLETED);

        List<EvalResultRecord> results = evalResultRepository.findByEvalRunId(run.evalRunId());
        assertThat(results).hasSize(2);
        assertThat(results.get(0).passed()).isTrue();
    }

    @Test
    void shouldDetectRegressions() {
        datasetRepository.save(new Dataset("ds-1", "Test", null, Map.of(), "manual", Map.of(), null, null));
        datasetItemRepository.save(new DatasetItem("item-1", "ds-1", "a", "b", Map.of(), Map.of(), null));

        EvalRun runA = evalService.submitEvalRun("ds-1", "Run A", Map.of(), Map.of(), 1);
        EvalRun runB = evalService.submitEvalRun("ds-1", "Run B", Map.of(), Map.of(), 1);

        evalResultRepository.save(new EvalResultRecord("r1", runA.evalRunId(), "item-1", null, null, "x", 1.0, true, 0, null, null));
        evalResultRepository.save(new EvalResultRecord("r2", runB.evalRunId(), "item-1", null, null, "y", 0.0, false, 0, null, null));

        EvalService.RegressionReport report = evalService.compareRuns(runA.evalRunId(), runB.evalRunId());
        assertThat(report.regressions()).isEqualTo(1);
        assertThat(report.improvements()).isEqualTo(0);
        assertThat(report.scoreDelta()).isEqualTo(-1.0);
    }
}
