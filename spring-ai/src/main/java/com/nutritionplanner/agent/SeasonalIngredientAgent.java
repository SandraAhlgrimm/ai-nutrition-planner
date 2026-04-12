package com.nutritionplanner.agent;

import com.nutritionplanner.agent.tools.NutritionPlannerTools;
import com.nutritionplanner.model.SeasonalIngredients;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.PromptChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Component;

/**
 * Seasonal Ingredient Agent - provides localized ingredient recommendations autonomously.
 * This agentic agent can fetch seasonal ingredients independently based on requests.
 *
 * Uses:
 * - @Tool methods via NutritionPlannerTools for autonomous ingredient lookups
 * - PromptChatMemoryAdvisor: Maintains context about ingredient preferences across interactions
 * - defaultTools: Registers available tools for autonomous invocation
 */
@Component
public class SeasonalIngredientAgent {

    private final ChatClient chatClient;

    public SeasonalIngredientAgent(ChatClient.Builder builder, NutritionPlannerTools tools, ChatMemory chatMemory) {
        this.chatClient = builder
                .defaultSystem("""
                        You are a Nutrition Expert with deep knowledge of seasonal produce and ingredient availability.
                        Your role is to identify and recommend ingredients that are in season for specific months and locations.

                        You have access to tools to fetch seasonal ingredients for any month and country.
                        Use these tools to provide accurate, current ingredient recommendations based on requests.

                        When recommending seasonal ingredients:
                        - Prioritize fresh, locally-available options for the specified month and location
                        - Consider nutritional value and meal planning applications
                        - Suggest diverse ingredients suitable for different dishes and cuisines
                        """)
                .defaultTools(tools)
                .defaultAdvisors(PromptChatMemoryAdvisor.builder(chatMemory).build())
                .build();
    }

    public SeasonalIngredients fetchSeasonalIngredients(String prompt) {
        return chatClient.prompt()
                .user(prompt)
                .call()
                .entity(SeasonalIngredients.class);
    }
}
