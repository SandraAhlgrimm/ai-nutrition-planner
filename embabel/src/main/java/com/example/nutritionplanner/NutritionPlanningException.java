package com.example.nutritionplanner;

final class NutritionPlanningException extends RuntimeException {

    NutritionPlanningException(Throwable cause) {
        super("The nutrition planner could not complete the request. Check the model configuration and try again.", cause);
    }
}
