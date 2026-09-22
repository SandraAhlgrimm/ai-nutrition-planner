package com.example.nutritionplanner;

import com.embabel.agent.spi.support.springai.SpringAiLlmService;
import com.embabel.common.ai.model.ModelProvider;
import com.example.NutritionPlannerConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import static com.example.nutritionplanner.NutritionTestData.*;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({NutritionPlannerController.class, NutritionPlannerUiController.class})
@Import(NutritionPlannerConfiguration.class)
@ActiveProfiles("offline")
@TestPropertySource(properties = {"nutrition-planner.model=test-model", "nutrition-planner.provider=Test"})
class NutritionPlannerWebTests {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @MockitoBean NutritionPlanner planner;
    @MockitoBean ModelProvider modelProvider;

    @BeforeEach
    void modelName() {
        doReturn(new SpringAiLlmService("test-model", "Test", mock(ChatModel.class)))
                .when(modelProvider).getLlm(any());
    }

    @Test
    void restKeepsRequestAndResponseShapeAndUsesAuthenticatedUsername() throws Exception {
        when(planner.plan(eq(REQUEST), argThat(principal -> principal.getName().equals("alice"))))
                .thenReturn(plan("Lentils"));

        mvc.perform(post("/api/nutrition-plan").with(user("alice")).contentType("application/json")
                        .content(mapper.writeValueAsString(REQUEST)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days[0].day").value("MONDAY"))
                .andExpect(jsonPath("$.days[0].breakfast").value(nullValue()))
                .andExpect(jsonPath("$.days[0].lunch.name").value("Lentils"))
                .andExpect(jsonPath("$.days[0].lunch.nutrition.calories").value(500))
                .andExpect(jsonPath("$.days[1].breakfast.name").value("Lentils"));
        verify(planner).plan(eq(REQUEST), argThat(principal -> principal.getName().equals("alice")));
    }

    @Test
    void restAndMcpRequireAuthentication() throws Exception {
        mvc.perform(post("/api/nutrition-plan").accept("application/json").contentType("application/json")
                        .content(mapper.writeValueAsString(REQUEST)))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/mcp").accept("application/json").contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(planner);
    }

    @Test
    void invalidRequestReturns400WithoutInvokingAPlanner() throws Exception {
        mvc.perform(post("/api/nutrition-plan").with(user("alice")).contentType("application/json")
                        .content("{\"days\":[],\"countryCode\":\"DE\",\"additionalInstructions\":\"\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(planner);
    }

    @Test
    void exhaustionIsAProblemDetailWithTheExactBudget() throws Exception {
        when(planner.plan(any(), any())).thenThrow(new NutritionPlanRejectedException(new RevisionBudget(3), FAIL));

        mvc.perform(post("/api/nutrition-plan").with(user("alice")).contentType("application/json")
                        .content(mapper.writeValueAsString(REQUEST)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.audits").value(4))
                .andExpect(jsonPath("$.revisions").value(3))
                .andExpect(jsonPath("$.detail").value(containsString("Remove nuts")))
                .andExpect(jsonPath("$.days").doesNotExist());
    }

    @Test
    void modelFailureIsExplicitAndDoesNotExposeProviderInternals() throws Exception {
        when(planner.plan(any(), any())).thenThrow(new NutritionPlanningException(new IllegalStateException("Private provider error")));
        mvc.perform(post("/api/nutrition-plan").with(user("alice")).contentType("application/json")
                        .content(mapper.writeValueAsString(REQUEST)))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.detail").value(containsString("could not complete")))
                .andExpect(jsonPath("$.days").doesNotExist());
    }

    @Test
    void loginAndPlanUiRemainAvailableAndShowModelThroughPublicApi() throws Exception {
        mvc.perform(get("/login")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Test (test-model)")));
        mvc.perform(get("/").with(user("alice"))).andExpect(status().isOk())
                .andExpect(content().string(containsString("htmx:beforeSwap")));
        when(planner.plan(any(), any())).thenReturn(plan("Lentils"));
        mvc.perform(post("/plan").with(user("alice")).param("monday", "LUNCH", "DINNER")
                        .param("wednesday", "BREAKFAST").param("countryCode", "DE")
                        .param("additionalInstructions", REQUEST.additionalInstructions()))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Lentils")));
        verify(planner).plan(eq(REQUEST), argThat(principal -> principal.getName().equals("alice")));
    }

    @Test
    void uiExhaustionReturnsEscapedErrorFragmentInsteadOfInvalidPlan() throws Exception {
        when(planner.plan(any(), any())).thenThrow(new NutritionPlanRejectedException(new RevisionBudget(3),
                new NutritionAuditValidationResult(false, FAIL.violations(), "<script>untrusted feedback</script>")));
        mvc.perform(post("/plan").with(user("alice")).param("monday", "LUNCH").param("countryCode", "DE"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().string(containsString("role=\"alert\"")))
                .andExpect(content().string(containsString("4 audits and 3 revisions")))
                .andExpect(content().string(containsString("&lt;script&gt;")));
    }

    @Test
    void emptyUiSelectionIsAnExplicitValidationError() throws Exception {
        mvc.perform(post("/plan").with(user("alice")).param("countryCode", "DE"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Select at least one day")));
        verifyNoInteractions(planner);
    }
}
