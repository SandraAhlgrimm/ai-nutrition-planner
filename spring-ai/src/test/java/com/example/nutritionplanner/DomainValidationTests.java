package com.example.nutritionplanner;

import org.junit.jupiter.api.Test;
import org.springframework.ai.converter.BeanOutputConverter;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DomainValidationTests {
    @Test
    void sharedRestRequestMapsToTheExistingMcpAndBrowserDomain() {
        var request = WeeklyPlanRequest.fromDays(List.of(new WeeklyPlanRequest.DayPlanRequest(DayOfWeek.MONDAY,
                List.of(WeeklyPlanRequest.MealType.LUNCH))), "DE", "Under 30 minutes");
        assertThat(request).isEqualTo(TestPlans.request());
        assertThat(request.shapeFeedback(TestPlans.plan())).isEmpty();
    }

    @Test
    void duplicateDaysAndExtraMealsAreNotValid() {
        var lunch = TestPlans.plan().days().getFirst().lunch();
        assertThat(TestPlans.request().shapeFeedback(new WeeklyPlan(List.of(
                new WeeklyPlan.DailyPlan(DayOfWeek.MONDAY, lunch, lunch, null))))).isNotBlank();
        assertThat(TestPlans.request().shapeFeedback(new WeeklyPlan(List.of(
                TestPlans.plan().days().getFirst(), TestPlans.plan().days().getFirst())))).isNotBlank();
    }

    @Test
    void missingOrInconsistentAuditsCannotBecomePassingDefaults() {
        var converter = new BeanOutputConverter<>(NutritionAuditValidationResult.class);
        for (var json : List.of("{\"violations\":[],\"consolidatedFeedback\":\"\"}",
                "{\"allPassed\":true}", "{\"allPassed\":false,\"violations\":[],\"consolidatedFeedback\":\"\"}",
                "{\"allPassed\":true,\"violations\":[{}],\"consolidatedFeedback\":\"wrong\"}")) {
            assertThatThrownBy(() -> converter.convert(json)).isInstanceOf(RuntimeException.class);
        }
    }

    @Test
    void invalidNutritionAndUnknownDaysFailExplicitly() {
        assertThatThrownBy(() -> new NutritionInfo(null, 1.0, 1.0, 1.0, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new NutritionInfo(1, Double.NaN, 1.0, 1.0, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TestPlans.plan().nutritionTotalsForDay(DayOfWeek.TUESDAY))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new WeeklyPlanRequest(Map.of(), "DE", "").validate())
                .isInstanceOf(InvalidPlanRequestException.class);
    }
}
