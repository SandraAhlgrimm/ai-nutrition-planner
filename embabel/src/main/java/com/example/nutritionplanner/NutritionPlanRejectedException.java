package com.example.nutritionplanner;

import com.embabel.agent.core.NonRetryable;

final class NutritionPlanRejectedException extends RuntimeException implements NonRetryable {

    private final int audits;
    private final int revisions;
    private final NutritionAuditValidationResult validationResult;
    private final WeeklyPlan candidate;

    NutritionPlanRejectedException(RevisionBudget budget, NutritionAuditValidationResult validationResult,
                                   WeeklyPlan candidate) {
        super("No compliant nutrition plan was found after %d audits and %d revisions. %s"
                .formatted(budget.auditNumber(), budget.revisionsUsed(), validationResult.consolidatedFeedback()));
        this.audits = budget.auditNumber();
        this.revisions = budget.revisionsUsed();
        this.validationResult = validationResult;
        this.candidate = java.util.Objects.requireNonNull(candidate);
    }

    int audits() {
        return audits;
    }

    WeeklyPlan candidate() {
        return candidate;
    }

    int revisions() {
        return revisions;
    }

    NutritionAuditValidationResult validationResult() {
        return validationResult;
    }
}
