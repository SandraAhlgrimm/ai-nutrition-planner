package com.example.nutritionplanner;

import com.example.nutritionplanner.model.UserProfileProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(UserProfileProperties.class)
public class NutritionPlannerApplication {

    public static void main(String[] args) {
        SpringApplication.run(NutritionPlannerApplication.class, args);
    }
}
