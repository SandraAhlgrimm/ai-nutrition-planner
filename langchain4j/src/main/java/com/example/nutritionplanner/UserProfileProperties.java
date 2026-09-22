package com.example.nutritionplanner;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.V;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "nutrition-planner")
public record UserProfileProperties(List<UserProfile> userProfiles) {
    @Agent(description = "Loads the authenticated user's dietary profile", typedOutputKey = UserProfile.class)
    public UserProfile getUserProfile(@V("username") String username) {
        return userProfiles.stream().filter(u -> u.username().equals(username)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No profile found for user: %s".formatted(username)));
    }
}