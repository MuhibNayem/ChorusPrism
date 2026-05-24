package com.chorus.observe.event;

import com.chorus.observe.model.Run;
import org.jspecify.annotations.NonNull;
import org.springframework.context.ApplicationEvent;

import java.util.Objects;

/**
 * Published when a run reaches a terminal state (SUCCESS or ERROR) for the first time.
 */
public class RunCompletedEvent extends ApplicationEvent {

    private final @NonNull String runId;
    private final @NonNull String tenantId;
    private final Run.@NonNull Status status;

    public RunCompletedEvent(@NonNull Object source, @NonNull String runId,
                             @NonNull String tenantId, Run.@NonNull Status status) {
        super(source);
        this.runId = Objects.requireNonNull(runId);
        this.tenantId = Objects.requireNonNull(tenantId);
        this.status = Objects.requireNonNull(status);
    }

    public @NonNull String runId() { return runId; }
    public @NonNull String tenantId() { return tenantId; }
    public Run.@NonNull Status status() { return status; }
}
