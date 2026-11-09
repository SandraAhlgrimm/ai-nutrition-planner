package com.example.nutritionplanner;

record NutritionInfo(Integer calories, Double proteinGrams, Double carbGrams, Double fatGrams, Integer sodiumMg) {

    static final NutritionInfo ZERO = new NutritionInfo(0, 0.0, 0.0, 0.0, 0);

    NutritionInfo plus(NutritionInfo other) {
        return new NutritionInfo(calories + other.calories, proteinGrams + other.proteinGrams,
                carbGrams + other.carbGrams, fatGrams + other.fatGrams, sodiumMg + other.sodiumMg);
    }
}
