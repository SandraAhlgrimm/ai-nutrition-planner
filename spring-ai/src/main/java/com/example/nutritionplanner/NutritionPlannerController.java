package com.example.nutritionplanner;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@RestController
@RequestMapping("/api/nutrition-plan")
class NutritionPlannerController {

    private final NutritionPlannerAgent nutritionPlannerAgent;

    NutritionPlannerController(NutritionPlannerAgent nutritionPlannerAgent) {

        this.nutritionPlannerAgent = nutritionPlannerAgent;
    }

    @PostMapping
     ResponseEntity<WeeklyPlan> createNutritionPlan(@RequestBody WeeklyPlanRequest request, Principal principal) {
        var weeklyPlan = nutritionPlannerAgent.createNutritionPlan(principal.getName(), request);
        return ResponseEntity.ok(weeklyPlan);
    }
}