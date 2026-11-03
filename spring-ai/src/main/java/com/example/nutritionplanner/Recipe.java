package com.example.nutritionplanner;

import java.util.List;
import java.util.Objects;

public record Recipe(String name, List<Ingredient> ingredients, NutritionInfo nutrition, String instructions,
                     Integer prepTimeMinutes) {

    public Recipe {
        if (name == null || name.isBlank() || instructions == null || instructions.isBlank()
                || prepTimeMinutes == null || prepTimeMinutes < 0) {
            throw new IllegalArgumentException("Recipe name, instructions and preparation time are required");
        }
        ingredients = List.copyOf(ingredients);
        Objects.requireNonNull(nutrition, "Recipe nutrition is required");
    }

    public record Ingredient(String name, String quantity, String unit) {}
}
