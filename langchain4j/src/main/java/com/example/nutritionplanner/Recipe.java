package com.example.nutritionplanner;

import org.jspecify.annotations.Nullable;

import java.util.List;

public record Recipe(String name, List<Ingredient> ingredients, @Nullable NutritionInfo nutrition, String instructions,
              int prepTimeMinutes) {

    public record Ingredient(String name, String quantity, String unit) {}
}
