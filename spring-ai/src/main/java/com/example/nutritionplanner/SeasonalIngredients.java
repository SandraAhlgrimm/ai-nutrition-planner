package com.example.nutritionplanner;

import java.util.List;

record SeasonalIngredients(List<String> ingredients) {
    SeasonalIngredients {
        ingredients = List.copyOf(ingredients);
        if (ingredients.isEmpty() || ingredients.stream().anyMatch(String::isBlank)) {
            throw new IllegalArgumentException("Seasonal ingredients must not be empty");
        }
    }
}
