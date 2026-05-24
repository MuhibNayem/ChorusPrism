package com.chorus.observe.budget;

import com.chorus.observe.config.ChorusObserveProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Service for dynamically updating model prices from the public LiteLLM catalog.
 * Resilient, asynchronous, and thread-safe.
 */
public class DynamicPricingService {

    private static final Logger LOG = LoggerFactory.getLogger(DynamicPricingService.class);

    private final ChorusObserveProperties properties;
    private final PricingTable pricingTable;
    private final ObjectMapper mapper;
    private final HttpClient httpClient;

    public DynamicPricingService(
            @NonNull ChorusObserveProperties properties,
            @NonNull PricingTable pricingTable,
            @NonNull ObjectMapper mapper
    ) {
        this.properties = Objects.requireNonNull(properties);
        this.pricingTable = Objects.requireNonNull(pricingTable);
        this.mapper = Objects.requireNonNull(mapper);
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    }

    @PostConstruct
    public void init() {
        if (!properties.getPricing().isDynamicEnabled()) {
            LOG.info("Dynamic pricing is disabled; using default static pricing tables.");
            return;
        }
        // Run startup dynamic fetch in a virtual thread to avoid blocking application startup
        Thread.startVirtualThread(this::fetchAndRefreshPrices);
    }

    /**
     * Refreshes model prices from the remote pricing catalog.
     * Scheduled to execute once every 24 hours.
     */
    @Scheduled(fixedDelay = 24, timeUnit = TimeUnit.HOURS)
    public void scheduledRefresh() {
        if (properties.getPricing().isDynamicEnabled()) {
            LOG.info("Triggering scheduled model prices refresh.");
            fetchAndRefreshPrices();
        }
    }

    /**
     * Fetch, parse, and merge remote pricing catalog.
     */
    public synchronized void fetchAndRefreshPrices() {
        String url = properties.getPricing().getUrl();
        try {
            LOG.info("Fetching model prices from: {}", url);
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                LOG.warn("Failed to fetch model prices, server returned status {}. Retaining current prices.", response.statusCode());
                return;
            }

            parseAndRegisterPrices(response.body());
        } catch (Exception e) {
            LOG.warn("Failed to fetch dynamic model prices: {}. Retaining current prices.", e.getMessage());
        }
    }

    private void parseAndRegisterPrices(@NonNull String json) {
        try {
            JsonNode root = mapper.readTree(json);
            Map<String, PricingTable.ModelPricing> exact = new HashMap<>();
            Map<String, PricingTable.ModelPricing> prefix = new HashMap<>();

            Iterator<Map.Entry<String, JsonNode>> fields = root.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                String modelName = field.getKey().toLowerCase().trim();
                JsonNode modelInfo = field.getValue();

                if (modelInfo.has("input_cost_per_token") && modelInfo.has("output_cost_per_token")) {
                    double inputCost = modelInfo.get("input_cost_per_token").asDouble();
                    double outputCost = modelInfo.get("output_cost_per_token").asDouble();

                    // Convert cost per token to cost per 1k tokens
                    BigDecimal inputPer1k = BigDecimal.valueOf(inputCost).multiply(BigDecimal.valueOf(1000));
                    BigDecimal outputPer1k = BigDecimal.valueOf(outputCost).multiply(BigDecimal.valueOf(1000));

                    PricingTable.ModelPricing pricing = new PricingTable.ModelPricing(inputPer1k, outputPer1k);
                    exact.put(modelName, pricing);

                    // Add dynamic prefix mapping for generic name segments (e.g. gpt-4o, claude-3-5-sonnet)
                    if (modelName.contains("-") && !modelName.contains("preview")) {
                        String prefixName = modelName.substring(0, modelName.lastIndexOf('-'));
                        prefix.putIfAbsent(prefixName, pricing);
                    }
                }
            }

            if (!exact.isEmpty()) {
                pricingTable.registerPrices(exact, prefix);
                LOG.info("Successfully loaded and merged {} dynamic model pricing records.", exact.size());
            }
        } catch (Exception e) {
            LOG.warn("Failed to parse remote model prices JSON catalog: {}", e.getMessage());
        }
    }
}
