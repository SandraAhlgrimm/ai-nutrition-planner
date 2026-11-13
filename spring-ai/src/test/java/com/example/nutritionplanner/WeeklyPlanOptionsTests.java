package com.example.nutritionplanner;

import org.junit.jupiter.api.Test;
import org.springframework.ai.openai.OpenAiChatModel.ResponseFormat;
import org.springframework.ai.openai.OpenAiChatOptions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeeklyPlanOptionsTests {

    @Test
    void weeklyPlanOptions_requestNonStrictJsonSchema() {
        var responseFormat = NutritionPlannerAgent.weeklyPlanOptions().build().getResponseFormat();

        assertNotNull(responseFormat);
        assertEquals(ResponseFormat.Type.JSON_SCHEMA, responseFormat.getType());
        // Strict mode would be rejected, because the DailyPlan meals are nullable and therefore not required
        assertEquals(Boolean.FALSE, responseFormat.getStrict());
        assertTrue(responseFormat.getJsonSchema().contains("\"days\""));
    }

    @Test
    void weeklyPlanOptions_keepConfiguredModelSettingsWhenMerged() {
        var configured = OpenAiChatOptions.builder().model("gpt-6-sol").reasoningEffort("none").build();

        // The same merge DefaultChatClient applies to per-call options
        var merged = configured.mutate().combineWith(NutritionPlannerAgent.weeklyPlanOptions()).build();

        assertEquals("gpt-6-sol", merged.getModel());
        assertEquals("none", merged.getReasoningEffort());
        assertNotNull(merged.getResponseFormat());
    }
}
