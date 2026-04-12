package com.nutritionplanner.config;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.openaiofficial.OpenAiOfficialChatModel;
import dev.langchain4j.model.openaiofficial.OpenAiOfficialStreamingChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "openai.api-key")
public class OpenAiModelConfig {

    @Bean
    ChatModel chatModel(
            @Value("${openai.api-key}") String apiKey,
            @Value("${openai.model-name}") String modelName,
            @Value("${openai.temperature}") double temperature,
            @Value("${openai.azure:false}") boolean azure,
            @Value("${openai.base-url:}") String baseUrl,
            @Value("${openai.deployment-name:}") String deploymentName) {
        var builder = OpenAiOfficialChatModel.builder()
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

    @Bean
    StreamingChatModel streamingChatModel(
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
