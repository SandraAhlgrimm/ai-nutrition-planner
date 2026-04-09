package com.nutritionplanner;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.nutritionplanner.agent.SeasonalIngredientService;
import com.nutritionplanner.agent.RecipeCuratorService;
import com.nutritionplanner.agent.NutritionGuardService;

@SpringBootTest
@ActiveProfiles("test")
class NutritionPlannerApplicationTest {

    @MockitoBean
    private SeasonalIngredientService seasonalIngredientService;

    @MockitoBean
    private RecipeCuratorService recipeCuratorService;

    @MockitoBean
    private NutritionGuardService nutritionGuardService;

    @Test
    void contextLoads() {
    }
}
