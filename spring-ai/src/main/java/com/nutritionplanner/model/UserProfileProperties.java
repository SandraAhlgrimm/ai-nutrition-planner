package com.nutritionplanner.model;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "nutrition-planner")
public record UserProfileProperties(List<UserProfile> userProfiles) {

    public UserProfile getUserProfile(String username) {
        return userProfiles.stream()
                .filter(p -> p.name().equalsIgnoreCase(username))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown user: " + username));
    }
}
