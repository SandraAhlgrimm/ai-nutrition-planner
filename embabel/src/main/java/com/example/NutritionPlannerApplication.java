package com.example;

import com.embabel.agent.autoconfigure.models.ollama.AgentOllamaAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@ConfigurationPropertiesScan
// Use the native Ollama options converter, not automatic model discovery.
@SpringBootApplication(exclude = AgentOllamaAutoConfiguration.class)
public class NutritionPlannerApplication {

	static void main(String[] args) {
		SpringApplication.run(NutritionPlannerApplication.class, args);
	}

}
