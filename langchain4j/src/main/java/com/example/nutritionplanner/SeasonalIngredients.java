package com.example.nutritionplanner;

import dev.langchain4j.agentic.declarative.TypedKey;

import java.util.Collections;
import java.util.List;

public record SeasonalIngredients(List<String> ingredients) implements TypedKey<SeasonalIngredients> {
    public SeasonalIngredients() { this(Collections.emptyList()); }
}
