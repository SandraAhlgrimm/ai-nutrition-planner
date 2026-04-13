package com.example.nutritionplanner.controller;

import com.example.nutritionplanner.model.WeeklyPlan;
import com.example.nutritionplanner.model.WeeklyPlanRequest;
import com.example.nutritionplanner.orchestration.NutritionPlannerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@RestController
@RequestMapping("/api")
public class NutritionPlannerController {

    private final NutritionPlannerService plannerService;

    public NutritionPlannerController(NutritionPlannerService plannerService) {
        this.plannerService = plannerService;
    }

    @PostMapping("/nutrition-plan")
    public ResponseEntity<WeeklyPlan> createNutritionPlan(@RequestBody WeeklyPlanRequest request,
                                                           Principal principal) {
        var weeklyPlan = plannerService.createPlan(request, principal.getName());
        return ResponseEntity.ok(weeklyPlan);
    }
}
