package com.example.nutritionplanner;

import com.embabel.agent.core.AgentPlatform;
import com.embabel.agent.spi.support.springai.SpringAiLlmService;
import com.embabel.common.ai.model.ModelSelectionCriteria;
import com.embabel.common.ai.model.ModelProvider;
import com.example.NutritionPlannerApplication;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.model.openai.autoconfigure.OpenAiCommonProperties;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.ui.ExtendedModelMap;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class ModelConfigurationTests {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(NutritionPlannerApplication.class)
            .withPropertyValues("logging.level.com.embabel=WARN", "logging.level.Embabel=WARN");

    @ParameterizedTest
    @ValueSource(strings = {"openai", "azure", "ollama"})
    void profilesStartIndependentlyWithoutModelTraffic(String profile) throws IOException {
        var calls = new AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            calls.incrementAndGet();
            exchange.sendResponseHeaders(500, -1);
            exchange.close();
        });
        server.start();
        try {
            String endpoint = "http://127.0.0.1:" + server.getAddress().getPort();
            selectedProfile(profile, endpoint).run(context -> {
                assertThat(context).hasNotFailed().hasSingleBean(ChatModel.class).hasSingleBean(SpringAiLlmService.class);
                var service = context.getBean(ModelProvider.class).getLlm(ModelSelectionCriteria.getPlatformDefault());
                assertEquals(profile.equals("ollama") ? "qwen2.5" : "gpt-4o", service.getName());
                assertEquals(profile.equals("ollama") ? OllamaChatModel.class : OpenAiChatModel.class,
                        context.getBean(ChatModel.class).getClass());
                assertFalse(context.getBean(AgentPlatform.class).agents().isEmpty(), "Real annotation scanning must register the graph");
                var loginModel = new ExtendedModelMap();
                assertEquals("login", context.getBean(NutritionPlannerUiController.class).login(loginModel));
                assertTrue(loginModel.get("aiModel").toString().contains(service.getName()));
                if (profile.equals("azure")) {
                    assertEquals(endpoint + "/openai/v1", context.getBean(OpenAiCommonProperties.class).getBaseUrl());
                }
                assertEquals(0, calls.get(), "Model discovery, pulls and live requests must not occur at startup");
            });
            assertEquals(0, calls.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void azureRootEndpointProducesV1RequestWithDeploymentAndApiKey() throws IOException {
        var path = new AtomicReference<String>();
        var key = new AtomicReference<String>();
        var body = new AtomicReference<String>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            path.set(exchange.getRequestURI().toString());
            key.set(exchange.getRequestHeaders().getFirst("api-key"));
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            var response = """
                    {"id":"test","object":"chat.completion","created":0,"model":"my-deployment",
                     "choices":[{"index":0,"message":{"role":"assistant","content":"ok"},"finish_reason":"stop"}],
                     "usage":{"prompt_tokens":1,"completion_tokens":1,"total_tokens":2}}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            selectedProfile("azure", "http://127.0.0.1:" + server.getAddress().getPort() + "/")
                    .withPropertyValues("AZURE_OPENAI_DEPLOYMENT_NAME=my-deployment")
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        assertNull(path.get(), "No startup probe should be sent");
                        assertEquals("ok", context.getBean(ChatModel.class).call("Say ok"));
                        assertEquals("/openai/v1/chat/completions", path.get());
                        assertEquals("test-azure-key", key.get());
                        assertTrue(body.get().contains("my-deployment"), body.get());
                    });
        } finally {
            server.stop(0);
        }
    }

    @Test
    void conflictingProvidersAndMissingKeysFailStartupExplicitly() {
        runner.withPropertyValues("spring.profiles.active=openai,ollama", "OPENAI_API_KEY=test-key")
                .run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues("spring.profiles.active=openai", "OPENAI_API_KEY=")
                .run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues("spring.profiles.active=azure", "AZURE_OPENAI_API_KEY=", "AZURE_OPENAI_ENDPOINT=https://example.openai.azure.com")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void endpointNormalizationRejectsLegacyQueryPathsAndRetainsV1Endpoints() {
        assertEquals("https://example.openai.azure.com/openai/v1",
                ModelConfiguration.azureV1BaseUrl("https://example.openai.azure.com/"));
        assertEquals("https://example.openai.azure.com/openai/v1",
                ModelConfiguration.azureV1BaseUrl("https://example.openai.azure.com/openai/v1/"));
        assertThrows(IllegalArgumentException.class, () -> ModelConfiguration.azureV1BaseUrl(
                "https://example.openai.azure.com/openai/deployments/gpt/chat/completions?api-version=2024-10-21"));
        assertThrows(IllegalArgumentException.class, () -> ModelConfiguration.azureV1BaseUrl("not-a-url"));
    }

    @Test
    void observabilityProfileExportsTracesAndMetricsToConfiguredLocalCollector() throws IOException {
        var traces = new CountDownLatch(1);
        var metrics = new CountDownLatch(1);
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            var body = exchange.getRequestBody().readAllBytes();
            if (body.length > 0 && exchange.getRequestURI().getPath().equals("/v1/traces")) {
                traces.countDown();
            }
            if (body.length > 0 && exchange.getRequestURI().getPath().equals("/v1/metrics")) {
                metrics.countDown();
            }
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();
        try {
            String endpoint = "http://127.0.0.1:" + server.getAddress().getPort();
            selectedProfile("openai", endpoint).withPropertyValues(
                    "spring.profiles.active=openai,observability",
                    "OTEL_EXPORTER_OTLP_TRACES_ENDPOINT=" + endpoint + "/v1/traces",
                    "OTEL_EXPORTER_OTLP_METRICS_ENDPOINT=" + endpoint + "/v1/metrics",
                    "management.opentelemetry.tracing.export.schedule-delay=50ms",
                    "management.otlp.metrics.export.step=100ms").run(context -> {
                assertThat(context).hasNotFailed().hasSingleBean(SpanExporter.class);
                Observation.createNotStarted("nutrition.test", context.getBean(ObservationRegistry.class)).observe(() -> {});
                context.getBean(MeterRegistry.class).counter("nutrition.test").increment();
                assertTrue(traces.await(5, TimeUnit.SECONDS), "OTLP trace export must be active");
                assertTrue(metrics.await(5, TimeUnit.SECONDS), "OTLP metric export must be active");
            });
        } finally {
            server.stop(0);
        }
    }

    private WebApplicationContextRunner selectedProfile(String profile, String endpoint) {
        var selected = runner.withPropertyValues("spring.profiles.active=" + profile);
        return switch (profile) {
            case "openai" -> selected.withPropertyValues("OPENAI_API_KEY=test-openai-key", "spring.ai.openai.base-url=" + endpoint);
            case "azure" -> selected.withPropertyValues("AZURE_OPENAI_API_KEY=test-azure-key", "AZURE_OPENAI_ENDPOINT=" + endpoint);
            case "ollama" -> selected.withPropertyValues("OLLAMA_BASE_URL=" + endpoint);
            default -> throw new IllegalArgumentException("Unknown test profile: " + profile);
        };
    }
}
