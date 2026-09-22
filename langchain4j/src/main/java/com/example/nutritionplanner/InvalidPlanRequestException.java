package com.example.nutritionplanner;

class InvalidPlanRequestException extends IllegalArgumentException {
    InvalidPlanRequestException(String message) {
        super(message);
    }
}
