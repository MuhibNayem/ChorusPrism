package com.chorus.observe.persistence;

import com.chorus.observe.model.PromptTag;

import java.util.*;
import java.util.stream.Collectors;

public class InMemoryPromptTagRepository extends PromptTagRepository {
    private final List<PromptTag> store = new ArrayList<>();

    public InMemoryPromptTagRepository() {
        super(null);
    }

    @Override
    public void save(PromptTag tag) {
        store.removeIf(t -> t.versionId().equals(tag.versionId()) && t.tagName().equals(tag.tagName()));
        store.add(tag);
    }

    @Override
    public void delete(String versionId, String tagName) {
        store.removeIf(t -> t.versionId().equals(versionId) && t.tagName().equals(tagName));
    }

    @Override
    public void deleteByVersionId(String versionId) {
        store.removeIf(t -> t.versionId().equals(versionId));
    }

    @Override
    public List<PromptTag> findByVersionId(String versionId) {
        return store.stream().filter(t -> t.versionId().equals(versionId)).sorted(Comparator.comparing(PromptTag::tagName)).collect(Collectors.toList());
    }

    @Override
    public List<PromptTag> findByVersionId(String versionId, int limit, int offset) {
        return store.stream().filter(t -> t.versionId().equals(versionId)).sorted(Comparator.comparing(PromptTag::tagName)).skip(offset).limit(limit).collect(Collectors.toList());
    }

    @Override
    public long countByVersionId(String versionId) {
        return store.stream().filter(t -> t.versionId().equals(versionId)).count();
    }

    @Override
    public List<PromptTag> findByTagName(String tagName) {
        return store.stream().filter(t -> t.tagName().equals(tagName)).sorted(Comparator.comparing(PromptTag::createdAt).reversed()).collect(Collectors.toList());
    }

    @Override
    public List<PromptTag> findByTagName(String tagName, int limit, int offset) {
        return store.stream().filter(t -> t.tagName().equals(tagName)).sorted(Comparator.comparing(PromptTag::createdAt).reversed()).skip(offset).limit(limit).collect(Collectors.toList());
    }

    @Override
    public long countByTagName(String tagName) {
        return store.stream().filter(t -> t.tagName().equals(tagName)).count();
    }

    @Override
    public Optional<PromptTag> findByVersionAndTag(String versionId, String tagName) {
        return store.stream().filter(t -> t.versionId().equals(versionId) && t.tagName().equals(tagName)).findFirst();
    }

    @Override
    public List<PromptTag> findAll() {
        return store.stream().sorted(Comparator.comparing(PromptTag::createdAt).reversed()).collect(Collectors.toList());
    }

    @Override
    public List<PromptTag> findAll(int limit, int offset) {
        return store.stream().sorted(Comparator.comparing(PromptTag::createdAt).reversed()).skip(offset).limit(limit).collect(Collectors.toList());
    }

    @Override
    public long count() {
        return store.size();
    }
}
