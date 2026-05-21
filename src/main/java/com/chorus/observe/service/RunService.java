package com.chorus.observe.service;

import com.chorus.observe.model.Run;
import com.chorus.observe.persistence.RunRepository;
import com.chorus.observe.persistence.RunRepository.RunQuery;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Service layer for run operations.
 */
public class RunService {

    private final RunRepository runRepository;

    public RunService(@NonNull RunRepository runRepository) {
        this.runRepository = Objects.requireNonNull(runRepository);
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
}
