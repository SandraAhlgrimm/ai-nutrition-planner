package com.example.nutritionplanner;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "nutrition-planner")
record UserProfileProperties(List<UserProfile> userProfiles) {
    UserProfile getUserProfile(String username) {
        return userProfiles.stream().filter(u -> u.username().equals(username)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No profile found for user: %s".formatted(username)));
    }
}