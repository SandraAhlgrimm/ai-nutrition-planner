package com.example.nutritionplanner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Locale;

@Service
class NutritionPlannerAgent {

    private static final Logger log = LoggerFactory.getLogger(NutritionPlannerAgent.class);

    private final Agents.NutritionPlanner nutritionPlanner;
    private final Clock clock;

    NutritionPlannerAgent(Agents.NutritionPlanner nutritionPlanner, Clock clock) {
        this.nutritionPlanner = nutritionPlanner;
        this.clock = clock;
    }

    WeeklyPlan createNutritionPlan(String username, WeeklyPlanRequest request) {
        log.info("Starting meal plan creation for user: {}", username);
        var month = LocalDate.now(clock).getMonth().toString();
        var country = Locale.of("", request.countryCode()).getDisplayCountry(Locale.ENGLISH);

        var weeklyPlan = nutritionPlanner.createNutritionPlan(
                username, request, month, country, request.additionalInstructions());
        log.info("Finished validated meal plan creation for user: {}", username);
        return weeklyPlan;
    }
}
