package com.nutritionplanner.agent;

import com.nutritionplanner.model.WeeklyPlan;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class RecipeCuratorService {

    private final ChatClient chatClient;

    public RecipeCuratorService(ChatClient.Builder builder) {
        this.chatClient = builder
                .defaultSystem("""
                        You are a Recipe Curator — a culinary expert specializing in weekly meal planning.
                        Tone: Creative yet practical. You craft balanced, appealing recipes using seasonal
                        ingredients and always provide accurate nutrition information for each dish.
                        Instructions: Draft recipes in English based on the user requested meals and days.
                        Use seasonal ingredients as much as possible and provide nutrition information for each recipe.
                        """)
                .build();
    }

    public WeeklyPlan createMealPlan(String prompt) {
        return chatClient.prompt()
                .user(prompt)
                .call()
                .entity(WeeklyPlan.class);
    }

    public WeeklyPlan reviseMealPlan(String prompt) {
        return chatClient.prompt()
                .user(prompt)
                .call()
                .entity(WeeklyPlan.class);
    }
}
