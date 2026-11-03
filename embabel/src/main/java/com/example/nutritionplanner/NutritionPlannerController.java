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

    private final NutritionPlanner planner;

    NutritionPlannerController(NutritionPlanner planner) {
        this.planner = planner;
    }

    @PostMapping
    ResponseEntity<WeeklyPlan> createNutritionPlan(@RequestBody WeeklyPlanRequest request, Principal principal) {
        return ResponseEntity.ok(planner.plan(request, principal));
    }
}