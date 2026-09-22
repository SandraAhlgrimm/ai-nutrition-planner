package com.example.nutritionplanner;

import java.util.List;

record NutritionInfo (Integer calories, Double proteinGrams, Double carbGrams, Double fatGrams, Integer sodiumMg) {
        NutritionInfo {
            if (calories == null || proteinGrams == null || carbGrams == null || fatGrams == null || sodiumMg == null
                    || calories < 0 || proteinGrams < 0 || carbGrams < 0 || fatGrams < 0 || sodiumMg < 0
                    || !Double.isFinite(proteinGrams) || !Double.isFinite(carbGrams) || !Double.isFinite(fatGrams)) {
                throw new IllegalArgumentException("Complete, finite, non-negative nutrition information is required");
            }
        }

        NutritionInfo(List<Recipe> recipes) {
            this(recipes.stream().mapToInt(r -> r.nutrition().calories()).sum(),
                    recipes.stream().mapToDouble(r -> r.nutrition().proteinGrams()).sum(),
                    recipes.stream().mapToDouble(r -> r.nutrition().carbGrams()).sum(),
                    recipes.stream().mapToDouble(r -> r.nutrition().fatGrams()).sum(),
                    recipes.stream().mapToInt(r -> r.nutrition().sodiumMg()).sum()
            );
        }
}
