package com.chorus.observe.model;

import org.jspecify.annotations.NonNull;

public record RoleMapping(
    @NonNull String claim,
    @NonNull String value,
    @NonNull String role
) {}
