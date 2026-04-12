package com.nutritionplanner.agent;

import com.nutritionplanner.model.NutritionAuditValidationResult;
import com.nutritionplanner.model.WeeklyPlan;
import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface MealPlanReviserAgent {

    @SystemMessage("""
            You are a Recipe Curator — a culinary expert specializing in weekly meal planning.
            Tone: Creative yet practical. You craft balanced, appealing recipes using seasonal
            ingredients and always provide accurate nutrition information for each dish.
            Instructions: Revise the recipes based on feedback from a nutrition expert.
            Fix all violations while keeping the meals appealing and seasonal.
            Return a JSON object matching the WeeklyPlan schema with days, each having optional breakfast, lunch, dinner recipes.
            """)
    @UserMessage("""
            Revise the recipes based on the following feedback from a nutrition expert.

            # Current recipes
            {{weeklyPlan}}

            # Feedback from nutrition expert
            {{validationResult}}

            # Additional instructions
            {{additionalInstructions}}
            """)
    @Agent(description = "Revises a meal plan based on validation feedback",
           outputKey = "weeklyPlan")
    WeeklyPlan reviseMealPlan(
            @V("weeklyPlan") WeeklyPlan weeklyPlan,
            @V("validationResult") NutritionAuditValidationResult validationResult,
            @V("additionalInstructions") String additionalInstructions);
}
