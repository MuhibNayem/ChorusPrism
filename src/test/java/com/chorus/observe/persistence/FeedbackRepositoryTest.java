package com.chorus.observe.persistence;

import com.chorus.observe.model.Feedback;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class FeedbackRepositoryTest {

    private FeedbackRepository repository;

    @BeforeEach
    void setUp() {
        this.repository = new InMemoryFeedbackRepository();
    }

    @Test
    void shouldSaveAndRetrieveFeedback() {
        Feedback feedback = new Feedback(
            "fb-1", "run-1", null, 4.5, "good", "Great response", "human", Instant.now()
        );

        repository.save(feedback);

        List<Feedback> found = repository.findByRunId("run-1");
        assertThat(found).hasSize(1);
        assertThat(found.get(0).score()).isEqualTo(4.5);

        Optional<Feedback> byId = repository.findById("fb-1");
        assertThat(byId).isPresent();
    }
}
