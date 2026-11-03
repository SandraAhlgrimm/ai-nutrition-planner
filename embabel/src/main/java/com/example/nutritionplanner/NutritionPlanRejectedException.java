package com.example.nutritionplanner;

import com.embabel.agent.core.NonRetryable;

final class NutritionPlanRejectedException extends RuntimeException implements NonRetryable {

    private final int audits;
    private final int revisions;
    private final NutritionAuditValidationResult validationResult;

    NutritionPlanRejectedException(RevisionBudget budget, NutritionAuditValidationResult validationResult) {
        super("No compliant nutrition plan was found after %d audits and %d revisions. %s"
                .formatted(budget.auditNumber(), budget.revisionsUsed(), validationResult.consolidatedFeedback()));
        this.audits = budget.auditNumber();
        this.revisions = budget.revisionsUsed();
        this.validationResult = validationResult;
    }

    int audits() {
        return audits;
    }

    int revisions() {
        return revisions;
    }

    NutritionAuditValidationResult validationResult() {
        return validationResult;
    }
}
