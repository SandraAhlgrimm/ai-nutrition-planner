package com.example.nutritionplanner;

import com.example.NutritionPlannerApplication;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

final class LocalModelFixture implements AutoCloseable {

    record Reply(int status, String body) {}

    final JsonMapper mapper = JsonMapper.builder()
            .changeDefaultPropertyInclusion(inclusion -> inclusion.withValueInclusion(JsonInclude.Include.NON_NULL))
            .build();
    final List<JsonNode> requests = new CopyOnWriteArrayList<>();
    final List<String> paths = new CopyOnWriteArrayList<>();
    final String profile;
    final String model;
    private final List<Throwable> failures = new CopyOnWriteArrayList<>();
    private final HttpServer server;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private volatile Function<JsonNode, Reply> responder = request -> error();

    LocalModelFixture(String profile, String model) throws IOException {
        this.profile = profile;
        this.model = model;
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(executor);
        server.createContext("/", this::respond);
        server.start();
    }

    String endpoint() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    WebApplicationContextRunner runner(boolean patterns) {
        var runner = new WebApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withUserConfiguration(NutritionPlannerApplication.class)
                .withPropertyValues("spring.profiles.active=offline," + profile + (patterns ? ",patterns" : ""),
                        "logging.level.com.embabel=WARN", "logging.level.Embabel=WARN");
        return switch (profile) {
            case "ollama" -> runner.withPropertyValues("OLLAMA_BASE_URL=" + endpoint(), "OLLAMA_MODEL_NAME=" + model);
            case "openai" -> runner.withPropertyValues("spring.ai.openai.base-url=" + endpoint() + "/v1",
                    "OPENAI_API_KEY=fixture-openai-key", "OPENAI_MODEL_NAME=" + model);
            case "azure" -> runner.withPropertyValues("AZURE_OPENAI_ENDPOINT=" + endpoint(),
                    "AZURE_OPENAI_API_KEY=fixture-azure-key", "AZURE_OPENAI_DEPLOYMENT_NAME=" + model);
            default -> throw new IllegalArgumentException(profile);
        };
    }

    void respondWith(Function<JsonNode, Reply> responder) {
        requests.clear();
        paths.clear();
        this.responder = responder;
    }

    Reply text(Object value) {
        return raw(mapper.writeValueAsString(value));
    }

    Reply raw(String text) {
        return response(Map.of("role", "assistant", "content", text), false);
    }

    Reply toolCall(String name, Map<String, Object> arguments) {
        return toolCalls(List.of(tool(name, arguments)));
    }

    Map<String, Object> tool(String name, Map<String, Object> arguments) {
        return Map.of("id", "call_" + name + "_" + requests.size(), "type", "function", "function",
                Map.of("name", name, "arguments", profile.equals("ollama") ? arguments : mapper.writeValueAsString(arguments)));
    }

    Reply toolCalls(List<Map<String, Object>> calls) {
        return response(Map.of("role", "assistant", "content", "", "tool_calls", calls), true);
    }

    Reply error() {
        return new Reply(400, "{\"error\":{\"message\":\"Fixture model unavailable\",\"type\":\"invalid_request_error\"}}");
    }

    void assertWireFormat() {
        String path = switch (profile) {
            case "ollama" -> "/api/chat";
            case "openai" -> "/v1/chat/completions";
            case "azure" -> "/openai/v1/chat/completions";
            default -> throw new IllegalArgumentException(profile);
        };
        assertTrue(paths.stream().allMatch(path::equals), paths.toString());
        for (var request : requests) {
            assertEquals(model, request.path("model").asText());
            assertFalse(request.path("stream").asBoolean());
        }
    }

    static String messages(JsonNode request) {
        return request.path("messages").valueStream().map(message -> message.path("content").asText())
                .collect(java.util.stream.Collectors.joining("\n"));
    }

    static String functionName(JsonNode request, String suffix) {
        return request.path("tools").valueStream().map(tool -> tool.path("function").path("name").asText())
                .filter(name -> name.endsWith(suffix)).findFirst().orElseThrow();
    }

    private Reply response(Map<String, Object> message, boolean toolCall) {
        if (profile.equals("ollama")) {
            return new Reply(200, mapper.writeValueAsString(Map.of("model", model, "created_at", "2026-11-03T12:00:00Z",
                    "message", message, "done", true, "done_reason", "stop", "prompt_eval_count", 10, "eval_count", 5)));
        }
        return new Reply(200, mapper.writeValueAsString(Map.of("id", "fixture", "object", "chat.completion", "created", 0,
                "model", model, "choices", List.of(Map.of("index", 0, "message", message,
                        "finish_reason", toolCall ? "tool_calls" : "stop")),
                "usage", Map.of("prompt_tokens", 10, "completion_tokens", 5, "total_tokens", 15))));
    }

    private void respond(HttpExchange exchange) throws IOException {
        Reply reply;
        try {
            var request = mapper.readTree(exchange.getRequestBody().readAllBytes());
            requests.add(request);
            paths.add(exchange.getRequestURI().getPath());
            reply = responder.apply(request);
        } catch (RuntimeException | AssertionError failure) {
            failures.add(failure);
            reply = error();
        }
        var bytes = reply.body().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(reply.status(), bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    @Override
    public void close() {
        server.stop(0);
        executor.close();
        assertTrue(failures.isEmpty(), () -> "Fixture handler failed: " + failures);
    }
}
