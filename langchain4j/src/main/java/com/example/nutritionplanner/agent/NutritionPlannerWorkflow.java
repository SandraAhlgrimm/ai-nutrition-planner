package com.nutritionplanner.agent;

import com.nutritionplanner.model.UserProfile;
import com.nutritionplanner.model.WeeklyPlan;
import com.nutritionplanner.model.WeeklyPlanRequest;
import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.V;

/**
 * Typed entry point for the composed agentic nutrition planning workflow.
 * The implementation is built by composing agents via AgenticServices builders.
 */
public interface NutritionPlannerWorkflow {

    @Agent(description = "Creates a validated weekly nutrition plan",
           outputKey = "weeklyPlan")
    WeeklyPlan createNutritionPlan(
            @V("userProfile") UserProfile userProfile,
            @V("request") WeeklyPlanRequest request,
            @V("month") String month,
            @V("country") String country,
            @V("additionalInstructions") String additionalInstructions);
}
