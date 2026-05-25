package com.chorus.observe.service;

import com.chorus.observe.model.RagQuery;
import com.chorus.observe.persistence.RagQueryRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Asynchronously scores RAG queries with RAGAS-style metrics:
 * <ul>
 *   <li><b>faithfulness</b> — via LLM-as-judge: is the answer grounded in the retrieved context?</li>
 *   <li><b>answerRelevancy</b> — via LLM-as-judge: how well does the answer address the query?</li>
 * </ul>
 * <p>
 * Deterministic scores (contextPrecision, contextRecall) are computed synchronously at
 * ingestion time inside {@link RagQuery#ofIngestion}.
 * <p>
 * Falls back gracefully when {@code llmJudgeUrl} is not configured — scores remain null.
 */
public class RagScoringService {

    private static final Logger LOG = LoggerFactory.getLogger(RagScoringService.class);
    private static final int SCORING_THREADS = 4;

    private final RagQueryRepository ragQueryRepository;
    private final ObjectMapper mapper;
    private final @Nullable String llmJudgeUrl;
    private final HttpClient httpClient;
    private final ExecutorService executor;

    public RagScoringService(
            @NonNull RagQueryRepository ragQueryRepository,
            @NonNull ObjectMapper mapper,
            @Nullable String llmJudgeUrl) {
        this.ragQueryRepository = Objects.requireNonNull(ragQueryRepository);
        this.mapper = Objects.requireNonNull(mapper);
        this.llmJudgeUrl = llmJudgeUrl;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
        this.executor = Executors.newFixedThreadPool(SCORING_THREADS, r -> {
            Thread t = new Thread(r, "rag-scorer");
            t.setDaemon(true);
            return t;
        });
        LOG.info("RagScoringService started. LLM judge: {}",
            llmJudgeUrl != null ? "configured" : "not configured (faithfulness scoring disabled)");
    }

    /**
     * Enqueues async scoring for a freshly ingested RAG query.
     */
    public void scoreAsync(@NonNull RagQuery query) {
        if (llmJudgeUrl == null) return;
        executor.submit(() -> {
            try {
                scoreOne(query);
            } catch (Exception e) {
                LOG.warn("RAG scoring failed for query {}: {}", query.queryId(), e.getMessage());
            }
        });
    }

    private void scoreOne(@NonNull RagQuery query) throws Exception {
        if (query.retrievedChunks() == null) return;

        Double faithfulness    = callJudge("faithfulness",    query);
        Double answerRelevancy = callJudge("answer_relevancy", query);

        if (faithfulness != null || answerRelevancy != null) {
            ragQueryRepository.updateScores(query.queryId(), faithfulness, answerRelevancy);
            LOG.debug("Scored RAG query {}: faith={} rel={}", query.queryId(), faithfulness, answerRelevancy);
        }
    }

    private @Nullable Double callJudge(@NonNull String task, @NonNull RagQuery query) {
        try {
            Map<String, Object> payload = Map.of(
                "task",     task,
                "query",    query.query(),
                "context",  query.retrievedChunks() != null ? query.retrievedChunks() : "",
                "scores",   query.similarityScores() != null ? query.similarityScores() : "[]"
            );
            String body = mapper.writeValueAsString(payload);
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(llmJudgeUrl))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(Duration.ofSeconds(30))
                .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                Map<?, ?> result = mapper.readValue(response.body(), Map.class);
                Object scoreObj = result.get("score");
                if (scoreObj instanceof Number n) {
                    return Math.min(1.0, Math.max(0.0, n.doubleValue()));
                }
            }
        } catch (Exception e) {
            LOG.debug("Judge call failed for task '{}': {}", task, e.getMessage());
        }
        return null;
    }
}
