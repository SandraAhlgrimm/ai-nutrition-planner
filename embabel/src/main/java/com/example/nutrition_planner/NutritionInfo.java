package com.example.nutrition_planner;

import java.util.List;

record NutritionInfo (int calories, double proteinGrams, double carbGrams, double fatGrams, int sodiumMg) {
        NutritionInfo(List<Recipe> recipes) {
            this(recipes.stream().mapToInt(r -> r.nutrition().calories()).sum(),
                    recipes.stream().mapToDouble(r -> r.nutrition().proteinGrams()).sum(),
                    recipes.stream().mapToDouble(r -> r.nutrition().carbGrams()).sum(),
                    recipes.stream().mapToDouble(r -> r.nutrition().fatGrams()).sum(),
                    recipes.stream().mapToInt(r -> r.nutrition().sodiumMg()).sum()
            );
        }
}
