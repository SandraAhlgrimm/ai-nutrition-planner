package com.example.nutritionplanner;

final class NutritionPlanValidationException extends RuntimeException {

    private final int audits;
    private final String feedback;

    NutritionPlanValidationException(int audits, String feedback) {
        super("No valid nutrition plan after " + audits + " audited candidates");
        this.audits = audits;
        this.feedback = feedback;
    }

    int audits() {
        return audits;
    }

    String feedback() {
        return feedback;
    }
}
