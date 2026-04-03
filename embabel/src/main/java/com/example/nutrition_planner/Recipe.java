package com.example.nutrition_planner;

import java.util.List;

record Recipe(String name, List<Ingredient> ingredients, NutritionInfo nutrition, String instructions,
              int prepTimeMinutes) {

    record Ingredient(String name, String quantity, String unit) {}
}
