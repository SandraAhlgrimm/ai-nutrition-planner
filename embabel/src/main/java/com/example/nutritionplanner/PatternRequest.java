package com.example.nutritionplanner;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

record PatternRequest(@NotBlank @Size(max = 2000) String prompt) {}
