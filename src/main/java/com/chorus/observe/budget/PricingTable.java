package com.chorus.observe.budget;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Immutable pricing table for LLM cost estimation.
 * <p>
 * Prices are per 1K tokens in USD. Supports exact model names and prefix
 * matching (e.g., "gpt-4o" matches "gpt-4o-2024-08-06").
 * <p>
 * Default prices are included for OpenAI and Anthropic models. Custom prices
 * can be registered via {@link #withPrice(String, ModelPricing)}.
 */
public final class PricingTable {

    private final Map<String, ModelPricing> exactPrices;
    private final Map<String, ModelPricing> prefixPrices;

    public PricingTable() {
        this.exactPrices = new ConcurrentHashMap<>();
        this.prefixPrices = new ConcurrentHashMap<>();
        loadDefaults();
    }

    private PricingTable(Map<String, ModelPricing> exact, Map<String, ModelPricing> prefix) {
        this.exactPrices = exact;
        this.prefixPrices = prefix;
    }

    private void loadDefaults() {
        // OpenAI
        exactPrices.put("gpt-4o", new ModelPricing(new BigDecimal("0.00500"), new BigDecimal("0.01500")));
        exactPrices.put("gpt-4o-mini", new ModelPricing(new BigDecimal("0.00015"), new BigDecimal("0.00060")));
        exactPrices.put("gpt-4-turbo", new ModelPricing(new BigDecimal("0.01000"), new BigDecimal("0.03000")));
        exactPrices.put("gpt-4", new ModelPricing(new BigDecimal("0.03000"), new BigDecimal("0.06000")));
        exactPrices.put("gpt-3.5-turbo", new ModelPricing(new BigDecimal("0.00050"), new BigDecimal("0.00150")));

        // Anthropic
        exactPrices.put("claude-3-5-sonnet", new ModelPricing(new BigDecimal("0.00300"), new BigDecimal("0.01500")));
        exactPrices.put("claude-3-5-sonnet-20241022", new ModelPricing(new BigDecimal("0.00300"), new BigDecimal("0.01500")));
        exactPrices.put("claude-3-sonnet", new ModelPricing(new BigDecimal("0.00300"), new BigDecimal("0.01500")));
        exactPrices.put("claude-3-haiku", new ModelPricing(new BigDecimal("0.00025"), new BigDecimal("0.00125")));
        exactPrices.put("claude-3-opus", new ModelPricing(new BigDecimal("0.01500"), new BigDecimal("0.07500")));

        // Prefix fallbacks
        prefixPrices.put("gpt-4o", exactPrices.get("gpt-4o"));
        prefixPrices.put("gpt-4", exactPrices.get("gpt-4"));
        prefixPrices.put("gpt-3.5", exactPrices.get("gpt-3.5-turbo"));
        prefixPrices.put("claude-3-5-sonnet", exactPrices.get("claude-3-5-sonnet"));
        prefixPrices.put("claude-3-sonnet", exactPrices.get("claude-3-sonnet"));
        prefixPrices.put("claude-3-haiku", exactPrices.get("claude-3-haiku"));
        prefixPrices.put("claude-3-opus", exactPrices.get("claude-3-opus"));
    }

    public @NonNull PricingTable withPrice(@NonNull String model, @NonNull ModelPricing pricing) {
        Map<String, ModelPricing> newExact = new ConcurrentHashMap<>(exactPrices);
        newExact.put(model, pricing);
        return new PricingTable(newExact, prefixPrices);
    }

    /**
     * Atomically register new prices into the lookup map.
     */
    public void registerPrices(@NonNull Map<String, ModelPricing> newExact, @NonNull Map<String, ModelPricing> newPrefix) {
        this.exactPrices.putAll(newExact);
        this.prefixPrices.putAll(newPrefix);
    }

    public @Nullable ModelPricing lookup(@Nullable String model) {
        if (model == null || model.isBlank()) return null;
        String normalized = model.toLowerCase().trim();

        // Exact match
        ModelPricing exact = exactPrices.get(normalized);
        if (exact != null) return exact;

        // Prefix match
        for (Map.Entry<String, ModelPricing> entry : prefixPrices.entrySet()) {
            if (normalized.startsWith(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * Estimate token count from character count.
     * Uses a conservative heuristic: ~4 characters per token for English text.
     */
    public int estimateTokens(@NonNull String text) {
        return Math.max(1, text.length() / 4);
    }

    /**
     * Calculate estimated cost for a given model, input text, and output text.
     */
    public @NonNull BigDecimal estimateCost(@Nullable String model, @NonNull String input, @NonNull String output) {
        ModelPricing pricing = lookup(model);
        if (pricing == null) {
            return BigDecimal.ZERO;
        }
        int inputTokens = estimateTokens(input);
        int outputTokens = estimateTokens(output);
        return pricing.calculate(inputTokens, outputTokens);
    }

    public record ModelPricing(@NonNull BigDecimal inputPricePer1k, @NonNull BigDecimal outputPricePer1k) {
        public ModelPricing {
            Objects.requireNonNull(inputPricePer1k);
            Objects.requireNonNull(outputPricePer1k);
        }

        public @NonNull BigDecimal calculate(int inputTokens, int outputTokens) {
            BigDecimal inputCost = inputPricePer1k.multiply(BigDecimal.valueOf(inputTokens)).divide(BigDecimal.valueOf(1000));
            BigDecimal outputCost = outputPricePer1k.multiply(BigDecimal.valueOf(outputTokens)).divide(BigDecimal.valueOf(1000));
            return inputCost.add(outputCost);
        }
    }
}
