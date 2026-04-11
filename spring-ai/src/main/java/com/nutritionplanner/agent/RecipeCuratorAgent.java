package com.nutritionplanner.agent;

import com.nutritionplanner.agent.tools.NutritionPlannerTools;
import com.nutritionplanner.model.WeeklyPlan;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.PromptChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Component;

/**
 * Recipe Curator Agent - creates and revises meal plans with tool support and memory.
 * This agentic agent can invoke tools autonomously for seasonal ingredients and validation.
 *
 * Uses:
 * - @Tool methods via NutritionPlannerTools for autonomous capability invocation
 * - PromptChatMemoryAdvisor: Maintains context across multiple interactions
 * - defaultTools: Registers tools available to this agent at build time
 * - internalToolExecution: Enabled (default) for transparent tool calling
 */
@Component
public class RecipeCuratorAgent {

    private final ChatClient chatClient;

    public RecipeCuratorAgent(ChatClient.Builder builder, NutritionPlannerTools tools, ChatMemory chatMemory) {
        this.chatClient = builder
                .defaultSystem("""
                        You are a Recipe Curator — a culinary expert specializing in weekly meal planning.
                        Tone: Creative yet practical. You craft balanced, appealing recipes using seasonal
                        ingredients and always provide accurate nutrition information for each dish.

                        You have access to tools that allow you to:
                        - Fetch seasonal ingredients for a specific month and country
                        - Fetch user profile information to understand dietary needs
                        - Generate meal plans with detailed recipes and nutrition info
                        - Revise meal plans based on feedback

                        When creating a meal plan:
                        1. Ask for seasonal ingredients if available
                        2. Consider the user's dietary restrictions and preferences
                        3. Provide accurate nutrition information for all recipes
                        """)
                .defaultTools(tools)
                .defaultAdvisors(new PromptChatMemoryAdvisor(chatMemory))
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
