package com.chorus.observe.persistence;

import com.chorus.observe.model.Run;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class RunRepositoryTest {

    private RunRepository repository;

    @BeforeEach
    void setUp() {
        this.repository = new InMemoryRunRepository();
    }

    @Test
    void shouldSaveAndRetrieveRun() {
        Run run = new Run(
            "run-1", "chorus", "agent-1", "gpt-4o",
            Instant.now(), Instant.now(), Run.Status.SUCCESS,
            Map.of("env", "test"), Map.of("key", "value"),
            100, BigDecimal.valueOf(0.01), 1200
        );

        repository.save(run);

        Optional<Run> found = repository.findById("run-1");
        assertThat(found).isPresent();
        assertThat(found.get().runId()).isEqualTo("run-1");
        assertThat(found.get().framework()).isEqualTo("chorus");
        assertThat(found.get().totalTokens()).isEqualTo(100);
    }

    @Test
    void shouldListRunsWithPagination() {
        for (int i = 0; i < 5; i++) {
            repository.save(new Run(
                "run-" + i, "chorus", "agent-1", "gpt-4o",
                Instant.now().plusSeconds(i), Instant.now().plusSeconds(i), Run.Status.SUCCESS,
                Map.of(), Map.of(), 100, BigDecimal.ZERO, 1000
            ));
        }

        RunRepository.RunQuery query = new RunRepository.RunQuery(
            null, null, null, null, null, null, null, null, null, "start_time", "DESC", 3, 0
        );
        List<Run> runs = repository.findAll(query);
        assertThat(runs).hasSize(3);
    }

    @Test
    void shouldFilterByFramework() {
        repository.save(new Run("run-a", "chorus", "agent-1", null,
            Instant.now(), null, Run.Status.RUNNING, Map.of(), Map.of(), 0, BigDecimal.ZERO, 0));
        repository.save(new Run("run-b", "langchain", "agent-2", null,
            Instant.now(), null, Run.Status.RUNNING, Map.of(), Map.of(), 0, BigDecimal.ZERO, 0));

        RunRepository.RunQuery query = new RunRepository.RunQuery(
            "chorus", null, null, null, null, null, null, null, null, "start_time", "DESC", 10, 0
        );
        List<Run> runs = repository.findAll(query);
        assertThat(runs).hasSize(1);
        assertThat(runs.get(0).runId()).isEqualTo("run-a");
    }
}
