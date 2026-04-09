package com.nutritionplanner.config;

import com.nutritionplanner.agent.NutritionGuardService;
import com.nutritionplanner.agent.RecipeCuratorService;
import com.nutritionplanner.agent.SeasonalIngredientService;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiServiceConfig {

    @Bean
    SeasonalIngredientService seasonalIngredientService(ChatModel chatModel) {
        return AiServices.builder(SeasonalIngredientService.class)
                .chatModel(chatModel)
                .build();
    }

    @Bean
    RecipeCuratorService recipeCuratorService(ChatModel chatModel) {
        return AiServices.builder(RecipeCuratorService.class)
                .chatModel(chatModel)
                .build();
    }

    @Bean
    NutritionGuardService nutritionGuardService(ChatModel chatModel) {
        return AiServices.builder(NutritionGuardService.class)
                .chatModel(chatModel)
                .build();
    }
}
