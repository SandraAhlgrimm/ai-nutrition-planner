package com.example.nutritionplanner.patterns;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.model.ollama.OllamaDecisionModel;
import dev.langchain4j.model.openai.OpenAiDecisionModel;
import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

import java.time.Duration;
import java.util.List;

@Configuration(proxyBeanMethods = false)
@Profile("patterns")
class PatternConfiguration {

    @Bean
    PatternLab patternLab(ChatModel chatModel, Environment environment) {
        var providers = List.of("openai", "ollama", "azure").stream()
                .filter(name -> environment.acceptsProfiles(Profiles.of(name))).toList();
        if (providers.size() != 1) {
            throw new IllegalStateException("The patterns profile requires exactly one of openai, ollama or azure.");
        }
        String provider = providers.getFirst();
        String modelName = environment.getProperty("nutrition-planner.patterns.decision-model-name", "").strip();
        String requirement = switch (provider) {
            case "ollama" -> "Set PATTERNS_DECISION_MODEL_NAME to a locally installed decision model (e.g. nimble); "
                    + "Ollama >=0.35.0 with /v1/systemone is required. Configuration is not a health check.";
            case "openai" -> "Set PATTERNS_DECISION_MODEL_NAME to a model available to your existing OpenAI account "
                    + "that supports /v1/decisions. Configuration is not a health check.";
            case "azure" -> "No native Azure DecisionModel is configured in this lab. Use the parallel mapper, "
                    + "or select openai/ollama with a decision-capable model.";
            default -> throw new IllegalStateException("Unexpected provider: " + provider);
        };
        @Nullable DecisionModel decision = modelName.isEmpty() ? null : switch (provider) {
            case "ollama" -> OllamaDecisionModel.builder()
                    .baseUrl(environment.getRequiredProperty("langchain4j.ollama.chat-model.base-url"))
                    .modelName(modelName).maxRetries(0).timeout(Duration.ofSeconds(45)).build();
            case "openai" -> OpenAiDecisionModel.builder()
                    .apiKey(environment.getRequiredProperty("langchain4j.open-ai.chat-model.api-key"))
                    .baseUrl(environment.getProperty("langchain4j.open-ai.chat-model.base-url", "https://api.openai.com/v1"))
                    .organizationId(environment.getProperty("langchain4j.open-ai.chat-model.organization-id"))
                    .projectId(environment.getProperty("langchain4j.open-ai.chat-model.project-id"))
                    .modelName(modelName).maxRetries(0).timeout(Duration.ofSeconds(45)).build();
            case "azure" -> null;
            default -> throw new IllegalStateException("Unexpected provider: " + provider);
        };
        return new PatternLab(chatModel, decision, provider, requirement);
    }
}
