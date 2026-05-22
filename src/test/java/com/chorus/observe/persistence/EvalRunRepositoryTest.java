package com.chorus.observe.persistence;

import com.chorus.observe.model.EvalRun;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class EvalRunRepositoryTest {

    private EvalRunRepository repository;

    @BeforeEach
    void setUp() {
        this.repository = new InMemoryEvalRunRepository();
    }

    @Test
    void shouldSaveAndRetrieveEvalRun() {
        EvalRun run = new EvalRun("eval-1", "ds-1", "Test Eval", Map.of(), Map.of(), 8, EvalRun.Status.PENDING, 0, Map.of(), null, null, null);
        repository.save(run);

        Optional<EvalRun> found = repository.findById("eval-1");
        assertThat(found).isPresent();
        assertThat(found.get().evalRunId()).isEqualTo("eval-1");
        assertThat(found.get().status()).isEqualTo(EvalRun.Status.PENDING);
    }

    @Test
    void shouldListByDatasetId() {
        repository.save(new EvalRun("eval-1", "ds-1", null, Map.of(), Map.of(), 8, EvalRun.Status.COMPLETED, 100, Map.of(), null, null, null));
        repository.save(new EvalRun("eval-2", "ds-1", null, Map.of(), Map.of(), 8, EvalRun.Status.PENDING, 0, Map.of(), null, null, null));
        repository.save(new EvalRun("eval-3", "ds-2", null, Map.of(), Map.of(), 8, EvalRun.Status.COMPLETED, 100, Map.of(), null, null, null));

        List<EvalRun> byDataset = repository.findByDatasetId("ds-1");
        assertThat(byDataset).hasSize(2);

        List<EvalRun> byStatus = repository.findByStatus(EvalRun.Status.COMPLETED);
        assertThat(byStatus).hasSize(2);
    }

    @Test
    void shouldDeleteEvalRun() {
        repository.save(new EvalRun("eval-1", "ds-1", null, Map.of(), Map.of(), 8, EvalRun.Status.PENDING, 0, Map.of(), null, null, null));
        repository.deleteById("eval-1");
        assertThat(repository.findById("eval-1")).isEmpty();
    }
}
