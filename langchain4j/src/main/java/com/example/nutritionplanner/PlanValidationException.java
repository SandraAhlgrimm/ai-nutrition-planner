package com.example.nutritionplanner;

public class PlanValidationException extends RuntimeException {

    private final NutritionAuditValidationResult audit;

    public PlanValidationException(NutritionAuditValidationResult audit) {
        super("The meal plan failed validation after the initial plan and three revisions. "
                + "No unvalidated plan was returned.");
        this.audit = audit;
    }

    public NutritionAuditValidationResult audit() {
        return audit;
    }
}
