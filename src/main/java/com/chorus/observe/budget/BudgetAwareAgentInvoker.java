package com.chorus.observe.budget;

import com.chorus.observe.model.BudgetEnforcement;
import com.chorus.observe.service.AgentInvoker;
import com.chorus.observe.service.BudgetService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Budget-enforcing decorator for {@link AgentInvoker}.
 * <p>
 * Before each invocation:
 * <ol>
 *   <li>Extracts the agent/model identifier from {@code agentConfigJson}</li>
 *   <li>Looks up the active budget for that agent (cached with 5-second TTL)</li>
 *   <li>If budget status is {@code EXCEEDED}, throws {@link BudgetExceededException}</li>
 * </ol>
 * After each invocation:
 * <ol>
 *   <li>Estimates cost from input/output tokens using {@link PricingTable}</li>
 *   <li>Records spend via {@link BudgetService} (atomic UPDATE, no lost updates)</li>
 * </ol>
 * <p>
 * This is transparent to callers — the interface is identical to {@link AgentInvoker}.
 */
public final class BudgetAwareAgentInvoker implements AgentInvoker {

    private static final Logger LOG = LoggerFactory.getLogger(BudgetAwareAgentInvoker.class);
    private static final long CACHE_TTL_MILLIS = 5_000;

    private final AgentInvoker delegate;
    private final BudgetService budgetService;
    private final PricingTable pricingTable;
    private final ObjectMapper mapper;
    private final ConcurrentHashMap<String, CacheEntry> budgetCache = new ConcurrentHashMap<>();

    public BudgetAwareAgentInvoker(
            @NonNull AgentInvoker delegate,
            @NonNull BudgetService budgetService,
            @NonNull PricingTable pricingTable,
            @NonNull ObjectMapper mapper
    ) {
        this.delegate = Objects.requireNonNull(delegate);
        this.budgetService = Objects.requireNonNull(budgetService);
        this.pricingTable = Objects.requireNonNull(pricingTable);
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public @NonNull String invoke(@NonNull String agentConfigJson, @NonNull String input) {
        AgentConfigSnapshot config = parseConfig(agentConfigJson);
        String agentId = config.agentId();
        String model = config.model();

        Optional<BudgetEnforcement> budgetOpt = findActiveBudgetCached(agentId);
        if (budgetOpt.isPresent()) {
            BudgetEnforcement budget = budgetOpt.get();
            if (budget.status() == BudgetEnforcement.Status.EXCEEDED) {
                LOG.warn("Agent {} blocked: budget {} exceeded ({} / {})",
                    agentId, budget.enforcementId(), budget.currentValue(), budget.limitValue());
                throw new BudgetExceededException(
                    "Budget exceeded for agent " + agentId + ": " + budget.currentValue() + " / " + budget.limitValue());
            }
        }

        String output = delegate.invoke(agentConfigJson, input);

        if (budgetOpt.isPresent()) {
            BudgetEnforcement budget = budgetOpt.get();
            BigDecimal estimatedCost = pricingTable.estimateCost(model, input, output);
            if (estimatedCost.compareTo(BigDecimal.ZERO) > 0) {
                BudgetEnforcement updated = budgetService.updateSpending(budget.enforcementId(), estimatedCost);
                LOG.debug("Recorded spend for agent {}: {} (total: {} / {})",
                    agentId, estimatedCost, updated.currentValue(), updated.limitValue());
                // Invalidate cache so next lookup sees updated state
                budgetCache.remove(agentId);
            }
        }

        return output;
    }

    private @NonNull Optional<BudgetEnforcement> findActiveBudgetCached(@NonNull String agentId) {
        CacheEntry entry = budgetCache.get(agentId);
        if (entry != null && !entry.isExpired()) {
            return entry.budget();
        }
        Optional<BudgetEnforcement> budget = budgetService.listBudgetsByAgent(agentId).stream()
            .filter(b -> b.status() == BudgetEnforcement.Status.ACTIVE
                || b.status() == BudgetEnforcement.Status.WARNING
                || b.status() == BudgetEnforcement.Status.EXCEEDED)
            .findFirst();
        budgetCache.put(agentId, new CacheEntry(budget, System.currentTimeMillis()));
        return budget;
    }

    private record AgentConfigSnapshot(@NonNull String agentId, @Nullable String model) {}

    private @NonNull AgentConfigSnapshot parseConfig(@NonNull String agentConfigJson) {
        try {
            Map<String, Object> config = mapper.readValue(agentConfigJson, new com.fasterxml.jackson.core.type.TypeReference<>() {});
            Object agentId = config.get("agentId");
            if (agentId == null) {
                Object name = config.get("name");
                agentId = name != null ? name : "unknown";
            }
            Object model = config.get("model");
            return new AgentConfigSnapshot(agentId.toString(), model != null ? model.toString() : null);
        } catch (Exception e) {
            return new AgentConfigSnapshot("unknown", null);
        }
    }

    private record CacheEntry(Optional<BudgetEnforcement> budget, long cachedAtMillis) {
        boolean isExpired() {
            return System.currentTimeMillis() - cachedAtMillis > CACHE_TTL_MILLIS;
        }
    }
}
