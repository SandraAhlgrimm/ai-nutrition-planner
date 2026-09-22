package com.example.nutritionplanner;

import com.embabel.agent.config.models.ollama.OllamaOptionsConverter;
import com.embabel.agent.openai.StandardOpenAiOptionsConverter;
import com.embabel.agent.spi.support.springai.SpringAiLlmService;
import com.embabel.common.ai.model.OptionsConverter;
import org.jspecify.annotations.Nullable;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.model.openai.autoconfigure.OpenAiCommonProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

import java.net.URI;
import java.util.stream.Stream;

@Configuration(proxyBeanMethods = false)
@Profile({"openai", "azure", "ollama"})
class ModelConfiguration {

    @Bean
    SpringAiLlmService nutritionPlannerLlm(ChatModel chatModel, Environment environment,
                                         @Value("${nutrition-planner.model}") String model,
                                         @Value("${nutrition-planner.provider}") String provider) {
        long selected = Stream.of("openai", "azure", "ollama")
                .filter(profile -> environment.acceptsProfiles(Profiles.of(profile))).count();
        if (selected != 1 || model.isBlank()) {
            throw new IllegalArgumentException("Select exactly one model profile (openai, azure, ollama) and a nonempty model");
        }
        if (!environment.acceptsProfiles(Profiles.of("ollama"))
                && environment.getRequiredProperty("spring.ai.openai.api-key").isBlank()) {
            throw new IllegalArgumentException("The selected OpenAI or Azure profile requires a nonempty API key");
        }
        OptionsConverter optionsConverter = environment.acceptsProfiles(Profiles.of("ollama"))
                ? new OllamaOptionsConverter() : StandardOpenAiOptionsConverter.INSTANCE;
        return new SpringAiLlmService(model, provider, chatModel, optionsConverter);
    }

    @Bean
    @Profile("azure")
    static BeanPostProcessor azureV1Endpoint() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessBeforeInitialization(Object bean, String beanName) {
                if (bean instanceof OpenAiCommonProperties properties) {
                    properties.setBaseUrl(azureV1BaseUrl(properties.getBaseUrl()));
                }
                return bean;
            }
        };
    }

    static String azureV1BaseUrl(@Nullable String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            throw new IllegalArgumentException("AZURE_OPENAI_ENDPOINT is required");
        }
        var uri = URI.create(endpoint);
        if (!("https".equals(uri.getScheme()) || "http".equals(uri.getScheme())) || uri.getHost() == null
                || uri.getQuery() != null || uri.getFragment() != null || uri.getUserInfo() != null) {
            throw new IllegalArgumentException("AZURE_OPENAI_ENDPOINT must be an HTTP(S) resource endpoint");
        }
        var base = endpoint.replaceAll("/+$", "");
        if (base.endsWith("/openai/v1")) {
            return base;
        }
        if (uri.getPath() != null && !uri.getPath().isEmpty() && !"/".equals(uri.getPath())) {
            throw new IllegalArgumentException("AZURE_OPENAI_ENDPOINT must be a resource root or /openai/v1 endpoint");
        }
        return base + "/openai/v1";
    }
}
