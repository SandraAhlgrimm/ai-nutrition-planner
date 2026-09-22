package com.example.nutritionplanner;

import com.embabel.agent.spi.support.springai.SpringAiLlmService;
import com.embabel.common.ai.model.LlmOptions;
import com.example.NutritionPlannerApplication;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static com.example.nutritionplanner.NutritionTestData.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(30)
class ModelProviderLoopTests {

    @ParameterizedTest
    @ValueSource(strings = {"ollama", "openai", "azure"})
    void realGoalUsesNativeOptionsAndConfiguredModelThroughSkillsAndUnfoldingTools(String profile) throws IOException {
        String model = switch (profile) {
            case "ollama" -> "fixture-qwen:custom";
            case "openai" -> "fixture-openai-model";
            case "azure" -> "fixture-azure-deployment";
            default -> throw new IllegalArgumentException(profile);
        };
        try (var fixture = new ProviderFixture(profile, model)) {
            var runner = new WebApplicationContextRunner()
                    .withInitializer(new ConfigDataApplicationContextInitializer())
                    .withUserConfiguration(NutritionPlannerApplication.class)
                    .withPropertyValues("spring.profiles.active=" + profile, "logging.level.root=WARN",
                            "embabel.agent.platform.action-qos.default.max-attempts=1",
                            "embabel.agent.platform.llm-operations.data-binding.max-attempts=1");
            runner = switch (profile) {
                case "ollama" -> runner.withPropertyValues("OLLAMA_BASE_URL=" + fixture.endpoint(),
                        "OLLAMA_MODEL_NAME=" + model);
                case "openai" -> runner.withPropertyValues("spring.ai.openai.base-url=" + fixture.endpoint() + "/v1",
                        "OPENAI_API_KEY=fixture-openai-key", "OPENAI_MODEL_NAME=" + model);
                case "azure" -> runner.withPropertyValues("AZURE_OPENAI_ENDPOINT=" + fixture.endpoint(),
                        "AZURE_OPENAI_API_KEY=fixture-azure-key", "AZURE_OPENAI_DEPLOYMENT_NAME=" + model);
                default -> throw new IllegalArgumentException(profile);
            };
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
                assertEquals(List.of("current_month"), toolNames(fixture.requests.get(0)).stream()
                        .filter(name -> name.equals("current_month")).toList());
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
                assertTrue(messages(fixture.requests.get(6)).contains("1000"), "Native nutrition tool result must return to the model");
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

    private static final class ProviderFixture implements AutoCloseable {

        private final JsonMapper mapper = JsonMapper.builder()
                .changeDefaultPropertyInclusion(inclusion -> inclusion.withValueInclusion(JsonInclude.Include.NON_NULL))
                .build();
        private final HttpServer server;
        private final String profile;
        private final String model;
        private final List<JsonNode> requests = new CopyOnWriteArrayList<>();
        private final List<String> paths = new CopyOnWriteArrayList<>();

        private ProviderFixture(String profile, String model) throws IOException {
            this.profile = profile;
            this.model = model;
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", this::respond);
            server.start();
        }

        private String endpoint() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        private void respond(HttpExchange exchange) throws IOException {
            var request = mapper.readTree(exchange.getRequestBody().readAllBytes());
            requests.add(request);
            paths.add(exchange.getRequestURI().getPath());
            int step = requests.size() - 1;
            String response = switch (step) {
                case 0 -> toolCall("current_month", Map.of());
                case 1 -> toolCall(request.path("tools").valueStream().map(tool -> tool.path("function"))
                        .filter(tool -> tool.path("description").asText().contains("Execute the current-month.sh"))
                        .findFirst().orElseThrow().path("name").asText(), Map.of());
                case 2 -> text(SEASONAL);
                case 3 -> text(plan("Fixture lentils"));
                case 4 -> toolCall("weekly_meal_plan_tools", Map.of("category", "nutrition"));
                case 5 -> toolCall("dailyNutritionTotals", Map.of());
                case 6 -> text(PASS);
                default -> "{\"error\":{\"message\":\"Unexpected extra model request\"}}";
            };
            var bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(step <= 6 ? 200 : 400, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        }

        private String text(Object result) {
            return response(Map.of("role", "assistant", "content", mapper.writeValueAsString(result)), false);
        }

        private String toolCall(String name, Map<String, Object> arguments) {
            var function = Map.of("name", name,
                    "arguments", profile.equals("ollama") ? arguments : mapper.writeValueAsString(arguments));
            var call = Map.of("id", "call_" + requests.size(), "type", "function", "function", function);
            return response(Map.of("role", "assistant", "content", "", "tool_calls", List.of(call)), true);
        }

        private String response(Map<String, Object> message, boolean toolCall) {
            if (profile.equals("ollama")) {
                return mapper.writeValueAsString(Map.of("model", model, "created_at", "2026-09-22T12:00:00Z",
                        "message", message, "done", true, "done_reason", "stop", "prompt_eval_count", 10, "eval_count", 5));
            }
            return mapper.writeValueAsString(Map.of("id", "fixture", "object", "chat.completion", "created", 0,
                    "model", model, "choices", List.of(Map.of("index", 0, "message", message,
                            "finish_reason", toolCall ? "tool_calls" : "stop")),
                    "usage", Map.of("prompt_tokens", 10, "completion_tokens", 5, "total_tokens", 15)));
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }
}
