package com.example.nutrition_planner;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "nutrition-planner")
record UserProfileProperties(List<UserProfile> userProfiles) {
    UserProfile getUserProfile(String name) {
        return userProfiles.stream().filter(u -> u.name().equals(name)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No profile found for user: %s".formatted(name)));
    }
}