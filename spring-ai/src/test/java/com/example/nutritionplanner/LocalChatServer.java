package com.example.nutritionplanner;

import com.sun.net.httpserver.HttpServer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Loopback-only wire fixture; exercises the real OpenAI SDK and Ollama HTTP client. */
public final class LocalChatServer implements AutoCloseable {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private final HttpServer server;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final Queue<Reply> replies = new ArrayDeque<>();
    private final List<Request> requests = new CopyOnWriteArrayList<>();
    private final List<String> errors = new CopyOnWriteArrayList<>();

    public LocalChatServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(executor);
        server.createContext("/", exchange -> {
            try (exchange) {
                var body = JSON.readTree(exchange.getRequestBody());
                requests.add(new Request(exchange.getRequestURI().toString(), body));
                Reply reply;
                synchronized (replies) {
                    reply = replies.poll();
                }
                if (reply == null) {
                    errors.add("Unexpected model request " + requests.size());
                    exchange.sendResponseHeaders(500, -1);
                    return;
                }
                var response = exchange.getRequestURI().getPath().endsWith("/api/chat")
                        ? ollama(reply) : openai(reply);
                var bytes = JSON.writeValueAsString(response).getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
            }
        });
        server.start();
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    public LocalChatServer text(String content) {
        synchronized (replies) {
            replies.add(new Reply(content, List.of()));
        }
        return this;
    }

    public LocalChatServer tool(String name, String arguments) {
        synchronized (replies) {
            replies.add(new Reply("", List.of(new Call(name, arguments))));
        }
        return this;
    }

    public List<Request> requests() {
        return List.copyOf(requests);
    }

    public List<String> errors() {
        return List.copyOf(errors);
    }

    public int remaining() {
        synchronized (replies) {
            return replies.size();
        }
    }

    private Map<String, Object> openai(Reply reply) {
        var message = new java.util.LinkedHashMap<String, Object>();
        message.put("role", "assistant");
        message.put("content", reply.content());
        if (!reply.calls().isEmpty()) {
            var calls = new ArrayList<Map<String, Object>>();
            for (int i = 0; i < reply.calls().size(); i++) {
                var call = reply.calls().get(i);
                calls.add(Map.of("id", "call-" + requests.size() + "-" + i, "type", "function",
                        "function", Map.of("name", call.name(), "arguments", call.arguments())));
            }
            message.put("tool_calls", calls);
        }
        return Map.of("id", "fixture-" + requests.size(), "object", "chat.completion", "created", 1,
                "model", "offline-model",
                "choices", List.of(Map.of("index", 0, "message", message,
                        "finish_reason", reply.calls().isEmpty() ? "stop" : "tool_calls")),
                "usage", Map.of("prompt_tokens", 10, "completion_tokens", 10, "total_tokens", 20));
    }

    private Map<String, Object> ollama(Reply reply) {
        var message = new java.util.LinkedHashMap<String, Object>();
        message.put("role", "assistant");
        message.put("content", reply.content());
        if (!reply.calls().isEmpty()) {
            message.put("tool_calls", reply.calls().stream().map(call -> Map.of("function",
                    Map.of("name", call.name(), "arguments", JSON.readTree(call.arguments())))).toList());
        }
        return Map.of("model", "offline-model", "created_at", "2026-11-03T12:00:00Z",
                "message", message, "done", true, "done_reason", "stop",
                "prompt_eval_count", 10, "eval_count", 10);
    }

    @Override
    public void close() {
        server.stop(0);
        executor.close();
    }

    public record Request(String path, JsonNode body) {}
    private record Call(String name, String arguments) {}
    private record Reply(String content, List<Call> calls) {}
}
