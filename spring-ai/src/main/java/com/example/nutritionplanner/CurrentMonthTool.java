package com.example.nutritionplanner;

import org.springframework.ai.tool.annotation.Tool;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.Locale;

record CurrentMonthTool(Clock clock) {

    @Tool(description = "Returns the current month in English using the application's UTC clock")
    public String currentMonth() {
        return LocalDate.now(clock).getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH);
    }
}
