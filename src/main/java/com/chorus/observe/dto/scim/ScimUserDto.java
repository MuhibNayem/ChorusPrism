package com.chorus.observe.dto.scim;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

public record ScimUserDto(
    @Nullable String id,
    @NonNull List<String> schemas,
    @NonNull String userName,
    @Nullable String displayName,
    boolean active,
    @Nullable Map<String, Object> name,
    @Nullable List<Email> emails,
    @Nullable Meta meta
) {
    public record Email(@NonNull String value, boolean primary) {}
    public record Meta(@NonNull String resourceType, @Nullable String created, @Nullable String lastModified) {}
}
