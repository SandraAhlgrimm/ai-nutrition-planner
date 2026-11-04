package com.example.nutritionplanner;

public class PlanValidationException extends RuntimeException {

    private final NutritionAuditValidationResult audit;
    private final WeeklyPlan candidate;

    public PlanValidationException(NutritionAuditValidationResult audit, WeeklyPlan candidate) {
        super("The meal plan failed validation after the initial plan and three revisions. "
                + "No unvalidated plan was returned.");
        this.audit = audit;
        this.candidate = java.util.Objects.requireNonNull(candidate);
    }

    public WeeklyPlan candidate() {
        return candidate;
    }

    public NutritionAuditValidationResult audit() {
        return audit;
    }
}
