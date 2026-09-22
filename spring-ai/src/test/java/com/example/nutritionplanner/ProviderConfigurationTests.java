package com.example.nutritionplanner;

import com.example.NutritionPlannerApplication;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.model.Model;
import org.springframework.ai.model.openai.autoconfigure.OpenAiCommonProperties;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.micrometer.metrics.autoconfigure.export.otlp.OtlpMetricsProperties;
import org.springframework.boot.micrometer.tracing.opentelemetry.autoconfigure.otlp.OtlpTracingProperties;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;

import java.io.IOException;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class ProviderConfigurationTests {

    @ParameterizedTest
    @CsvSource({"openai,openai,gpt-4o", "azure,openai,test-deployment", "ollama,ollama,qwen2.5"})
    void selectedProfileCreatesOnlyItsChatProviderWithoutNetworkCalls(String profile, String provider, String modelName) {
        new WebApplicationContextRunner().withInitializer(new ConfigDataApplicationContextInitializer())
                .withUserConfiguration(NutritionPlannerApplication.class)
                .withPropertyValues("spring.profiles.active=" + profile, "spring.ai.mcp.server.enabled=false",
                        "OPENAI_API_KEY=offline-test-key", "OPENAI_MODEL_NAME=gpt-4o",
                        "AZURE_OPENAI_API_KEY=offline-test-key",
                        "AZURE_OPENAI_ENDPOINT=https://offline-example.openai.azure.com/",
                        "AZURE_OPENAI_DEPLOYMENT_NAME=test-deployment",
                        "OLLAMA_BASE_URL=http://127.0.0.1:1", "OLLAMA_MODEL_NAME=qwen2.5")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(ChatModel.class).doesNotHaveBean(EmbeddingModel.class);
                    assertThat(context.getBeansOfType(Model.class)).hasSize(1);
                    var model = context.getBean(ChatModel.class);
                    assertThat(model.getOptions().getModel()).isEqualTo(modelName);
                    assertThat(context.getEnvironment().getProperty("spring.ai.model.chat")).isEqualTo(provider);
                    assertThat(context.getEnvironment().getProperty("spring.ai.model.embedding")).isEqualTo("none");
                    assertThat(context.getEnvironment().getProperty("management.otlp.metrics.export.enabled")).isEqualTo("false");
                    assertThat(context.getEnvironment().getProperty("management.tracing.export.enabled")).isEqualTo("false");
                    if (profile.equals("ollama")) {
                        assertThat(model).isInstanceOf(OllamaChatModel.class);
                        assertThat(context).doesNotHaveBean(OpenAiChatModel.class);
                    } else {
                        assertThat(model).isInstanceOf(OpenAiChatModel.class);
                        assertThat(context).doesNotHaveBean(OllamaChatModel.class);
                        if (profile.equals("azure")) {
                            var properties = context.getBean(OpenAiCommonProperties.class);
                            assertThat(properties.isMicrosoftFoundry()).isTrue();
                            assertThat(properties.getMicrosoftDeploymentName()).isEqualTo("test-deployment");
                            assertThat(properties.getBaseUrl()).isEqualTo("https://offline-example.openai.azure.com/");
                        }
                    }
                });
    }

    @Test
    void observabilityProfileUsesBoot4TracingAndEnabledMetricsExportWithoutStartingExporters() throws IOException {
        var environment = new MockEnvironment()
                .withProperty("OTEL_EXPORTER_OTLP_TRACES_ENDPOINT", "http://localhost:4318/v1/traces")
                .withProperty("OTEL_EXPORTER_OTLP_METRICS_ENDPOINT", "http://localhost:4318/v1/metrics");
        new YamlPropertySourceLoader().load("observability", new ClassPathResource("application-observability.yaml"))
                .forEach(environment.getPropertySources()::addLast);
        var binder = Binder.get(environment);
        var traces = binder.bind("management.opentelemetry.tracing.export.otlp", OtlpTracingProperties.class).get();
        var metrics = binder.bind("management.otlp.metrics.export", OtlpMetricsProperties.class).get();
        assertThat(traces.getEndpoint()).isEqualTo("http://localhost:4318/v1/traces");
        assertThat(metrics.getUrl()).isEqualTo("http://localhost:4318/v1/metrics");
        assertThat(metrics.getStep()).isEqualTo(Duration.ofSeconds(5));
        assertThat(metrics.isEnabled()).isTrue();
        assertThat(environment.getProperty("management.tracing.export.enabled", Boolean.class)).isTrue();
    }
}
