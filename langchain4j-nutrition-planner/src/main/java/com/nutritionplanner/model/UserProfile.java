package com.nutritionplanner.model;

import java.util.List;

public record UserProfile(String name, List<String> dietaryRestrictions, List<String> healthGoals,
                           int dailyCalorieTarget, List<String> allergies, List<String> dislikedIngredients) {
}
