package com.nutritionplanner.agent;

import com.nutritionplanner.model.SeasonalIngredients;
import dev.langchain4j.service.SystemMessage;

public interface SeasonalIngredientService {

    @SystemMessage("""
            You are a nutrition expert with deep knowledge of seasonal produce.
            Return a JSON object matching this schema: {"items": [{"name": "...", "quantity": "...", "unit": "..."}]}
            Focus on fish, meat, fruits, vegetables, and herbs that are at peak availability and quality.
            """)
    SeasonalIngredients fetchSeasonalIngredients(String prompt);
}
