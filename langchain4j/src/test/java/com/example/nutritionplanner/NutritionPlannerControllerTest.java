package com.example.nutritionplanner;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.security.Principal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NutritionPlannerController.class)
@AutoConfigureMockMvc(addFilters = false)
class NutritionPlannerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NutritionPlannerAgent nutritionPlannerAgent;

    @Test
    void createNutritionPlanUsesAuthenticatedUsername() throws Exception {
        when(nutritionPlannerAgent.createNutritionPlan(eq("alice"), any(WeeklyPlanRequest.class)))
                .thenReturn(new WeeklyPlan(List.of()));

        mockMvc.perform(post("/api/nutrition-plan")
                        .principal((Principal) () -> "alice")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "days": [],
                                  "countryCode": "DE",
                                  "additionalInstructions": "quick recipes"
                                }
                                """))
                .andExpect(status().isOk());

        var requestCaptor = ArgumentCaptor.forClass(WeeklyPlanRequest.class);
        verify(nutritionPlannerAgent).createNutritionPlan(eq("alice"), requestCaptor.capture());
        assertThat(requestCaptor.getValue().countryCode()).isEqualTo("DE");
        assertThat(requestCaptor.getValue().additionalInstructions()).isEqualTo("quick recipes");
    }
}
