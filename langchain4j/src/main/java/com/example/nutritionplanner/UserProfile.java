package com.example.nutritionplanner;

import dev.langchain4j.agentic.declarative.TypedKey;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

import java.util.Collections;
import java.util.List;

public record UserProfile(String username, List<String> dietaryRestrictions, List<String> healthGoals, int dailyCalorieTarget,
        List<String> allergies, List<String> dislikedIngredients) implements TypedKey<UserProfile> {

    @ConstructorBinding
    public UserProfile(String username, List<String> dietaryRestrictions, List<String> healthGoals, int dailyCalorieTarget,
            List<String> allergies, List<String> dislikedIngredients) {
        this.username = username;
        this.dietaryRestrictions = dietaryRestrictions;
        this.healthGoals = healthGoals;
        this.dailyCalorieTarget = dailyCalorieTarget;
        this.allergies = allergies;
        this.dislikedIngredients = dislikedIngredients;
    }

    public UserProfile() {
        this("", Collections.emptyList(), Collections.emptyList(), 0, Collections.emptyList(), Collections.emptyList());
    }
}
