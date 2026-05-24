package com.chorus.observe.dto.scim;

import org.jspecify.annotations.NonNull;

import java.util.List;

public record ScimListResponse(
    @NonNull List<String> schemas,
    int totalResults,
    int startIndex,
    int itemsPerPage,
    @NonNull List<ScimUserDto> Resources
) {
    public ScimListResponse {
        schemas = List.of("urn:ietf:params:scim:api:messages:2.0:ListResponse");
    }
}
