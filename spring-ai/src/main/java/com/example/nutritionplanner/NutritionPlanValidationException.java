package com.example.nutritionplanner;

final class NutritionPlanValidationException extends RuntimeException {

    private final int audits;
    private final String feedback;
    private final Object candidate;

    NutritionPlanValidationException(int audits, String feedback, Object candidate) {
        super("No valid nutrition plan after " + audits + " audited candidates");
        this.audits = audits;
        this.feedback = feedback;
        this.candidate = java.util.Objects.requireNonNull(candidate);
    }

    int audits() {
        return audits;
    }

    Object candidate() {
        return candidate;
    }

    String feedback() {
        return feedback;
    }
}
