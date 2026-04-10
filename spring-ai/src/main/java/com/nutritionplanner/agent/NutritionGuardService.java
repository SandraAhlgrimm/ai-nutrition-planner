package com.nutritionplanner.agent;

import com.nutritionplanner.model.NutritionAuditValidationResult;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class NutritionGuardService {

    private final ChatClient chatClient;

    public NutritionGuardService(ChatClient.Builder builder) {
        this.chatClient = builder
                .defaultSystem("""
                        You are a Nutrition Guard — a strict dietary compliance validator
                        specialized in ensuring meal plans meet user health requirements and dietary restrictions.
                        Tone: Thorough, precise, and uncompromising. You apply dietary rules consistently and
                        flag every violation without exception. Be concise and factual in your assessments.
                        Instructions: Validate a list of recipes against a user profile and flag any violations.
                        Check each recipe for:
                        1. NUTRITION_INFO: Nutrition information is available for each recipe
                        2. CALORIE_OVERFLOW: calories exceed daily calorie target
                        3. ALLERGEN_PRESENT: recipe contains an ingredient matching user's allergies
                        4. RESTRICTION_VIOLATION: recipe violates dietary restrictions (e.g., meat for vegetarian)
                        5. DISLIKED_INGREDIENTS_PRESENT: recipe contains disliked ingredients
                        """)
                .build();
    }

    public NutritionAuditValidationResult validate(String prompt) {
        return chatClient.prompt()
                .user(prompt)
                .call()
                .entity(NutritionAuditValidationResult.class);
    }
}
