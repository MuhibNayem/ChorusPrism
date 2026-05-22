package com.chorus.observe.model;

import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Objects;

/**
 * Paginated result wrapper for list endpoints.
 */
public record PagedResult<T>(
    @NonNull List<T> items,
    long total,
    int page,
    int size
) {
    public PagedResult {
        Objects.requireNonNull(items, "items");
        if (page < 0) page = 0;
        if (size < 1) size = 20;
    }
}
