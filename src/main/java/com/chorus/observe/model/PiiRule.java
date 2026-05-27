package com.chorus.observe.model;

public record PiiRule(
    String ruleId,
    String name,
    String category,
    String regexPattern,
    String replacement,
    boolean enabled,
    String severity
) {}
