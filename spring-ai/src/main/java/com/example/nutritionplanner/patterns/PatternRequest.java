package com.example.nutritionplanner.patterns;

public record PatternRequest(String prompt) {
    public PatternRequest {
        if (prompt == null || prompt.isBlank() || prompt.length() > 1000) {
            throw PatternFailure.invalid("prompt must contain 1 to 1000 characters");
        }
        prompt = prompt.strip();
    }
}
