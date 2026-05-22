package com.chorus.observe.embedding;

import org.jspecify.annotations.NonNull;

/**
 * Dedicated invoker for text embedding endpoints.
 * <p>
 * Separated from {@link com.chorus.observe.service.AgentInvoker} because embedding APIs
 * typically have different endpoints, request formats, and response shapes than chat APIs.
 */
public interface EmbeddingInvoker {

    /**
     * Generate an embedding vector for the given text.
     *
     * @param model the embedding model identifier
     * @param text  the text to embed
     * @return embedding vector (float array)
     */
    @NonNull float[] embed(@NonNull String model, @NonNull String text);
}
