package com.chorus.observe.service;

import com.chorus.observe.model.BudgetEnforcement;
import com.chorus.observe.model.PagedResult;
import com.chorus.observe.persistence.BudgetEnforcementRepository;
import org.jspecify.annotations.NonNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for budget enforcement tracking.
 */
public class BudgetService {

    private final BudgetEnforcementRepository budgetEnforcementRepository;

    public BudgetService(@NonNull BudgetEnforcementRepository budgetEnforcementRepository) {
        this.budgetEnforcementRepository = Objects.requireNonNull(budgetEnforcementRepository);
    }

    public @NonNull BudgetEnforcement createBudget(@NonNull String agentId, @NonNull String budgetType, @NonNull BigDecimal limitValue, @NonNull String currency) {
        String enforcementId = "budget-" + UUID.randomUUID().toString().substring(0, 8);
        BudgetEnforcement enforcement = new BudgetEnforcement(
            enforcementId, agentId, budgetType, limitValue, BigDecimal.ZERO,
            currency, BudgetEnforcement.Status.ACTIVE, null, Instant.now(), Instant.now()
        );
        budgetEnforcementRepository.save(enforcement);
        return enforcement;
    }

    public @NonNull Optional<BudgetEnforcement> getBudget(@NonNull String enforcementId) {
        return budgetEnforcementRepository.findById(enforcementId);
    }

    public @NonNull List<BudgetEnforcement> listBudgets() {
        return budgetEnforcementRepository.findAll();
    }

    public @NonNull PagedResult<BudgetEnforcement> listBudgets(int page, int size) {
        int offset = page * size;
        return new PagedResult<>(budgetEnforcementRepository.findAll(size, offset), budgetEnforcementRepository.count(), page, size);
    }

    public @NonNull List<BudgetEnforcement> listBudgetsByAgent(@NonNull String agentId) {
        return budgetEnforcementRepository.findByAgentId(agentId);
    }

    public @NonNull PagedResult<BudgetEnforcement> listBudgetsByAgent(@NonNull String agentId, int page, int size) {
        int offset = page * size;
        return new PagedResult<>(budgetEnforcementRepository.findByAgentId(agentId, size, offset), budgetEnforcementRepository.countByAgentId(agentId), page, size);
    }

    public @NonNull List<BudgetEnforcement> listActiveBudgets() {
        return budgetEnforcementRepository.findActive();
    }

    public @NonNull PagedResult<BudgetEnforcement> listActiveBudgets(int page, int size) {
        int offset = page * size;
        return new PagedResult<>(budgetEnforcementRepository.findActive(size, offset), budgetEnforcementRepository.countActive(), page, size);
    }

    public @NonNull BudgetEnforcement updateSpending(@NonNull String enforcementId, @NonNull BigDecimal delta) {
        int updated = budgetEnforcementRepository.addSpendingAtomic(enforcementId, delta);
        if (updated == 0) {
            throw new IllegalArgumentException("Budget not found or concurrently modified: " + enforcementId);
        }
        Optional<BudgetEnforcement> opt = budgetEnforcementRepository.findById(enforcementId);
        return opt.orElseThrow(() -> new IllegalStateException("Budget disappeared after atomic update: " + enforcementId));
    }

    public void pauseBudget(@NonNull String enforcementId) {
        Optional<BudgetEnforcement> opt = budgetEnforcementRepository.findById(enforcementId);
        if (opt.isEmpty()) return;
        BudgetEnforcement current = opt.get();
        budgetEnforcementRepository.save(new BudgetEnforcement(
            current.enforcementId(), current.agentId(), current.budgetType(),
            current.limitValue(), current.currentValue(), current.currency(),
            BudgetEnforcement.Status.PAUSED, current.triggeredAt(), current.createdAt(), Instant.now()
        ));
    }

    public void resumeBudget(@NonNull String enforcementId) {
        Optional<BudgetEnforcement> opt = budgetEnforcementRepository.findById(enforcementId);
        if (opt.isEmpty()) return;
        BudgetEnforcement current = opt.get();
        BudgetEnforcement.Status status = current.currentValue().compareTo(current.limitValue()) >= 0 ? BudgetEnforcement.Status.EXCEEDED : BudgetEnforcement.Status.ACTIVE;
        budgetEnforcementRepository.save(new BudgetEnforcement(
            current.enforcementId(), current.agentId(), current.budgetType(),
            current.limitValue(), current.currentValue(), current.currency(),
            status, current.triggeredAt(), current.createdAt(), Instant.now()
        ));
    }
}
