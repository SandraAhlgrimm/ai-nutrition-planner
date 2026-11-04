package com.example.nutritionplanner;

import com.embabel.agent.spi.support.springai.SpringAiLlmService;
import com.embabel.common.ai.model.LlmOptions;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.openai.OpenAiChatOptions;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static com.example.nutritionplanner.NutritionTestData.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(30)
class ModelProviderLoopTests {

    @ParameterizedTest
    @CsvSource({"ollama,false", "openai,false", "azure,false", "ollama,true", "openai,true", "azure,true"})
    void realGoalUsesNativeOptionsAndConfiguredModelThroughSkillsAndUnfoldingTools(String profile, boolean patterns) throws IOException {
        String model = switch (profile) {
            case "ollama" -> "fixture-qwen:custom";
            case "openai" -> "fixture-openai-model";
            case "azure" -> "fixture-azure-deployment";
            default -> throw new IllegalArgumentException(profile);
        };
        try (var fixture = new LocalModelFixture(profile, model)) {
            fixture.respondWith(request -> switch (fixture.requests.size() - 1) {
                case 0 -> fixture.toolCall(request.path("tools").valueStream().map(tool -> tool.path("function"))
                        .filter(tool -> tool.path("description").asText().contains("Call once with no arguments"))
                        .findFirst().orElseThrow().path("name").asText(), Map.of());
                case 1 -> fixture.toolCall(request.path("tools").valueStream().map(tool -> tool.path("function"))
                        .filter(tool -> tool.path("description").asText().contains("Execute the current-month.sh"))
                        .findFirst().orElseThrow().path("name").asText(), Map.of());
                case 2 -> fixture.text(SEASONAL);
                case 3 -> fixture.text(plan("Fixture lentils"));
                case 4 -> fixture.toolCall("weekly_meal_plan_tools", Map.of("category", "nutrition"));
                case 5 -> fixture.toolCall("dailyNutritionTotals", Map.of());
                case 6 -> fixture.text(PASS);
                default -> fixture.error();
            });
            var runner = fixture.runner(patterns).withPropertyValues(
                    "embabel.agent.platform.action-qos.default.max-attempts=1",
                    "embabel.agent.platform.llm-operations.data-binding.max-attempts=1");
            runner.run(context -> {
                assertThat(context).hasNotFailed().hasSingleBean(ChatModel.class);
                assertTrue(fixture.requests.isEmpty(), "No provider traffic at startup");

                // No mocked Ai, LlmOperations or ChatModel: run the production GOAP graph and tool loop.
                var plan = context.getBean(NutritionPlanner.class).plan(REQUEST, () -> "alice");
                assertEquals(plan("Fixture lentils"), plan);
                assertEquals(model, context.getBean(ChatModel.class).getOptions().getModel());
                var options = context.getBean(SpringAiLlmService.class).convertOptions(LlmOptions.withAutoLlm());
                if (profile.equals("ollama")) {
                    assertInstanceOf(OllamaChatOptions.class, options);
                } else {
                    assertInstanceOf(OpenAiChatOptions.class, options);
                }
                assertEquals(model, options.getModel());

                assertEquals(7, fixture.requests.size(), "Three skill calls, drafting, and three audit calls");
                for (var request : fixture.requests) {
                    assertEquals(model, request.path("model").asText());
                    assertFalse(request.path("stream").asBoolean());
                }
                assertEquals(1, fixture.requests.get(0).path("tools").valueStream().map(tool -> tool.path("function"))
                        .filter(tool -> tool.path("description").asText().contains("Call once with no arguments")).count());
                for (var request : fixture.requests) {
                    var names = toolNames(request);
                    assertEquals(names.size(), names.stream().distinct().count(), "No duplicate native tools");
                }
                assertTrue(messages(fixture.requests.get(1)).contains("current-month.sh"));
                assertTrue(messages(fixture.requests.get(2)).contains("exit code 0"), "Native bundled script must execute");
                assertSchema(fixture.requests.get(0), "items");
                assertSchema(fixture.requests.get(3), "days");
                assertSchema(fixture.requests.get(4), "allPassed");
                assertTrue(messages(fixture.requests.get(3)).contains(REQUEST.additionalInstructions()));
                assertTrue(messages(fixture.requests.get(3)).contains(ALICE.toString()));

                var facade = function(fixture.requests.get(4), "weekly_meal_plan_tools");
                assertEquals("object", facade.path("parameters").path("type").asText());
                assertTrue(facade.path("parameters").path("properties").has("category"));
                assertFalse(toolNames(fixture.requests.get(4)).contains("dailyNutritionTotals"));
                assertTrue(toolNames(fixture.requests.get(5)).containsAll(List.of("dailyNutritionTotals", "nutritionTotalsForDay")));
                assertEquals("object", function(fixture.requests.get(5), "dailyNutritionTotals")
                        .path("parameters").path("type").asText());
                assertTrue(messages(fixture.requests.get(6)).contains("MONDAY"));
                assertTrue(messages(fixture.requests.get(6)).contains("2500"), "Native nutrition tool result must return to the model");
                String expectedPath = switch (profile) {
                    case "ollama" -> "/api/chat";
                    case "openai" -> "/v1/chat/completions";
                    case "azure" -> "/openai/v1/chat/completions";
                    default -> throw new IllegalArgumentException(profile);
                };
                assertTrue(fixture.paths.stream().allMatch(expectedPath::equals), fixture.paths.toString());
            });
        }
    }

    private static void assertSchema(JsonNode request, String property) {
        String prompt = messages(request);
        assertTrue(prompt.contains("\"" + property + "\"") && prompt.contains("\"properties\""), prompt);
    }

    private static String messages(JsonNode request) {
        return request.path("messages").valueStream().map(message -> message.path("content").asText())
                .collect(java.util.stream.Collectors.joining("\n"));
    }

    private static List<String> toolNames(JsonNode request) {
        return request.path("tools").valueStream()
                .map(tool -> tool.path("function").path("name").asText()).toList();
    }

    private static JsonNode function(JsonNode request, String name) {
        return request.path("tools").valueStream().map(tool -> tool.path("function"))
                .filter(tool -> tool.path("name").asText().equals(name)).findFirst().orElseThrow();
    }

}
