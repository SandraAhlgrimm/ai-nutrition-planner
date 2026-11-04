package com.example.nutritionplanner.patterns;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

public final class PatternModels {

    static final int MAX_PROMPT_LENGTH = 1_000;
    static final int MAX_ITEMS = 4;
    static final int MAX_ITEM_LENGTH = 160;
    static final String NOTICE = "Advisory cooking ideas only. Not an audited nutrition plan or medical advice.";

    private PatternModels() {
    }

    public record PromptRequest(String prompt) {
        public PromptRequest {
            prompt = inputText(prompt, "prompt", MAX_PROMPT_LENGTH);
        }
    }

    public record MapperRequest(String prompt, List<String> items) {
        public MapperRequest {
            prompt = inputText(prompt, "prompt", MAX_PROMPT_LENGTH);
            if (items == null || items.isEmpty() || items.size() > MAX_ITEMS) {
                throw PatternException.invalid("items must contain between 1 and " + MAX_ITEMS + " meal briefs.");
            }
            items = items.stream().map(item -> inputText(item, "item", MAX_ITEM_LENGTH)).toList();
        }
    }

    public record Catalog(String framework, String coreVersion, String agenticVersion, String provider,
                          String chatModel, @Nullable String decisionModel, List<Pattern> patterns) {
    }

    public record Pattern(String id, String description, String endpoint, String nativeApi, boolean available,
                          String requirement, int maxChatModelCalls, int maxDecisionModelCalls) {
    }

    public record Calls(int chatModel, int decisionModel) {
    }

    public record RoutingResult(boolean advisoryOnly, String notice, double threshold,
                                Map<String, Double> probabilities, Map<String, String> advice, Calls calls) {
    }

    public record Assessment(String tier, double confidence, boolean accepted, boolean applicationFinalCheck) {
    }

    public record CascadeResult(boolean advisoryOnly, String notice, String tier, String advice, double threshold,
                                List<Assessment> assessments, Calls calls) {
    }

    public record MealIdea(int index, String item, String idea) {
    }

    public record MapperResult(boolean advisoryOnly, String notice, List<MealIdea> items, String synthesis,
                               Calls calls) {
    }

    private static String inputText(@Nullable String value, String field, int maximum) {
        if (value == null || value.isBlank() || value.length() > maximum) {
            throw PatternException.invalid(field + " must contain between 1 and " + maximum + " characters.");
        }
        return value.strip();
    }
}
