package com.chorus.observe.service;

import com.chorus.observe.model.Dataset;
import com.chorus.observe.model.DatasetItem;
import com.chorus.observe.model.GeneratedEvalCase;
import com.chorus.observe.persistence.DatasetItemRepository;
import com.chorus.observe.persistence.DatasetRepository;
import com.chorus.observe.persistence.GeneratedEvalCaseRepository;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;

/**
 * Human review gate for generated eval cases.
 * Enforces the workflow: PENDING_REVIEW → APPROVED/REJECTED.
 * No direct path from GENERATED to APPROVED is permitted.
 */
public class EvalReviewService {

    private static final Logger LOG = LoggerFactory.getLogger(EvalReviewService.class);

    private final GeneratedEvalCaseRepository generatedEvalCaseRepository;
    private final DatasetRepository datasetRepository;
    private final DatasetItemRepository datasetItemRepository;

    public EvalReviewService(@NonNull GeneratedEvalCaseRepository generatedEvalCaseRepository,
                             @NonNull DatasetRepository datasetRepository,
                             @NonNull DatasetItemRepository datasetItemRepository) {
        this.generatedEvalCaseRepository = Objects.requireNonNull(generatedEvalCaseRepository);
        this.datasetRepository = Objects.requireNonNull(datasetRepository);
        this.datasetItemRepository = Objects.requireNonNull(datasetItemRepository);
    }

    /**
     * Lists generated eval cases pending human review.
     */
    public @NonNull List<GeneratedEvalCase> listPendingReview(int limit, int offset) {
        return generatedEvalCaseRepository.findByStatus(GeneratedEvalCase.Status.PENDING_REVIEW, limit, offset);
    }

    public long countPendingReview() {
        return generatedEvalCaseRepository.countByStatus(GeneratedEvalCase.Status.PENDING_REVIEW);
    }

    /**
     * Approves a generated eval case and optionally adds it to a dataset.
     *
     * @throws IllegalStateException if the case is not in PENDING_REVIEW status
     */
    public @NonNull GeneratedEvalCase approveCase(@NonNull String caseId,
                                                   @NonNull String reviewedBy,
                                                   @Nullable String reviewNotes,
                                                   @Nullable String targetDatasetId) {
        GeneratedEvalCase evalCase = generatedEvalCaseRepository.findById(caseId)
            .orElseThrow(() -> new IllegalArgumentException("Case not found: " + caseId));

        if (evalCase.status() != GeneratedEvalCase.Status.PENDING_REVIEW) {
            throw new IllegalStateException(
                "Case must be in PENDING_REVIEW status to approve. Current status: " + evalCase.status());
        }

        String datasetId = targetDatasetId;
        if (datasetId != null) {
            Dataset dataset = datasetRepository.findById(datasetId)
                .orElseThrow(() -> new IllegalArgumentException("Dataset not found: " + datasetId));

            String itemId = "di-" + UUID.randomUUID().toString().substring(0, 8);
            DatasetItem item = new DatasetItem(
                itemId, datasetId, evalCase.input(), evalCase.expectedOutput(),
                Map.of("sourceCaseId", evalCase.caseId(), "sourceRunId", evalCase.sourceRunId()),
                Map.of(), Instant.now()
            );
            datasetItemRepository.save(item);
            LOG.info("Added case {} to dataset {} as item {}", caseId, datasetId, itemId);
        }

        GeneratedEvalCase approved = new GeneratedEvalCase(
            evalCase.caseId(), evalCase.sourceRunId(), evalCase.sourceSpanId(),
            evalCase.input(), evalCase.expectedOutput(), evalCase.metadata(),
            GeneratedEvalCase.Status.APPROVED, reviewedBy, Instant.now(),
            reviewNotes, datasetId, evalCase.createdAt()
        );
        generatedEvalCaseRepository.save(approved);
        LOG.info("Approved case {} by {}", caseId, reviewedBy);
        return approved;
    }

    /**
     * Rejects a generated eval case.
     *
     * @throws IllegalStateException if the case is not in PENDING_REVIEW status
     */
    public @NonNull GeneratedEvalCase rejectCase(@NonNull String caseId,
                                                  @NonNull String reviewedBy,
                                                  @Nullable String reviewNotes) {
        GeneratedEvalCase evalCase = generatedEvalCaseRepository.findById(caseId)
            .orElseThrow(() -> new IllegalArgumentException("Case not found: " + caseId));

        if (evalCase.status() != GeneratedEvalCase.Status.PENDING_REVIEW) {
            throw new IllegalStateException(
                "Case must be in PENDING_REVIEW status to reject. Current status: " + evalCase.status());
        }

        GeneratedEvalCase rejected = new GeneratedEvalCase(
            evalCase.caseId(), evalCase.sourceRunId(), evalCase.sourceSpanId(),
            evalCase.input(), evalCase.expectedOutput(), evalCase.metadata(),
            GeneratedEvalCase.Status.REJECTED, reviewedBy, Instant.now(),
            reviewNotes, evalCase.datasetId(), evalCase.createdAt()
        );
        generatedEvalCaseRepository.save(rejected);
        LOG.info("Rejected case {} by {}", caseId, reviewedBy);
        return rejected;
    }

    /**
     * Moves a case from GENERATED to PENDING_REVIEW.
     * This is the only valid transition into the review queue.
     */
    public @NonNull GeneratedEvalCase submitForReview(@NonNull String caseId) {
        GeneratedEvalCase evalCase = generatedEvalCaseRepository.findById(caseId)
            .orElseThrow(() -> new IllegalArgumentException("Case not found: " + caseId));

        if (evalCase.status() != GeneratedEvalCase.Status.GENERATED) {
            throw new IllegalStateException(
                "Case must be in GENERATED status to submit for review. Current status: " + evalCase.status());
        }

        GeneratedEvalCase submitted = new GeneratedEvalCase(
            evalCase.caseId(), evalCase.sourceRunId(), evalCase.sourceSpanId(),
            evalCase.input(), evalCase.expectedOutput(), evalCase.metadata(),
            GeneratedEvalCase.Status.PENDING_REVIEW, null, null, null,
            evalCase.datasetId(), evalCase.createdAt()
        );
        generatedEvalCaseRepository.save(submitted);
        LOG.info("Submitted case {} for review", caseId);
        return submitted;
    }
}
