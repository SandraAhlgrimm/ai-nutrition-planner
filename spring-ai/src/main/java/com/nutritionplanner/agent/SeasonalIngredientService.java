package com.nutritionplanner.agent;

import com.nutritionplanner.model.SeasonalIngredients;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class SeasonalIngredientService {

    private final ChatClient chatClient;

    public SeasonalIngredientService(ChatClient.Builder builder) {
        this.chatClient = builder
                .defaultSystem("You are a nutrition expert with deep knowledge of seasonal produce.")
                .build();
    }

    public SeasonalIngredients fetchSeasonalIngredients(String prompt) {
        return chatClient.prompt()
                .user(prompt)
                .call()
                .entity(SeasonalIngredients.class);
    }
}
