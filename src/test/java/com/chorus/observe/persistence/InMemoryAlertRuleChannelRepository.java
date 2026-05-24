package com.chorus.observe.persistence;

import com.chorus.observe.model.AlertRuleChannel;

import java.util.*;
import java.util.stream.Collectors;

public class InMemoryAlertRuleChannelRepository extends AlertRuleChannelRepository {
    private final List<AlertRuleChannel> store = new ArrayList<>();

    public InMemoryAlertRuleChannelRepository() {
        super(null);
    }

    @Override
    public void save(AlertRuleChannel link) {
        store.removeIf(l -> l.ruleId().equals(link.ruleId()) && l.channelId().equals(link.channelId()));
        store.add(link);
    }

    @Override
    public List<AlertRuleChannel> findByRuleId(String ruleId) {
        return store.stream()
            .filter(l -> l.ruleId().equals(ruleId))
            .collect(Collectors.toList());
    }

    @Override
    public List<AlertRuleChannel> findByChannelId(String channelId) {
        return store.stream()
            .filter(l -> l.channelId().equals(channelId))
            .collect(Collectors.toList());
    }

    @Override
    public void deleteByRuleId(String ruleId) {
        store.removeIf(l -> l.ruleId().equals(ruleId));
    }

    @Override
    public void deleteByRuleIdAndChannelId(String ruleId, String channelId) {
        store.removeIf(l -> l.ruleId().equals(ruleId) && l.channelId().equals(channelId));
    }
}
