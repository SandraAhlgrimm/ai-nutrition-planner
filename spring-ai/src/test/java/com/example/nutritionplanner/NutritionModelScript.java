package com.example.nutritionplanner;

import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

final class NutritionModelScript {
    final AtomicInteger candidates = new AtomicInteger();
    final AtomicInteger audits = new AtomicInteger();
    final AtomicInteger skills = new AtomicInteger();
    final AtomicInteger months = new AtomicInteger();
    final AtomicInteger totals = new AtomicInteger();
    final List<Prompt> generationPrompts = new CopyOnWriteArrayList<>();
    final List<Prompt> allPrompts = new CopyOnWriteArrayList<>();
    int failedAudits;
    boolean interactive;
    boolean wrongDay;
    boolean malformedPlan;

    ChatResponse respond(Prompt prompt) {
        allPrompts.add(prompt);
        var system = prompt.getSystemMessage().getText();
        var results = prompt.getInstructions().stream().filter(ToolResponseMessage.class::isInstance)
                .map(ToolResponseMessage.class::cast).flatMap(m -> m.getResponses().stream()).toList();
        var options = (ToolCallingChatOptions) prompt.getOptions();
        var names = options.getToolCallbacks() == null ? List.<String>of()
                : options.getToolCallbacks().stream().map(t -> t.getToolDefinition().name()).toList();
        if (system.contains("Recipe Curator")) {
            generationPrompts.add(prompt);
            assertThat(prompt.getUserMessage().getText()).contains("MONDAY", "LUNCH", "vegetarian", "nuts",
                    "1800", "cilantro", "Under 30 minutes");
            if (interactive && results.isEmpty() && !prompt.getUserMessage().getText().contains("# Revision")) {
                assertThat(names).containsExactly("AskUserQuestionTool");
                return TestPlans.tool("AskUserQuestionTool", TestPlans.QUESTION_CALL);
            }
            if (interactive && !results.isEmpty()) {
                assertThat(results.getLast().responseData()).contains(TestPlans.QUESTION, TestPlans.ANSWER);
            }
            if (!interactive) assertThat(names).doesNotContain("AskUserQuestionTool");
            candidates.incrementAndGet();
            var json = TestPlans.JSON.writeValueAsString(TestPlans.plan());
            return TestPlans.text(malformedPlan ? "not JSON" : wrongDay ? json.replace("MONDAY", "TUESDAY") : json);
        }
        if (system.contains("Nutrition Guard")) {
            assertThat(prompt.getUserMessage().getText()).contains("MONDAY", "LUNCH", "vegetarian", "nuts");
            if (results.isEmpty()) {
                assertThat(names).containsExactly("toolSearchTool");
                return TestPlans.tool("toolSearchTool",
                        """
                        {"query":"total calories protein carbs fat sodium for each day","maxResults":3}
                        """);
            }
            if (results.size() == 1) {
                assertThat(results.getFirst().name()).isEqualTo("toolSearchTool");
                assertThat(results.getFirst().responseData()).contains("dailyNutritionTotals");
                assertThat(names).contains("dailyNutritionTotals");
                return TestPlans.tool("dailyNutritionTotals", "{}");
            }
            assertThat(results.getLast().name()).isEqualTo("dailyNutritionTotals");
            assertThat(results.getLast().responseData()).contains("600", "30.0", "70.0", "15.0", "500");
            totals.incrementAndGet();
            return TestPlans.text(audits.incrementAndGet() <= failedAudits
                    ? """
                      {"allPassed":false,"violations":[],"consolidatedFeedback":"Use less sodium"}
                      """
                    : """
                      {"allPassed":true,"violations":[],"consolidatedFeedback":""}
                      """);
        }
        assertThat(names).containsExactlyInAnyOrder("Skill", "currentMonth");
        assertThat(prompt.getUserMessage().getText()).contains("Germany");
        if (results.isEmpty()) return TestPlans.tool("Skill", "{\"command\":\"current-month\"}");
        if (results.size() == 1) {
            assertThat(results.getFirst().responseData()).contains("currentMonth", "UTC", "packaged");
            skills.incrementAndGet();
            return TestPlans.tool("currentMonth", "{}");
        }
        assertThat(results.getLast().responseData()).contains("September");
        months.incrementAndGet();
        return TestPlans.text("{\"ingredients\":[\"carrots\",\"potatoes\"]}");
    }
}
