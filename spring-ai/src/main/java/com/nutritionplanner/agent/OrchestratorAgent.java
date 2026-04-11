package com.nutritionplanner.agent;

import com.nutritionplanner.agent.tools.OrchestratorTools;
import com.nutritionplanner.model.WeeklyPlan;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.PromptChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Component;

/**
 * Orchestrator Agent - coordinates agentic meal planning using Orchestrator-Workers pattern.
 *
 * This agent autonomously:
 * 1. Decomposes the task: "Create a validated meal plan"
 * 2. Decides which tools/agents to invoke and in what order
 * 3. Processes tool results and adapts strategy based on outcomes
 * 4. Iterates until plan meets validation criteria
 *
 * Uses:
 * - @Tool methods via OrchestratorTools for autonomous agent/tool invocation
 * - PromptChatMemoryAdvisor: Maintains full planning session context for adaptive decisions
 * - defaultTools: Registers all worker agent tools for autonomous selection
 * - internalToolExecution: Enabled (default) for transparent tool calling
 *
 * Agentic Pattern: Orchestrator-Workers
 * - Orchestrator (this agent) decomposes and directs workflow
 * - Workers (RecipeCurator, NutritionGuard agents) execute specialized tasks
 * - Adaptive behavior adjusts strategy based on validation failures
 */
@Component
public class OrchestratorAgent {

    private final ChatClient chatClient;

    public OrchestratorAgent(ChatClient.Builder builder, OrchestratorTools orchestratorTools, ChatMemory chatMemory) {
        this.chatClient = builder
                .defaultSystem("""
                        You are the Orchestrator - an intelligent meal planning coordinator that manages the
                        entire process of creating personalized nutrition plans.

                        Your role is to:
                        1. Understand the user's meal planning requirements and constraints
                        2. Gather necessary information (user profile, seasonal ingredients)
                        3. Create a meal plan using the Recipe Curator agent
                        4. Validate the plan using the Nutrition Guard agent
                        5. If validation fails, adjust your strategy and refine the plan
                        6. Return the final validated meal plan

                        You have access to tools (worker agents):
                        - fetchUserProfile: Get detailed user dietary needs and constraints
                        - getSeasonalIngredients: Get seasonal ingredients for location/month
                        - createMealPlan: Use Recipe Curator to create initial or revised meal plan
                        - validateMealPlan: Use Nutrition Guard to validate recipes
                        - reviseMealPlan: Ask Recipe Curator to revise based on feedback

                        ADAPTIVE WORKFLOW GUIDANCE:
                        When validation fails:
                        - Analyze the specific violations reported by the Nutrition Guard
                        - Decide on strategy adjustments:
                          * Adjust cuisine type or ingredient preferences
                          * Focus on lower-calorie alternatives for calorie violations
                          * Remove allergen sources for allergen violations
                          * Suggest alternatives to disliked ingredients
                          * Ensure restrictions are clearly communicated to Recipe Curator
                        - Ask Recipe Curator to revise with explicit guidance on what needs to change
                        - Re-validate the revised plan
                        - Continue iterating up to 3 times, then return best-effort plan if needed

                        Be thoughtful, analytical, and adaptive. Make informed decisions about strategy
                        adjustments based on the specific violations encountered. Use tool calling to
                        autonomously execute tasks rather than waiting for user input.
                        """)
                .defaultTools(orchestratorTools)
                .defaultAdvisors(new PromptChatMemoryAdvisor(chatMemory))
                .build();
    }

    public WeeklyPlan createMealPlan(String prompt) {
        return chatClient.prompt()
                .user(prompt)
                .call()
                .entity(WeeklyPlan.class);
    }
}
