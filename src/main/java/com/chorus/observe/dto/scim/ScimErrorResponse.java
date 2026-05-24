package com.chorus.observe.dto.scim;

import org.jspecify.annotations.NonNull;

import java.util.List;

public record ScimErrorResponse(
    @NonNull List<String> schemas,
    @NonNull String detail,
    int status
) {
    public ScimErrorResponse {
        schemas = List.of("urn:ietf:params:scim:api:messages:2.0:Error");
    }

    public static ScimErrorResponse of(int status, @NonNull String detail) {
        return new ScimErrorResponse(List.of(), detail, status);
    }
}
