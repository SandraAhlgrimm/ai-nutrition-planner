package com.example.nutritionplanner;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import tools.jackson.databind.json.JsonMapper;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class TestPlans {
    static final String QUESTION = "Which cooking style?";
    static final String ANSWER = "Quick";
    static final String REST_REQUEST = """
            {"days":[{"day":"MONDAY","meals":["LUNCH"]}],
             "countryCode":"DE","additionalInstructions":"Under 30 minutes"}
            """;
    static final String QUESTION_CALL = """
            {"questions":[{"question":"Which cooking style?","header":"Cooking",
              "options":[{"label":"Quick","description":"Under 30 minutes"},
                         {"label":"Slow","description":"Long cooking"}],"multiSelect":false}]}
            """;
    static final JsonMapper JSON = JsonMapper.builder().build();

    static WeeklyPlan plan() {
        return new WeeklyPlan(List.of(new WeeklyPlan.DailyPlan(DayOfWeek.MONDAY, null,
                new Recipe("Vegetable soup", List.of(new Recipe.Ingredient("carrots", "200", "g")),
                        new NutritionInfo(600, 30.0, 70.0, 15.0, 500), "Cook and serve.", 20), null)));
    }

    static WeeklyPlanRequest request() {
        return new WeeklyPlanRequest(Map.of(DayOfWeek.MONDAY, Set.of(WeeklyPlanRequest.MealType.LUNCH)),
                "DE", "Under 30 minutes");
    }

    static ChatResponse text(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }

    static ChatResponse tool(String name, String arguments) {
        return new ChatResponse(List.of(new Generation(AssistantMessage.builder().content("")
                .toolCalls(List.of(new AssistantMessage.ToolCall(name + "-id", "function", name, arguments))).build())));
    }
}
