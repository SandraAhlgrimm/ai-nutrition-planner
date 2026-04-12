package com.nutritionplanner.agent;

import com.nutritionplanner.model.SeasonalIngredients;
import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface SeasonalIngredientAgent {

    @SystemMessage("""
            You are a nutrition expert with deep knowledge of seasonal produce.
            Focus on fish, meat, fruits, vegetables, and herbs that are at peak availability and quality.
            """)
    @UserMessage("""
            Return a list of ingredients in English that are currently in season
            for the month of {{month}} in {{country}}.
            Return a JSON object matching this schema: {"items": [{"name": "...", "quantity": "...", "unit": "..."}]}
            """)
    @Agent(description = "Fetches seasonal ingredients for a given location and time of year",
           outputKey = "seasonalIngredients")
    SeasonalIngredients fetchSeasonalIngredients(@V("month") String month, @V("country") String country);
}
