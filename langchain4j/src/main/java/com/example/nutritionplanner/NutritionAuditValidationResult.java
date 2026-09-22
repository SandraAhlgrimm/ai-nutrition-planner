package com.example.nutritionplanner;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import dev.langchain4j.agentic.declarative.TypedKey;

import java.time.DayOfWeek;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public record NutritionAuditValidationResult(@JsonProperty(required = true) @JsonSetter(nulls = Nulls.FAIL) boolean allPassed,
                                            List<NutritionAuditRecipeViolation> violations,
                                            String consolidatedFeedback) implements TypedKey<NutritionAuditValidationResult> {

    public NutritionAuditValidationResult {
        violations = List.copyOf(violations);
        Objects.requireNonNull(consolidatedFeedback, "Audit feedback is required");
        if (allPassed && !violations.isEmpty()) {
            throw new IllegalArgumentException("A passing audit cannot contain violations");
        }
    }

    public record NutritionAuditRecipeViolation(DayOfWeek dayOfWeek, String recipeName,
                                                String explanation, String suggestedFix) {}

    public NutritionAuditValidationResult() {
        this(false, Collections.emptyList(), "");
    }
}