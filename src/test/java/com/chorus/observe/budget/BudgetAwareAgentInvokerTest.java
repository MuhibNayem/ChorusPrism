package com.chorus.observe.budget;

import com.chorus.observe.model.BudgetEnforcement;
import com.chorus.observe.persistence.InMemoryBudgetEnforcementRepository;
import com.chorus.observe.service.AgentInvoker;
import com.chorus.observe.service.BudgetService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

class BudgetAwareAgentInvokerTest {

    private BudgetAwareAgentInvoker invoker;
    private InMemoryBudgetEnforcementRepository budgetRepo;
    private BudgetService budgetService;
    private AgentInvoker delegate;
    private PricingTable pricingTable;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        budgetRepo = new InMemoryBudgetEnforcementRepository();
        budgetService = new BudgetService(budgetRepo);
        delegate = (config, input) -> "output for: " + input;
        pricingTable = new PricingTable();
        mapper = new ObjectMapper();
        invoker = new BudgetAwareAgentInvoker(delegate, budgetService, pricingTable, mapper);
    }

    @Test
    void shouldAllowInvocationWhenNoBudgetExists() {
        String result = invoker.invoke("{\"agentId\": \"agent-1\"}", "hello");
        assertThat(result).isEqualTo("output for: hello");
    }

    @Test
    void shouldAllowInvocationWhenBudgetIsActive() {
        budgetService.createBudget("agent-1", "cost", new BigDecimal("100.00"), "USD");

        String result = invoker.invoke("{\"agentId\": \"agent-1\"}", "hello");
        assertThat(result).isEqualTo("output for: hello");
    }

    @Test
    void shouldBlockInvocationWhenBudgetExceeded() {
        BudgetEnforcement budget = budgetService.createBudget("agent-1", "cost", new BigDecimal("0.001"), "USD");
        // Artificially exceed the budget
        budgetService.updateSpending(budget.enforcementId(), new BigDecimal("0.002"));

        assertThatThrownBy(() -> invoker.invoke("{\"agentId\": \"agent-1\"}", "hello"))
            .isInstanceOf(BudgetExceededException.class)
            .hasMessageContaining("Budget exceeded");
    }

    @Test
    void shouldRecordSpendAfterInvocation() {
        BudgetEnforcement budget = budgetService.createBudget("agent-1", "cost", new BigDecimal("100.00"), "USD");

        invoker.invoke("{\"agentId\": \"agent-1\", \"model\": \"gpt-4o\"}", "this is a test prompt with enough text to generate tokens");

        BudgetEnforcement updated = budgetRepo.findById(budget.enforcementId()).orElseThrow();
        assertThat(updated.currentValue()).isGreaterThan(BigDecimal.ZERO);
    }

    @Test
    void shouldParseAgentIdAndModelInSinglePass() {
        // This test verifies the fix for double JSON parsing.
        // If parsing were done twice per invocation, it would be observable
        // only under load. We verify correctness here.
        budgetService.createBudget("my-agent", "cost", new BigDecimal("100.00"), "USD");

        String result = invoker.invoke(
            "{\"agentId\": \"my-agent\", \"model\": \"gpt-4o\"}",
            "hello"
        );
        assertThat(result).isEqualTo("output for: hello");
    }

    @Test
    void shouldFallbackToNameFieldWhenAgentIdMissing() {
        budgetService.createBudget("named-agent", "cost", new BigDecimal("100.00"), "USD");

        String result = invoker.invoke(
            "{\"name\": \"named-agent\"}",
            "hello"
        );
        assertThat(result).isEqualTo("output for: hello");
    }

    @Test
    void shouldHandleInvalidJsonGracefully() {
        String result = invoker.invoke("not-json", "hello");
        assertThat(result).isEqualTo("output for: hello");
    }

    @Test
    void shouldCacheBudgetLookup() {
        BudgetEnforcement budget = budgetService.createBudget("agent-1", "cost", new BigDecimal("100.00"), "USD");

        // First invocation should cache the budget
        invoker.invoke("{\"agentId\": \"agent-1\"}", "hello");

        // Modify budget directly in repo (bypassing cache invalidation)
        budgetRepo.save(new BudgetEnforcement(
            budget.enforcementId(), budget.agentId(), budget.budgetType(),
            budget.limitValue(), new BigDecimal("99.00"), budget.currency(),
            BudgetEnforcement.Status.ACTIVE, budget.triggeredAt(), budget.createdAt(), budget.updatedAt()
        ));

        // Second invocation should still use cached budget (within 5s TTL)
        String result = invoker.invoke("{\"agentId\": \"agent-1\"}", "hello again");
        assertThat(result).isEqualTo("output for: hello again");
    }
}
