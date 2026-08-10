package com.example.nutritionplanner;

import dev.langchain4j.model.chat.ChatModel;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.security.Principal;
import java.time.DayOfWeek;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(NutritionPlannerUiController.class)
@AutoConfigureMockMvc(addFilters = false)
class NutritionPlannerUiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ChatModel chatModel;

    @MockitoBean
    private NutritionPlannerAgent nutritionPlannerAgent;

    @Test
    void createPlanMapsFormDataToWeeklyPlanRequest() throws Exception {
        when(nutritionPlannerAgent.createNutritionPlan(eq("alice"), any(WeeklyPlanRequest.class)))
                .thenReturn(new WeeklyPlan(List.of()));

        mockMvc.perform(post("/plan")
                        .principal((Principal) () -> "alice")
                        .param("monday", "BREAKFAST", "DINNER")
                        .param("countryCode", "DE")
                        .param("additionalInstructions", "prefer quick recipes"))
                .andExpect(status().isOk())
                .andExpect(view().name("fragments/plan :: plan"));

        var requestCaptor = ArgumentCaptor.forClass(WeeklyPlanRequest.class);
        verify(nutritionPlannerAgent).createNutritionPlan(eq("alice"), requestCaptor.capture());
        var request = requestCaptor.getValue();
        assertThat(request.countryCode()).isEqualTo("DE");
        assertThat(request.additionalInstructions()).isEqualTo("prefer quick recipes");
        assertThat(request.days()).containsExactly(
                new WeeklyPlanRequest.DayPlanRequest(DayOfWeek.MONDAY,
                        List.of(WeeklyPlanRequest.MealType.BREAKFAST, WeeklyPlanRequest.MealType.DINNER))
        );
    }
}
