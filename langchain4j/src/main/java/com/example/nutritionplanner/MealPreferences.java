package com.example.nutritionplanner;

import dev.langchain4j.agentic.declarative.TypedKey;
import dev.langchain4j.model.output.structured.Description;

public record MealPreferences(
        @Description("e.g. Mediterranean, Asian, Mexican") String cuisineStyle,
        @Description("e.g. 30 minutes") String maxCookingTime) implements TypedKey<MealPreferences> {

    public MealPreferences() {
        this("", "");
    }
}
