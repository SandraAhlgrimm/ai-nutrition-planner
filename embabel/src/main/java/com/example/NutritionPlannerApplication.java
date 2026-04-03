package com.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@ConfigurationPropertiesScan
@SpringBootApplication
public class NutritionPlannerApplication {

	static void main(String[] args) {
		SpringApplication.run(NutritionPlannerApplication.class, args);
	}

}
