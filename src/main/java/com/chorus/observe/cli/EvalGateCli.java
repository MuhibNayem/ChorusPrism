package com.chorus.observe.cli;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * CLI tool for CI/CD eval gate integration.
 * <p>
 * Usage:
 * <pre>
 *   java EvalGateCli --api-url=http://chorus:8080 --dataset-id=ds-1 \
 *                    --threshold=0.85 --token=$CHORUS_TOKEN
 * </pre>
 * <p>
 * Exit codes:
 * <ul>
 *   <li>0 — eval passed (score >= threshold)</li>
 *   <li>1 — eval failed (score &lt; threshold)</li>
 *   <li>2 — Chorus API unreachable or unexpected error</li>
 * </ul>
 */
public class EvalGateCli {

    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static void main(String[] args) {
        Config config = parseArgs(args);
        if (config == null) {
            printUsage();
            System.exit(2);
            return;
        }

        try {
            // 1. Submit eval run
            String evalRunId = submitEvalRun(config);
            if (evalRunId == null) {
                System.err.println("Failed to submit eval run");
                System.exit(2);
                return;
            }

            // 2. Poll for completion (max 10 min)
            Map<String, Object> result = pollUntilComplete(config, evalRunId);
            if (result == null) {
                System.err.println("Eval run timed out or failed");
                System.exit(2);
                return;
            }

            // 3. Check score against threshold
            double avgScore = parseDouble(result.get("avgScore"), 0.0);
            double passRate = parseDouble(result.get("passRate"), 0.0);
            long passed = parseLong(result.get("passed"), 0);
            long totalCases = parseLong(result.get("totalCases"), 0);

            System.out.printf("Eval complete: avgScore=%.3f, passRate=%.1f%% (%d/%d passed)%n",
                avgScore, passRate * 100, passed, totalCases);

            if (avgScore >= config.threshold && passRate >= config.threshold) {
                System.out.println("PASS — score meets threshold");
                System.exit(0);
            } else {
                System.out.println("FAIL — score below threshold");
                System.exit(1);
            }
        } catch (Exception e) {
            if (isConnectionError(e)) {
                System.err.println("Chorus API unreachable: " + e.getMessage());
                System.exit(2);
            } else {
                System.err.println("Unexpected error: " + e.getMessage());
                e.printStackTrace();
                System.exit(2);
            }
        }
    }

    private static String submitEvalRun(Config config) throws Exception {
        String body = MAPPER.writeValueAsString(Map.of(
            "datasetId", config.datasetId,
            "agentConfig", config.agentConfig,
            "scorerConfig", Map.of("scorers", config.scorers, "threshold", config.threshold),
            "parallelism", config.parallelism,
            "minRuns", config.minRuns
        ));

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(config.apiUrl + "/api/v1/eval/runs"))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + config.token)
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .timeout(Duration.ofSeconds(30))
            .build();

        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            Map<?, ?> result = MAPPER.readValue(response.body(), Map.class);
            Object id = result.get("evalRunId");
            return id != null ? id.toString() : null;
        }
        return null;
    }

    private static Map<String, Object> pollUntilComplete(Config config, String evalRunId) throws Exception {
        long deadline = System.currentTimeMillis() + config.timeoutMinutes * 60_000L;
        while (System.currentTimeMillis() < deadline) {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(config.apiUrl + "/api/v1/eval/runs/" + evalRunId))
                .header("Authorization", "Bearer " + config.token)
                .GET()
                .timeout(Duration.ofSeconds(30))
                .build();

            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                Map<?, ?> result = MAPPER.readValue(response.body(), Map.class);
                String status = String.valueOf(result.get("status"));
                if ("COMPLETED".equals(status)) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> summary = (Map<String, Object>) result.get("summaryMetrics");
                    return summary;
                }
                if ("FAILED".equals(status) || "CANCELLED".equals(status)) {
                    return null;
                }
            }
            Thread.sleep(5_000);
        }
        return null;
    }

    private static Config parseArgs(String[] args) {
        if (args.length == 0) return null;
        Config config = new Config();
        for (String arg : args) {
            if (arg.startsWith("--api-url=")) config.apiUrl = arg.substring("--api-url=".length());
            else if (arg.startsWith("--dataset-id=")) config.datasetId = arg.substring("--dataset-id=".length());
            else if (arg.startsWith("--threshold=")) config.threshold = Double.parseDouble(arg.substring("--threshold=".length()));
            else if (arg.startsWith("--token=")) config.token = arg.substring("--token=".length());
            else if (arg.startsWith("--timeout-minutes=")) config.timeoutMinutes = Integer.parseInt(arg.substring("--timeout-minutes=".length()));
            else if (arg.startsWith("--parallelism=")) config.parallelism = Integer.parseInt(arg.substring("--parallelism=".length()));
            else if (arg.startsWith("--min-runs=")) config.minRuns = Integer.parseInt(arg.substring("--min-runs=".length()));
        }
        if (config.apiUrl == null || config.datasetId == null || config.token == null) {
            return null;
        }
        return config;
    }

    private static void printUsage() {
        System.out.println("Chorus Eval Gate CLI");
        System.out.println();
        System.out.println("  --api-url=URL         Chorus API base URL");
        System.out.println("  --dataset-id=ID       Dataset to evaluate against");
        System.out.println("  --threshold=N         Minimum passing score (default: 0.7)");
        System.out.println("  --token=TOKEN         API bearer token");
        System.out.println("  --timeout-minutes=N   Max wait time (default: 10)");
        System.out.println("  --parallelism=N       Eval parallelism (default: 4)");
        System.out.println("  --min-runs=N          Minimum runs per case (default: 3)");
        System.out.println();
        System.out.println("Exit codes: 0=pass, 1=fail, 2=unreachable");
    }

    private static double parseDouble(Object value, double defaultValue) {
        if (value == null) return defaultValue;
        if (value instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(value.toString()); } catch (NumberFormatException e) { return defaultValue; }
    }

    private static long parseLong(Object value, long defaultValue) {
        if (value == null) return defaultValue;
        if (value instanceof Number n) return n.longValue();
        try { return Long.parseLong(value.toString()); } catch (NumberFormatException e) { return defaultValue; }
    }

    private static boolean isConnectionError(Throwable e) {
        return e instanceof java.net.ConnectException
            || e instanceof java.net.http.HttpTimeoutException
            || e instanceof java.io.IOException
            || (e.getCause() != null && isConnectionError(e.getCause()));
    }

    private static class Config {
        String apiUrl;
        String datasetId;
        double threshold = 0.7;
        String token;
        int timeoutMinutes = 10;
        int parallelism = 4;
        int minRuns = 3;
        String[] scorers = new String[]{"exact_match"};
        Map<String, Object> agentConfig = Map.of();
    }
}
