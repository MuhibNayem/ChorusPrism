package com.chorus.observe.persistence;

import com.chorus.observe.model.Dataset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class DatasetRepositoryTest {

    private DatasetRepository repository;

    @BeforeEach
    void setUp() {
        this.repository = new InMemoryDatasetRepository();
    }

    @Test
    void shouldSaveAndRetrieveDataset() {
        Dataset dataset = new Dataset("ds-1", "Test Dataset", "Desc", Map.of("env", "test"), "manual", Map.of(), null, null);
        repository.save(dataset);

        Optional<Dataset> found = repository.findById("ds-1");
        assertThat(found).isPresent();
        assertThat(found.get().datasetId()).isEqualTo("ds-1");
        assertThat(found.get().name()).isEqualTo("Test Dataset");
    }

    @Test
    void shouldListDatasetsOrderedByCreatedAt() {
        repository.save(new Dataset("ds-1", "A", null, Map.of(), "manual", Map.of(), null, null));
        repository.save(new Dataset("ds-2", "B", null, Map.of(), "traces", Map.of(), null, null));

        List<Dataset> all = repository.findAll();
        assertThat(all).hasSize(2);

        List<Dataset> bySource = repository.findBySource("traces");
        assertThat(bySource).hasSize(1);
        assertThat(bySource.get(0).datasetId()).isEqualTo("ds-2");
    }

    @Test
    void shouldDeleteDataset() {
        repository.save(new Dataset("ds-1", "A", null, Map.of(), "manual", Map.of(), null, null));
        repository.deleteById("ds-1");
        assertThat(repository.findById("ds-1")).isEmpty();
    }
}
