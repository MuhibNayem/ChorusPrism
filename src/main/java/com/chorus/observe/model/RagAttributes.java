package com.chorus.observe.model;

/**
 * OpenTelemetry attribute names for RAG retrieval spans.
 * <p>
 * Use these constants when instrumenting your retrieval pipeline so Chorus Observe
 * can automatically extract RAGAS metrics, cluster queries, and detect embedding drift.
 *
 * <h3>Minimal example (any OTel SDK)</h3>
 * <pre>
 * // Python
 * with tracer.start_as_current_span("rag.retrieve") as span:
 *     span.set_attribute(RagAttributes.QUERY_TEXT, "What is the refund policy?")
 *     span.set_attribute(RagAttributes.COLLECTION, "support_kb")
 *     span.set_attribute(RagAttributes.TOP_K, 5)
 *     span.set_attribute(RagAttributes.CHUNK_COUNT, len(chunks))
 *     span.set_attribute(RagAttributes.SIMILARITY_SCORES, str(scores))  # "[0.92, 0.87, 0.81]"
 *     span.set_attribute(RagAttributes.CACHE_HIT, "false")
 * </pre>
 *
 * <h3>Required vs optional</h3>
 * <ul>
 *   <li>{@link #QUERY_TEXT} — required; the span is ignored without it</li>
 *   <li>All other attributes — optional but strongly recommended for full metrics</li>
 * </ul>
 *
 * <h3>Semantic convention aliases</h3>
 * Chorus also accepts the OTel {@code gen_ai.retrieval.*} and {@code db.vector.*} aliases
 * listed in this class. Prefer the {@code rag.*} names for new integrations.
 */
public final class RagAttributes {

    private RagAttributes() {}

    // ── Chorus-native names (preferred) ──────────────────────────────────────

    /** The query submitted to the retrieval system. <b>Required.</b> */
    public static final String QUERY_TEXT        = "rag.query_text";

    /** Vector store / index collection queried (e.g. {@code "support_kb"}). */
    public static final String COLLECTION        = "rag.collection";

    /** Maximum number of chunks requested from the retriever. */
    public static final String TOP_K             = "rag.top_k";

    /** Number of chunks actually returned. Used to compute context precision. */
    public static final String CHUNK_COUNT       = "rag.chunk_count";

    /** Retrieved chunk identifiers as a JSON array string: {@code "[chunk_1, chunk_2]"}. */
    public static final String RETRIEVED_CHUNKS  = "rag.retrieved_chunks";

    /**
     * Per-chunk similarity scores as a JSON array string: {@code "[0.92, 0.87, 0.81]"}.
     * Used to compute context precision and context recall.
     */
    public static final String SIMILARITY_SCORES = "rag.similarity_scores";

    /** Whether the result was served from cache. Value: {@code "true"} or {@code "false"}. */
    public static final String CACHE_HIT         = "rag.cache_hit";

    // ── OTel semantic convention aliases (also accepted) ─────────────────────

    /** OTel alias for {@link #QUERY_TEXT}. */
    public static final String GEN_AI_RETRIEVAL_QUERY    = "gen_ai.retrieval.query";

    /** OTel alias for {@link #COLLECTION}. */
    public static final String DB_VECTOR_COLLECTION_NAME = "db.vector.collection_name";

    /** OTel alias for {@link #TOP_K}. */
    public static final String DB_VECTOR_QUERY_TOP_K     = "db.vector.query.top_k";

    /** OTel alias for {@link #CHUNK_COUNT}. */
    public static final String DB_VECTOR_RESULT_COUNT    = "db.vector.result.count";

    /** OTel agent identifier, surfaced as metadata on the stored query. */
    public static final String GEN_AI_AGENT_ID           = "gen_ai.agent.id";
}
