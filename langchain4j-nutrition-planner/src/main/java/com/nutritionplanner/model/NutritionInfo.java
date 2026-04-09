package com.nutritionplanner.model;

import java.util.List;

public record NutritionInfo(int calories, double proteinGrams, double carbGrams, double fatGrams, int sodiumMg) {

    public NutritionInfo(List<Recipe> recipes) {
        this(
            recipes.stream().filter(r -> r.nutrition() != null).mapToInt(r -> r.nutrition().calories()).sum(),
            recipes.stream().filter(r -> r.nutrition() != null).mapToDouble(r -> r.nutrition().proteinGrams()).sum(),
            recipes.stream().filter(r -> r.nutrition() != null).mapToDouble(r -> r.nutrition().carbGrams()).sum(),
            recipes.stream().filter(r -> r.nutrition() != null).mapToDouble(r -> r.nutrition().fatGrams()).sum(),
            recipes.stream().filter(r -> r.nutrition() != null).mapToInt(r -> r.nutrition().sodiumMg()).sum()
        );
    }
}
