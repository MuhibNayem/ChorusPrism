package com.chorus.observe.model;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

public record SsoSpKey(
    @Nullable UUID id,
    @NonNull String tenantId,
    @NonNull String privateKeyEncrypted,
    @NonNull String certPem,
    @NonNull String algorithm,
    int keySizeBits,
    @NonNull Instant createdAt
) {}
