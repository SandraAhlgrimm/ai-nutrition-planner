package com.example.nutritionplanner;

import java.util.List;

record NutritionInfo (Integer calories, Double proteinGrams, Double carbGrams, Double fatGrams, Integer sodiumMg) {
        NutritionInfo(List<Recipe> recipes) {
            this(recipes.stream().filter(r -> r.nutrition() != null).mapToInt(r -> r.nutrition().calories()).sum(),
                    recipes.stream().filter(r -> r.nutrition() != null).mapToDouble(r -> r.nutrition().proteinGrams()).sum(),
                    recipes.stream().filter(r -> r.nutrition() != null).mapToDouble(r -> r.nutrition().carbGrams()).sum(),
                    recipes.stream().filter(r -> r.nutrition() != null).mapToDouble(r -> r.nutrition().fatGrams()).sum(),
                    recipes.stream().filter(r -> r.nutrition() != null).mapToInt(r -> r.nutrition().sodiumMg()).sum()
            );
        }
}
