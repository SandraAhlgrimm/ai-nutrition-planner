package com.example.nutritionplanner;

import com.example.NutritionPlannerApplication;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(30)
class ProviderHttpTests {
    @ParameterizedTest
    @ValueSource(strings = {"openai", "azure", "ollama"})
    void realProviderRunsSkillsStructuredOutputSearchAndAuditThroughItsHttpClient(String profile) throws Exception {
        try (var server = new LocalChatServer()) {
            server.tool("Skill", "{\"command\":\"current-month\"}")
                    .tool("currentMonth", "{}")
                    .text("{\"ingredients\":[\"carrots\",\"potatoes\"]}")
                    .text(TestPlans.JSON.writeValueAsString(TestPlans.plan()))
                    .tool("toolSearchTool", "{\"query\":\"total calories protein carbs fat sodium for each day\",\"maxResults\":3}")
                    .tool("dailyNutritionTotals", "{}")
                    .text("{\"allPassed\":true,\"violations\":[],\"consolidatedFeedback\":\"\"}");
            new WebApplicationContextRunner()
                    .withInitializer(new ConfigDataApplicationContextInitializer())
                    .withUserConfiguration(NutritionPlannerApplication.class)
                    .withPropertyValues("spring.profiles.active=" + profile, "spring.ai.mcp.server.enabled=false",
                            "OPENAI_API_KEY=offline-test-key", "OPENAI_MODEL_NAME=offline-model",
                            "AZURE_OPENAI_API_KEY=offline-test-key",
                            "AZURE_OPENAI_ENDPOINT=" + server.baseUrl(),
                            "AZURE_OPENAI_DEPLOYMENT_NAME=offline-model",
                            "spring.ai.openai.base-url=" + server.baseUrl(),
                            "OLLAMA_BASE_URL=" + server.baseUrl(), "OLLAMA_MODEL_NAME=offline-model")
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        assertThat(server.requests()).isEmpty();
                        assertThat(context.getBean(ChatModel.class).getOptions())
                                .isInstanceOf(profile.equals("ollama") ? OllamaChatOptions.class : OpenAiChatOptions.class);
                        assertThat(context.getBean(NutritionPlannerAgent.class)
                                .createNutritionPlan("alice", TestPlans.request())).isEqualTo(TestPlans.plan());
                        assertThat(server.requests()).hasSize(7);
                        assertThat(server.requests().get(1).body().toString()).contains("currentMonth", "UTC", "packaged");
                        assertThat(server.requests().get(2).body().toString()).contains("currentMonth");
                        assertThat(server.requests().get(3).body().toString())
                                .contains("MONDAY", "LUNCH", "vegetarian", "nuts", "2250-2750", "Under 30 minutes");
                        assertThat(server.requests().get(6).body().toString()).contains("dailyNutritionTotals", "2500");
                        assertThat(server.requests()).allSatisfy(request -> {
                            assertThat(request.body().path("model").asText()).isEqualTo("offline-model");
                            assertThat(request.path()).contains(profile.equals("ollama") ? "/api/chat" : "/chat/completions");
                        });
                        assertThat(server.errors()).isEmpty();
                        assertThat(server.remaining()).isZero();
                    });
        }
    }
}
