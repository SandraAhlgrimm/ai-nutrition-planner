package com.nutritionplanner.agent;

import com.nutritionplanner.agent.tools.NutritionPlannerTools;
import com.nutritionplanner.model.NutritionAuditValidationResult;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.PromptChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Component;

/**
 * Nutrition Guard Agent - validates meal plans autonomously with tool support.
 * This agentic agent can fetch user profiles and validate recipes independently.
 *
 * Uses:
 * - @Tool methods via NutritionPlannerTools for autonomous decision-making
 * - PromptChatMemoryAdvisor: Maintains validation context across interactions
 * - defaultTools: Registers available tools at build time
 */
@Component
public class NutritionGuardAgent {

    private final ChatClient chatClient;

    public NutritionGuardAgent(ChatClient.Builder builder, NutritionPlannerTools tools, ChatMemory chatMemory) {
        this.chatClient = builder
                .defaultSystem("""
                        You are a Nutrition Guard — a strict dietary compliance validator
                        specialized in ensuring meal plans meet user health requirements and dietary restrictions.
                        Tone: Thorough, precise, and uncompromising. You apply dietary rules consistently and
                        flag every violation without exception. Be concise and factual in your assessments.

                        You have access to tools that allow you to:
                        - Fetch user profile information (allergies, restrictions, calorie targets)
                        - Validate meal plans against user constraints

                        When validating, check each recipe for:
                        1. NUTRITION_INFO: Nutrition information is available for each recipe
                        2. CALORIE_OVERFLOW: calories exceed daily calorie target
                        3. ALLERGEN_PRESENT: recipe contains an ingredient matching user's allergies
                        4. RESTRICTION_VIOLATION: recipe violates dietary restrictions (e.g., meat for vegetarian)
                        5. DISLIKED_INGREDIENTS_PRESENT: recipe contains disliked ingredients

                        Provide a structured validation result with specific violations and improvement suggestions.
                        """)
                .defaultTools(tools)
                .defaultAdvisors(PromptChatMemoryAdvisor.builder(chatMemory).build())
                .build();
    }

    public NutritionAuditValidationResult validate(String prompt) {
        return chatClient.prompt()
                .user(prompt)
                .call()
                .entity(NutritionAuditValidationResult.class);
    }
}
