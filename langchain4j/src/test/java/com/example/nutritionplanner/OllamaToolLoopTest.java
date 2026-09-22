package com.example.nutritionplanner;

import com.example.NutritionPlannerApplication;
import com.sun.net.httpserver.HttpServer;
import dev.langchain4j.agentic.agent.AgentInvocationException;
import dev.langchain4j.agentic.observability.AgentMonitor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static com.example.nutritionplanner.PlanFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OllamaToolLoopTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Test
    void realOllamaClientContinuesAfterToolOnlyResponseAndParsesTheFinalAudit() throws Exception {
        try (var ollama = new OllamaFixture(auditResponse(JSON.writeValueAsString(PASSED)), true)) {
            ollama.application().run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context.getBean(NutritionPlannerAgent.class).createNutritionPlan("alice", REQUEST))
                        .isEqualTo(plan(1));
                assertThat(context.getBean(AgentMonitor.class).successfulExecutions()).hasSize(1);
                ollama.assertToolRoundTrip();
            });
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void emptyFinalOllamaContentReproducesNullOutputParsingFailureNotImmediateToolReturn(
            boolean callsTools) throws Exception {
        try (var ollama = new OllamaFixture(auditResponse(""), callsTools)) {
            ollama.application().run(context -> {
                assertThat(context).hasNotFailed();
                assertThatThrownBy(() -> context.getBean(NutritionPlannerAgent.class)
                        .createNutritionPlan("alice", REQUEST))
                        .isInstanceOf(AgentInvocationException.class)
                        .hasStackTraceContaining("OutputParsingException")
                        .hasStackTraceContaining("Failed to parse null (base64: null)");
                assertThat(context.getBean(AgentMonitor.class).successfulExecutions()).isEmpty();
                assertThat(context.getBean(AgentMonitor.class).ongoingExecutions()).isEmpty();
                if (callsTools) {
                    ollama.assertToolRoundTrip();
                } else {
                    assertThat(ollama.requests).hasSize(3);
                }
            });
        }
    }

    private static String auditResponse(String content) {
        return JSON.writeValueAsString(Map.of(
                "model", "qwen2.5",
                "message", Map.of("role", "assistant", "content", content),
                "done", true, "done_reason", "stop", "prompt_eval_count", 500, "eval_count", 40));
    }

    private static final class OllamaFixture implements AutoCloseable {

        private final HttpServer server;
        private final List<JsonNode> requests = new CopyOnWriteArrayList<>();
        private final List<String> responses;

        private OllamaFixture(String finalResponse, boolean callsTools) throws Exception {
            responses = new ArrayList<>(List.of(
                    auditResponse("{\"items\":[{\"name\":\"carrot\",\"quantity\":\"200\",\"unit\":\"g\"}]}"),
                    auditResponse(JSON.writeValueAsString(plan(1)))));
            if (callsTools) {
                responses.add("""
                    {"model":"qwen2.5","message":{"role":"assistant","content":"","tool_calls":[
                      {"function":{"name":"dailyNutritionTotals","arguments":{}}},
                      {"function":{"name":"nutritionTotalsForDay","arguments":{"day":"MONDAY"}}},
                      {"function":{"name":"totalMealCount","arguments":{}}}
                    ]},"done":true,"done_reason":"stop","prompt_eval_count":500,"eval_count":40}
                    """);
            }
            responses.add(finalResponse);
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/api/chat", exchange -> {
                requests.add(JSON.readTree(exchange.getRequestBody().readAllBytes()));
                int index = requests.size() - 1;
                int status = index < responses.size() ? 200 : 500;
                byte[] body = (index < responses.size() ? responses.get(index)
                        : "{\"error\":\"Unexpected extra model call\"}").getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(status, body.length);
                try (var output = exchange.getResponseBody()) {
                    output.write(body);
                }
            });
            server.start();
        }

        private WebApplicationContextRunner application() {
            return new WebApplicationContextRunner()
                    .withInitializer(new ConfigDataApplicationContextInitializer())
                    .withUserConfiguration(NutritionPlannerApplication.class)
                    .withPropertyValues("spring.profiles.active=ollama",
                            "OLLAMA_BASE_URL=http://127.0.0.1:" + server.getAddress().getPort(),
                            "OLLAMA_MODEL_NAME=qwen2.5",
                            "langchain4j.ollama.chat-model.max-retries=0");
        }

        private void assertToolRoundTrip() {
            assertThat(requests).hasSize(4);
            var firstAudit = requests.get(2);
            var continuation = requests.get(3);
            assertThat(firstAudit.get("tools")).hasSize(3);
            assertThat(continuation.get("tools")).isEqualTo(firstAudit.get("tools"));
            assertThat(continuation.get("messages").get(0)).isEqualTo(firstAudit.get("messages").get(0));
            assertThat(continuation.get("messages").get(1)).isEqualTo(firstAudit.get("messages").get(1));
            var messages = continuation.get("messages");
            assertThat(messages).hasSize(6);
            assertThat(messages.get(2).get("tool_calls")).hasSize(3);
            assertThat(messages.get(3).get("role").asString()).isEqualTo("tool");
            assertThat(JSON.readTree(messages.get(3).get("content").asString()).get("MONDAY").get("calories").asInt())
                    .isEqualTo(100);
            assertThat(JSON.readTree(messages.get(4).get("content").asString()).get("calories").asInt()).isEqualTo(100);
            assertThat(messages.get(5).get("content").asString()).isEqualTo("2");
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }
}
