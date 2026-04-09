package com.nutritionplanner.model;

import java.util.List;

public record Recipe(String name, List<Ingredient> ingredients, NutritionInfo nutrition,
                     String instructions, int prepTimeMinutes) {

    public record Ingredient(String name, String quantity, String unit) {}
}
