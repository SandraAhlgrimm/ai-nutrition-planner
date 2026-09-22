package com.example.nutritionplanner;

import com.example.NutritionPlannerApplication;
import com.sun.net.httpserver.HttpServer;
import com.azure.core.http.HttpClient;
import com.azure.core.http.HttpHeaderName;
import com.azure.core.http.HttpHeaders;
import com.azure.core.http.HttpResponse;
import com.azure.core.util.BinaryData;
import dev.langchain4j.micrometer.metrics.listeners.MicrometerMetricsChatModelListener;
import dev.langchain4j.model.azure.AzureOpenAiChatModel;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProviderConfigurationTest {

    @ParameterizedTest
    @CsvSource({"openai,OpenAiChatModel,custom-openai", "ollama,OllamaChatModel,custom-ollama",
            "azure,AzureOpenAiChatModel,custom-azure"})
    void eachProfileStartsOnlyItsProvider(
            String profile, String modelClass, String modelName) throws Exception {
        var requestBody = new AtomicReference<String>();
        var requestPath = new AtomicReference<String>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requestPath.set(exchange.getRequestURI().getPath());
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            String response = profile.equals("ollama")
                    ? """
                      {"model":"custom-ollama","message":{"role":"assistant","content":"ready"},
                       "done":true,"done_reason":"stop","prompt_eval_count":10,"eval_count":5}
                      """
                    : """
                      {"id":"stub","model":"%s","choices":[{"index":0,
                       "message":{"role":"assistant","content":"ready"},"finish_reason":"stop"}],
                       "usage":{"prompt_tokens":10,"completion_tokens":5,"total_tokens":15}}
                      """.formatted(modelName);
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (var body = exchange.getResponseBody()) {
                body.write(bytes);
            }
        });
        server.start();
        String endpoint = "http://127.0.0.1:" + server.getAddress().getPort();
        try {
            new WebApplicationContextRunner()
                    .withInitializer(new ConfigDataApplicationContextInitializer())
                    .withUserConfiguration(NutritionPlannerApplication.class)
                    .withPropertyValues("spring.profiles.active=" + profile,
                            "OPENAI_API_KEY=local-stub-key", "OPENAI_MODEL_NAME=custom-openai",
                            "langchain4j.open-ai.chat-model.base-url=" + endpoint + "/v1",
                            "OLLAMA_BASE_URL=" + endpoint, "OLLAMA_MODEL_NAME=custom-ollama",
                            "AZURE_OPENAI_API_KEY=local-stub-key", "AZURE_OPENAI_ENDPOINT=https://example.invalid",
                            "AZURE_OPENAI_DEPLOYMENT_NAME=custom-azure")
                    .run(context -> {
                        assertThat(context).hasNotFailed().hasSingleBean(ChatModel.class)
                                .hasSingleBean(Agents.NutritionPlanner.class)
                                .doesNotHaveBean(StreamingChatModel.class).doesNotHaveBean(EmbeddingModel.class);
                        var model = context.getBean(ChatModel.class);
                        assertThat(model.getClass().getSimpleName()).isEqualTo(modelClass);
                        assertThat(model.defaultRequestParameters().modelName()).isEqualTo(modelName);
                        assertThat(model.listeners()).anyMatch(MicrometerMetricsChatModelListener.class::isInstance);
                        if (!profile.equals("azure")) {
                            assertThat(model.chat("Reply with ready.")).isEqualTo("ready");
                            var body = JsonMapper.builder().build().readTree(requestBody.get());
                            assertThat(body.get("model").asString()).isEqualTo(modelName);
                            assertThat(requestPath.get()).endsWith(profile.equals("ollama") ? "/api/chat" : "/chat/completions");
                            assertTokenMetrics(context.getBean(MeterRegistry.class));
                        }
                        assertThat(context.getBean(UserProfileProperties.class).getUserProfile("alice"))
                                .isEqualTo(PlanFixtures.ALICE);
                        assertThat(context.getEnvironment().getProperty("spring.threads.virtual.enabled", Boolean.class))
                                .isTrue();
                    });
        } finally {
            server.stop(0);
        }
    }

    @Test
    void azureSerializesThroughTheRealSdkWithAnInMemoryTransportAndHttpsEndpoint() {
        var requests = new AtomicReference<com.azure.core.http.HttpRequest>();
        var data = BinaryData.fromString("""
                {"id":"stub","model":"custom-azure","choices":[{"index":0,
                 "message":{"role":"assistant","content":"ready"},"finish_reason":"stop"}],
                 "usage":{"prompt_tokens":10,"completion_tokens":5,"total_tokens":15}}
                """);
        HttpClient transport = request -> {
            requests.set(request);
            var response = mock(HttpResponse.class);
            when(response.getRequest()).thenReturn(request);
            when(response.getStatusCode()).thenReturn(200);
            when(response.getHeaders()).thenReturn(new HttpHeaders().set(HttpHeaderName.CONTENT_TYPE, "application/json"));
            when(response.getHeaderValue(HttpHeaderName.CONTENT_TYPE)).thenReturn("application/json");
            when(response.getBodyAsBinaryData()).thenReturn(data);
            when(response.getBodyAsByteArray()).thenReturn(Mono.just(data.toBytes()));
            when(response.getBodyAsString()).thenReturn(Mono.just(data.toString()));
            when(response.getBody()).thenReturn(data.toFluxByteBuffer());
            when(response.buffer()).thenReturn(response);
            return Mono.just(response);
        };
        var registry = new SimpleMeterRegistry();
        try {
            var model = AzureOpenAiChatModel.builder()
                    .apiKey("local-stub-key").endpoint("https://example.invalid")
                    .deploymentName("custom-azure").httpClientProvider(() -> transport).maxRetries(0)
                    .listeners(List.of(new MicrometerMetricsChatModelListener(registry))).build();

            assertThat(model.chat("Reply with ready.")).isEqualTo("ready");
            assertThat(requests.get().getUrl().getPath()).contains("/deployments/custom-azure/chat/completions");
            assertThat(requests.get().getBodyAsBinaryData().toString()).contains("Reply with ready.");
            assertTokenMetrics(registry);
        } finally {
            registry.close();
        }
    }

    @Test
    void observabilityProfileEnablesCurrentExportPropertiesWithoutActivatingOpenAi() {
        new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withPropertyValues("spring.profiles.active=ollama,observability",
                        "OTEL_EXPORTER_OTLP_TRACES_ENDPOINT=http://localhost:4318/v1/traces",
                        "OTEL_EXPORTER_OTLP_METRICS_ENDPOINT=http://localhost:4318/v1/metrics")
                .run(context -> {
                    var environment = context.getEnvironment();
                    assertThat(environment.getProperty("management.otlp.metrics.export.enabled", Boolean.class)).isTrue();
                    assertThat(environment.getProperty("management.tracing.export.otlp.enabled", Boolean.class)).isTrue();
                    assertThat(environment.getProperty("management.opentelemetry.tracing.export.otlp.endpoint"))
                            .isEqualTo("http://localhost:4318/v1/traces");
                    assertThat(environment.getProperty("management.otlp.metrics.export.url"))
                            .isEqualTo("http://localhost:4318/v1/metrics");
                    assertThat(environment.containsProperty("langchain4j.open-ai.chat-model.api-key")).isFalse();
                    assertThat(environment.containsProperty("langchain4j.ollama.chat-model.base-url")).isTrue();
                });
    }

    private static void assertTokenMetrics(MeterRegistry registry) {
        assertThat(registry.get("gen_ai.client.token.usage").tag("gen_ai.token.type", "input").summary().totalAmount())
                .isEqualTo(10);
        assertThat(registry.get("gen_ai.client.token.usage").tag("gen_ai.token.type", "output").summary().totalAmount())
                .isEqualTo(5);
    }
}
