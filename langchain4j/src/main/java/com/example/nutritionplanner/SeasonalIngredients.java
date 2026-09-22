package com.example.nutritionplanner;

import dev.langchain4j.agentic.declarative.TypedKey;

import java.util.Collections;
import java.util.List;

public record SeasonalIngredients(List<Recipe.Ingredient> items) implements TypedKey<SeasonalIngredients> {
    public SeasonalIngredients {
        items = List.copyOf(items);
    }

    public SeasonalIngredients() { this(Collections.emptyList()); }
}
