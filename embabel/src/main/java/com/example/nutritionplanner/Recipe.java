package com.example.nutritionplanner;

import java.util.List;

record Recipe(String name, List<Ingredient> ingredients, NutritionInfo nutrition, String instructions,
              int prepTimeMinutes) {

    record Ingredient(String name, String quantity, String unit) {}
}
