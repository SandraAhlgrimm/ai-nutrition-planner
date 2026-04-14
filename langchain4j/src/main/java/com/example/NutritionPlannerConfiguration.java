package com.example;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.ollama.OllamaStreamingChatModel;
import dev.langchain4j.model.openaiofficial.OpenAiOfficialChatModel;
import dev.langchain4j.model.openaiofficial.OpenAiOfficialStreamingChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

import java.util.List;
import java.util.Set;

import static dev.langchain4j.model.chat.Capability.RESPONSE_FORMAT_JSON_SCHEMA;

@Configuration
class NutritionPlannerConfiguration {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/**").permitAll()
                        .anyRequest().authenticated())
                .formLogin(form -> form.loginPage("/login").permitAll())
                .httpBasic(Customizer.withDefaults()).build();
    }

    @ConditionalOnProperty(name = "ollama.base-url")
    @Bean
    ChatModel ollamaChatModel(
            @Value("${ollama.base-url}") String baseUrl,
            @Value("${ollama.model-name}") String modelName,
            @Value("${ollama.temperature}") double temperature) {
        return OllamaChatModel.builder()
                .baseUrl(baseUrl)
                .modelName(modelName)
                .temperature(temperature)
                .build();
    }

    @ConditionalOnProperty(name = "ollama.base-url")
    @Bean
    StreamingChatModel ollamaStreamingChatModel(
            @Value("${ollama.base-url}") String baseUrl,
            @Value("${ollama.model-name}") String modelName,
            @Value("${ollama.temperature}") double temperature) {
        return OllamaStreamingChatModel.builder()
                .baseUrl(baseUrl)
                .modelName(modelName)
                .temperature(temperature)
                .build();
    }

    @ConditionalOnProperty(name = "openai.api-key")
    @Bean
    ChatModel openaiChatModel(
            @Value("${openai.api-key}") String apiKey,
            @Value("${openai.model-name}") String modelName,
            @Value("${openai.temperature}") double temperature,
            @Value("${openai.azure:false}") boolean azure,
            @Value("${openai.base-url:}") String baseUrl,
            @Value("${openai.deployment-name:}") String deploymentName) {
        var builder = OpenAiOfficialChatModel.builder()
                .supportedCapabilities(Set.of(RESPONSE_FORMAT_JSON_SCHEMA))
                .strictJsonSchema(true)
                .apiKey(apiKey)
                .modelName(modelName)
                .temperature(temperature);
        if (azure) {
            builder.isAzure(true)
                    .baseUrl(baseUrl)
                    .azureDeploymentName(deploymentName);
        }
        return builder.build();
    }

    @ConditionalOnProperty(name = "openai.api-key")
    @Bean
    StreamingChatModel openaiStreamingChatModel(
            @Value("${openai.api-key}") String apiKey,
            @Value("${openai.model-name}") String modelName,
            @Value("${openai.temperature}") double temperature,
            @Value("${openai.azure:false}") boolean azure,
            @Value("${openai.base-url:}") String baseUrl,
            @Value("${openai.deployment-name:}") String deploymentName) {
        var builder = OpenAiOfficialStreamingChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .temperature(temperature);
        if (azure) {
            builder.isAzure(true)
                    .baseUrl(baseUrl)
                    .azureDeploymentName(deploymentName);
        }
        return builder.build();
    }
}
