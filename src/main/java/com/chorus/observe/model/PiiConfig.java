package com.chorus.observe.model;

import java.util.List;

public record PiiConfig(
    boolean masterEnabled,
    List<PiiRule> rules
) {}
