package com.example.nutritionplanner;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.scope.ResultWithAgenticScope;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
class NutritionPlannerAgent {

    ResultWithAgenticScope<WeeklyPlan> createNutritionPlan(String planId, String username, WeeklyPlanRequest request) {
        var country = Locale.of("", request.countryCode()).getDisplayCountry(Locale.ENGLISH);

        return AgenticServices.createAgenticSystem(Agents.NutritionPlanner.class)
                .createNutritionPlan(planId, username, request, country, request.additionalInstructions());
    }
}
