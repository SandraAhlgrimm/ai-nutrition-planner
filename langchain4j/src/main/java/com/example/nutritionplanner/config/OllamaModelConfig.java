package com.example.nutritionplanner.config;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.ollama.OllamaStreamingChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "ollama.base-url")
public class OllamaModelConfig {

    @Bean
    ChatModel chatModel(
            @Value("${ollama.base-url}") String baseUrl,
            @Value("${ollama.model-name}") String modelName,
            @Value("${ollama.temperature}") double temperature) {
        return OllamaChatModel.builder()
                .baseUrl(baseUrl)
                .modelName(modelName)
                .temperature(temperature)
                .build();
    }

    @Bean
    StreamingChatModel streamingChatModel(
            @Value("${ollama.base-url}") String baseUrl,
            @Value("${ollama.model-name}") String modelName,
            @Value("${ollama.temperature}") double temperature) {
        return OllamaStreamingChatModel.builder()
                .baseUrl(baseUrl)
                .modelName(modelName)
                .temperature(temperature)
                .build();
    }
}
