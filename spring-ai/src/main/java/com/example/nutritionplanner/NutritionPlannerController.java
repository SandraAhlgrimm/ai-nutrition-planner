package com.example.nutritionplanner;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/nutrition-plan")
class NutritionPlannerController {

    private final NutritionPlannerAgent agent;

    NutritionPlannerController(NutritionPlannerAgent agent) {
        this.agent = agent;
    }

    @PostMapping
    WeeklyPlan createNutritionPlan(@RequestBody PlanRequest request, Principal principal) {
        var weeklyRequest = WeeklyPlanRequest.fromDays(request.days(), request.countryCode(), request.additionalInstructions());
        return agent.createNutritionPlan(principal.getName(), weeklyRequest);
    }

    record PlanRequest(List<WeeklyPlanRequest.DayPlanRequest> days, String countryCode, String additionalInstructions) {}
}
