package com.nutritionplanner.controller;

import com.nutritionplanner.model.WeeklyPlanRequest;
import com.nutritionplanner.orchestration.NutritionPlannerOrchestrator;
import org.springframework.beans.factory.annotation.Value;
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
    private final String aiModel;

    public NutritionPlannerUiController(NutritionPlannerOrchestrator orchestrator,
                                         @Value("${langchain4j.azure-open-ai.chat-model.deployment-name:unknown}") String aiModel) {
        this.orchestrator = orchestrator;
        this.aiModel = aiModel;
    }

    @GetMapping("/login")
    public String login(Model model) {
        model.addAttribute("aiModel", "Azure OpenAI (" + aiModel + ")");
        return "login";
    }

    @GetMapping("/")
    public String form(Model model) {
        model.addAttribute("aiModel", "Azure OpenAI (" + aiModel + ")");
        return "index";
    }

    @PostMapping("/plan")
    public String createPlan(
            @RequestParam(required = false) List<String> monday,
            @RequestParam(required = false) List<String> tuesday,
            @RequestParam(required = false) List<String> wednesday,
            @RequestParam(required = false) List<String> thursday,
            @RequestParam(required = false) List<String> friday,
            @RequestParam(required = false) List<String> saturday,
            @RequestParam(required = false) List<String> sunday,
            @RequestParam(defaultValue = "DE") String countryCode,
            @RequestParam(required = false, defaultValue = "") String additionalInstructions,
            Model model,
            Principal principal) {

        var days = new ArrayList<WeeklyPlanRequest.DayPlanRequest>();
        addDay(days, DayOfWeek.MONDAY, monday);
        addDay(days, DayOfWeek.TUESDAY, tuesday);
        addDay(days, DayOfWeek.WEDNESDAY, wednesday);
        addDay(days, DayOfWeek.THURSDAY, thursday);
        addDay(days, DayOfWeek.FRIDAY, friday);
        addDay(days, DayOfWeek.SATURDAY, saturday);
        addDay(days, DayOfWeek.SUNDAY, sunday);

        var request = new WeeklyPlanRequest(days, countryCode, additionalInstructions);
        var weeklyPlan = orchestrator.createPlan(request, principal.getName());

        model.addAttribute("plan", weeklyPlan);
        model.addAttribute("aiModel", "Azure OpenAI (" + aiModel + ")");
        return "fragments/plan :: plan";
    }

    private void addDay(List<WeeklyPlanRequest.DayPlanRequest> days, DayOfWeek day, List<String> meals) {
        if (meals != null && !meals.isEmpty()) {
            days.add(new WeeklyPlanRequest.DayPlanRequest(day,
                    meals.stream().map(WeeklyPlanRequest.MealType::valueOf).toList()));
        }
    }
}
