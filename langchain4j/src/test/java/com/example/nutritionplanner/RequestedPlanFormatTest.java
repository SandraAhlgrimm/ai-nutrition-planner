package com.example.nutritionplanner;

import dev.langchain4j.model.chat.request.json.JsonRawSchema;
import io.modelcontextprotocol.json.McpJsonDefaults;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Map;

import static com.example.nutritionplanner.PlanFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;

class RequestedPlanFormatTest {

    private final JsonMapper json = JsonMapper.builder().build();

    @Test
    void acceptsACompleteRequestedPlan() {
        assertThat(valid(REQUEST, plan(1))).isTrue();
    }

    @ParameterizedTest
    @MethodSource("com.example.nutritionplanner.RequestShapeAuditTest#invalidPlans")
    void rejectsMissingExtraOrDuplicateMealsAndDays(WeeklyPlan plan, String ignoredFeedback) {
        assertThat(valid(REQUEST, plan)).isFalse();
    }

    @Test
    void constrainsTheReportedMondayDinnerAndTuesdayLunchRequestWithoutLosingEitherMeal() {
        var request = new WeeklyPlanRequest(List.of(
                new WeeklyPlanRequest.DayPlanRequest(DayOfWeek.MONDAY, List.of(WeeklyPlanRequest.MealType.DINNER)),
                new WeeklyPlanRequest.DayPlanRequest(DayOfWeek.TUESDAY, List.of(WeeklyPlanRequest.MealType.LUNCH))),
                "DE", "One simple vegetarian lentil, carrot and potato soup.");
        var dinner = plan(1).days().getFirst();
        var lunch = new WeeklyPlan.DailyPlan(DayOfWeek.TUESDAY, null, dinner.dinner(), null);
        assertThat(valid(request, new WeeklyPlan(List.of(dinner, lunch)))).isTrue();
        assertThat(valid(request, new WeeklyPlan(List.of(dinner)))).isFalse();
        assertThat(valid(request, new WeeklyPlan(List.of(dinner,
                new WeeklyPlan.DailyPlan(DayOfWeek.TUESDAY, null, null, null))))).isFalse();
        assertThat(RequestedPlanFormat.requiredMeals(request)).isEqualTo("MONDAY DINNER\nTUESDAY LUNCH");
    }

    @Test
    void requiresAllSixMealsWhenAllMealsAreSelectedForMondayAndTuesday() {
        var meals = List.of(WeeklyPlanRequest.MealType.values());
        var request = new WeeklyPlanRequest(List.of(
                new WeeklyPlanRequest.DayPlanRequest(DayOfWeek.MONDAY, meals),
                new WeeklyPlanRequest.DayPlanRequest(DayOfWeek.TUESDAY, meals)), "DE", "One simple vegetarian meal");
        var recipe = plan(1).days().getFirst().dinner();
        var monday = new WeeklyPlan.DailyPlan(DayOfWeek.MONDAY, recipe, recipe, recipe);
        assertThat(valid(request, new WeeklyPlan(List.of(monday,
                new WeeklyPlan.DailyPlan(DayOfWeek.TUESDAY, recipe, recipe, recipe))))).isTrue();
        assertThat(valid(request, new WeeklyPlan(List.of(monday,
                new WeeklyPlan.DailyPlan(DayOfWeek.TUESDAY, null, recipe, recipe))))).isFalse();
        assertThat(valid(request, new WeeklyPlan(List.of(monday,
                new WeeklyPlan.DailyPlan(DayOfWeek.TUESDAY, recipe, null, recipe))))).isFalse();
    }

    @Test
    void requiresNutritionAndCompleteRecipeFieldsWithoutReplacingModelValues() {
        var incompleteRecipe = new Recipe("Soup", List.of(new Recipe.Ingredient("carrot", "200", "g")),
                null, "Boil the carrots.", 20);
        var invalid = new WeeklyPlan(List.of(
                new WeeklyPlan.DailyPlan(DayOfWeek.MONDAY, null, null, incompleteRecipe), plan(1).days().getLast()));

        assertThat(valid(REQUEST, invalid)).isFalse();
    }

    @Test
    void schemasStaySpecificToEachInvocation() {
        var dinner = plan(1).days().getFirst();
        var oneMeal = new WeeklyPlanRequest(List.of(REQUEST.days().getFirst()), "DE", "");
        var firstSchema = schema(REQUEST);
        var secondSchema = schema(oneMeal);

        assertThat(firstSchema).isNotEqualTo(secondSchema).isEqualTo(schema(REQUEST));
        assertThat(McpJsonDefaults.getSchemaValidator().validate(secondSchema,
                new WeeklyPlan(List.of(dinner))).valid()).isTrue();
        assertThat(McpJsonDefaults.getSchemaValidator().validate(firstSchema,
                new WeeklyPlan(List.of(dinner))).valid()).isFalse();
    }

    private boolean valid(WeeklyPlanRequest request, WeeklyPlan plan) {
        return McpJsonDefaults.getSchemaValidator().validate(schema(request), plan).valid();
    }

    private Map<String, Object> schema(WeeklyPlanRequest request) {
        var format = RequestedPlanFormat.parameters(request, json).responseFormat();
        assertThat(format.jsonSchema().rootElement()).isInstanceOf(JsonRawSchema.class);
        var schema = (JsonRawSchema) format.jsonSchema().rootElement();
        return json.readValue(schema.schema(), new TypeReference<>() {});
    }
}
