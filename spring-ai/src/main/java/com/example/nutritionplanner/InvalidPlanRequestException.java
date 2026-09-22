package com.example.nutritionplanner;

final class InvalidPlanRequestException extends IllegalArgumentException {
    InvalidPlanRequestException(String message) {
        super(message);
    }
}
