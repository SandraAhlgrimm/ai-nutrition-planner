package com.nutritionplanner.controller;

import com.nutritionplanner.model.WeeklyPlanRequest;
import com.nutritionplanner.orchestration.NutritionPlannerOrchestrator;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.security.Principal;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.List;

@Controller
public class NutritionPlannerUiController {

    private final NutritionPlannerOrchestrator orchestrator;

    public NutritionPlannerUiController(NutritionPlannerOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @GetMapping("/")
    public String index() {
        return "index";
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @PostMapping("/plan")
    public String generatePlan(
            @RequestParam(required = false) List<String> monday,
            @RequestParam(required = false) List<String> tuesday,
            @RequestParam(required = false) List<String> wednesday,
            @RequestParam(required = false) List<String> thursday,
            @RequestParam(required = false) List<String> friday,
            @RequestParam(required = false) List<String> saturday,
            @RequestParam(required = false) List<String> sunday,
            @RequestParam(defaultValue = "US") String countryCode,
            @RequestParam(defaultValue = "") String additionalInstructions,
            Principal principal,
            Model model) {

        var days = new ArrayList<WeeklyPlanRequest.DayPlanRequest>();
        addDay(days, DayOfWeek.MONDAY, monday);
        addDay(days, DayOfWeek.TUESDAY, tuesday);
        addDay(days, DayOfWeek.WEDNESDAY, wednesday);
        addDay(days, DayOfWeek.THURSDAY, thursday);
        addDay(days, DayOfWeek.FRIDAY, friday);
        addDay(days, DayOfWeek.SATURDAY, saturday);
        addDay(days, DayOfWeek.SUNDAY, sunday);

        var request = new WeeklyPlanRequest(days, countryCode, additionalInstructions);
        var plan = orchestrator.createPlan(request, principal.getName());

        model.addAttribute("plan", plan);
        return "fragments/plan :: plan";
    }

    private void addDay(List<WeeklyPlanRequest.DayPlanRequest> days, DayOfWeek dayOfWeek, List<String> meals) {
        if (meals != null && !meals.isEmpty()) {
            days.add(new WeeklyPlanRequest.DayPlanRequest(dayOfWeek,
                    meals.stream().map(WeeklyPlanRequest.MealType::valueOf).toList()));
        }
    }
}
