package com.example.nutritionplanner;

import dev.langchain4j.agentic.declarative.TypedKey;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

import java.util.Collections;
import java.util.List;

public record UserProfile(String username, List<String> dietaryRestrictions, List<String> healthGoals, int dailyCalorieTarget,
        List<String> allergies, List<String> dislikedIngredients) implements TypedKey<UserProfile> {

    // Tells Spring Boot to bind configuration properties via the canonical constructor
    @ConstructorBinding
    public UserProfile {}

    public UserProfile() {
        this("", Collections.emptyList(), Collections.emptyList(), 0, Collections.emptyList(), Collections.emptyList());
    }
}
