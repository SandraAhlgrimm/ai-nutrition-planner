package com.nutritionplanner.controller;

import com.nutritionplanner.model.WeeklyPlanRequest;
import com.nutritionplanner.orchestration.StreamingPlannerService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.security.Principal;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.List;

@RestController
public class SseProgressController {

    private final StreamingPlannerService streamingPlannerService;

    public SseProgressController(StreamingPlannerService streamingPlannerService) {
        this.streamingPlannerService = streamingPlannerService;
    }

    @GetMapping("/plan/stream")
    public SseEmitter streamPlan(
            @RequestParam(required = false) List<String> monday,
            @RequestParam(required = false) List<String> tuesday,
            @RequestParam(required = false) List<String> wednesday,
            @RequestParam(required = false) List<String> thursday,
            @RequestParam(required = false) List<String> friday,
            @RequestParam(required = false) List<String> saturday,
            @RequestParam(required = false) List<String> sunday,
            @RequestParam(defaultValue = "DE") String countryCode,
            @RequestParam(required = false, defaultValue = "") String additionalInstructions,
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
        return streamingPlannerService.streamPlan(request, principal.getName());
    }

    private void addDay(List<WeeklyPlanRequest.DayPlanRequest> days, DayOfWeek day, List<String> meals) {
        if (meals != null && !meals.isEmpty()) {
            days.add(new WeeklyPlanRequest.DayPlanRequest(day,
                    meals.stream().map(WeeklyPlanRequest.MealType::valueOf).toList()));
        }
    }
}
