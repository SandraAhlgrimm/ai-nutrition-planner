package com.example.nutritionplanner;

import dev.langchain4j.agentic.declarative.TypedKey;
import org.jspecify.annotations.Nullable;

import java.time.DayOfWeek;
import java.util.Collections;
import java.util.List;

public record WeeklyPlan(List<DailyPlan> days) implements TypedKey<WeeklyPlan> {

    public WeeklyPlan {
        days = List.copyOf(days);
    }

    public WeeklyPlan() {
        this(Collections.emptyList());
    }

    public record DailyPlan(DayOfWeek day, @Nullable Recipe breakfast, @Nullable Recipe lunch, @Nullable Recipe dinner) {}
}
