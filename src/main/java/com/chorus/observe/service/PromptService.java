package com.chorus.observe.service;

import com.chorus.observe.model.PagedResult;
import com.chorus.observe.model.PromptAbTest;
import com.chorus.observe.model.PromptTag;
import com.chorus.observe.model.PromptVersion;
import com.chorus.observe.persistence.PromptAbTestRepository;
import com.chorus.observe.persistence.PromptTagRepository;
import com.chorus.observe.persistence.PromptVersionRepository;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for prompt registry, tagging, and A/B testing.
 */
public class PromptService {

    private final PromptVersionRepository promptVersionRepository;
    private final PromptTagRepository promptTagRepository;
    private final PromptAbTestRepository promptAbTestRepository;

    public PromptService(
            @NonNull PromptVersionRepository promptVersionRepository,
            @NonNull PromptTagRepository promptTagRepository,
            @NonNull PromptAbTestRepository promptAbTestRepository) {
        this.promptVersionRepository = Objects.requireNonNull(promptVersionRepository);
        this.promptTagRepository = Objects.requireNonNull(promptTagRepository);
        this.promptAbTestRepository = Objects.requireNonNull(promptAbTestRepository);
    }

    public @NonNull PromptVersion createVersion(@NonNull String name, @NonNull String content, @Nullable String model, @Nullable Double temperature, @Nullable Integer maxTokens, @Nullable String createdBy) {
        String versionId = "prompt-" + UUID.randomUUID().toString().substring(0, 8);
        PromptVersion version = new PromptVersion(versionId, name, content, model, temperature, maxTokens, Map.of(), createdBy, Instant.now());
        promptVersionRepository.save(version);
        return version;
    }

    public @NonNull Optional<PromptVersion> getVersion(@NonNull String versionId) {
        return promptVersionRepository.findById(versionId);
    }

    public @NonNull List<PromptVersion> listVersions() {
        return promptVersionRepository.findAll();
    }

    public @NonNull PagedResult<PromptVersion> listVersions(int page, int size) {
        int offset = page * size;
        return new PagedResult<>(promptVersionRepository.findAll(size, offset), promptVersionRepository.count(), page, size);
    }

    public @NonNull List<PromptVersion> listVersionsByName(@NonNull String name) {
        return promptVersionRepository.findByName(name);
    }

    public @NonNull PagedResult<PromptVersion> listVersionsByName(@NonNull String name, int page, int size) {
        int offset = page * size;
        return new PagedResult<>(promptVersionRepository.findByName(name, size, offset), promptVersionRepository.countByName(name), page, size);
    }

    public @NonNull List<PromptVersion> listVersionsByTag(@NonNull String tagName) {
        List<PromptTag> tags = promptTagRepository.findByTagName(tagName);
        return tags.stream().map(t -> promptVersionRepository.findById(t.versionId())).flatMap(Optional::stream).toList();
    }

    public void deleteVersion(@NonNull String versionId) {
        promptTagRepository.deleteByVersionId(versionId);
        promptVersionRepository.deleteById(versionId);
    }

    public @NonNull PromptTag addTag(@NonNull String versionId, @NonNull String tagName) {
        PromptTag tag = new PromptTag(versionId, tagName, Instant.now());
        promptTagRepository.save(tag);
        return tag;
    }

    public void removeTag(@NonNull String versionId, @NonNull String tagName) {
        promptTagRepository.delete(versionId, tagName);
    }

    public @NonNull List<PromptTag> getTags(@NonNull String versionId) {
        return promptTagRepository.findByVersionId(versionId);
    }

    public @NonNull PagedResult<PromptTag> getTags(@NonNull String versionId, int page, int size) {
        int offset = page * size;
        return new PagedResult<>(promptTagRepository.findByVersionId(versionId, size, offset), promptTagRepository.countByVersionId(versionId), page, size);
    }

    public @NonNull PromptAbTest createAbTest(@Nullable String datasetId, @NonNull String promptAId, @NonNull String promptBId) {
        String testId = "ab-" + UUID.randomUUID().toString().substring(0, 8);
        PromptAbTest test = new PromptAbTest(testId, datasetId, promptAId, promptBId, PromptAbTest.Status.PENDING, null, null, Map.of(), Instant.now(), null);
        promptAbTestRepository.save(test);
        return test;
    }

    public @NonNull Optional<PromptAbTest> getAbTest(@NonNull String testId) {
        return promptAbTestRepository.findById(testId);
    }

    public @NonNull List<PromptAbTest> listAbTests() {
        return promptAbTestRepository.findAll();
    }

    public @NonNull PagedResult<PromptAbTest> listAbTests(int page, int size) {
        int offset = page * size;
        return new PagedResult<>(promptAbTestRepository.findAll(size, offset), promptAbTestRepository.count(), page, size);
    }

    public void completeAbTest(@NonNull String testId, @Nullable String winnerId, @Nullable Double pValue, @NonNull Map<String, Object> summaryMetrics) {
        Optional<PromptAbTest> opt = promptAbTestRepository.findById(testId);
        if (opt.isEmpty()) return;
        PromptAbTest test = opt.get();
        promptAbTestRepository.save(new PromptAbTest(
            test.testId(), test.datasetId(), test.promptAId(), test.promptBId(),
            PromptAbTest.Status.COMPLETED, winnerId, pValue, summaryMetrics,
            test.createdAt(), Instant.now()
        ));
    }

    public void deleteAbTest(@NonNull String testId) {
        promptAbTestRepository.deleteById(testId);
    }
}
