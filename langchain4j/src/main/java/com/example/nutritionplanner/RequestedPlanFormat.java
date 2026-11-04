package com.example.nutritionplanner;

import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.json.JsonRawSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class RequestedPlanFormat {

    private RequestedPlanFormat() {}

    static ChatRequestParameters parameters(WeeklyPlanRequest request, JsonMapper mapper) {
        var days = request.days().stream().map(day -> {
            var properties = new LinkedHashMap<String, Object>();
            properties.put("day", Map.of("type", "string", "const", day.day().name()));
            for (var meal : WeeklyPlanRequest.MealType.values()) {
                properties.put(meal.name().toLowerCase(Locale.ROOT), day.meals().contains(meal)
                        ? Map.of("$ref", "#/$defs/recipe") : Map.of("type", "null"));
            }
            return object(properties);
        }).toList();

        var schema = new LinkedHashMap<String, Object>(object(Map.of("days", Map.of(
                "type", "array", "prefixItems", days, "minItems", days.size(), "maxItems", days.size()))));
        schema.put("$defs", Map.of("recipe", recipeSchema()));
        return ChatRequestParameters.builder()
                .responseFormat(ResponseFormat.builder()
                        .type(ResponseFormatType.JSON)
                        .jsonSchema(JsonSchema.builder().name("WeeklyPlan")
                                .rootElement(JsonRawSchema.from(mapper.writeValueAsString(schema))).build())
                        .build())
                .build();
    }

    static String requiredMeals(WeeklyPlanRequest request) {
        return request.days().stream()
                .flatMap(day -> day.meals().stream().map(meal -> day.day() + " " + meal))
                .reduce((first, next) -> first + "\n" + next).orElseThrow();
    }

    private static Map<String, Object> recipeSchema() {
        var text = Map.of("type", "string", "minLength", 1);
        var integer = Map.of("type", "integer", "minimum", 0);
        var number = Map.of("type", "number", "minimum", 0);
        var properties = new LinkedHashMap<String, Object>();
        properties.put("name", text);
        properties.put("ingredients", Map.of("type", "array", "minItems", 1,
                "items", object(Map.of("name", text, "quantity", text, "unit", text))));
        properties.put("nutrition", object(Map.of("calories", integer, "proteinGrams", number,
                "carbGrams", number, "fatGrams", number, "sodiumMg", integer)));
        properties.put("instructions", text);
        properties.put("prepTimeMinutes", integer);
        return object(properties);
    }

    private static Map<String, Object> object(Map<String, ?> properties) {
        return Map.of("type", "object", "properties", properties,
                "required", List.copyOf(properties.keySet()), "additionalProperties", false);
    }
}
