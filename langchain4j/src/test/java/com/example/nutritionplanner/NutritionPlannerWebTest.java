package com.example.nutritionplanner;

import com.example.NutritionPlannerConfiguration;
import dev.langchain4j.agentic.agent.AgentInvocationException;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

import static com.example.nutritionplanner.PlanFixtures.*;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({NutritionPlannerController.class, NutritionPlannerUiController.class})
@Import(NutritionPlannerConfiguration.class)
@ActiveProfiles("test")
class NutritionPlannerWebTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    JsonMapper json;
    @MockitoBean
    NutritionPlannerAgent planner;
    @MockitoBean
    ChatModel chatModel;

    @BeforeEach
    void modelName() {
        when(chatModel.defaultRequestParameters())
                .thenReturn(DefaultChatRequestParameters.builder().modelName("scripted-model").build());
    }

    @Test
    void postPreservesJsonShapeAndUsesAuthenticatedPrincipal() throws Exception {
        when(planner.createNutritionPlan("bob", REQUEST)).thenReturn(plan(1));

        mvc.perform(post("/api/nutrition-plan").with(user("bob"))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(REQUEST)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days.length()").value(2))
                .andExpect(jsonPath("$.days[0].day").value("MONDAY"))
                .andExpect(jsonPath("$.days[0].dinner.name").value("candidate-1"))
                .andExpect(jsonPath("$.days[0].dinner.nutrition.calories").value(100))
                .andExpect(jsonPath("$.days[0].dinner.ingredients[0].quantity").value("200"))
                .andExpect(jsonPath("$.days[0].breakfast").doesNotExist());
        verify(planner).createNutritionPlan("bob", REQUEST);
    }

    @Test
    void exhaustionIs422ProblemDetailWithFinalAuditNotAnInvalidPlan() throws Exception {
        when(planner.createNutritionPlan(any(), any())).thenThrow(new PlanValidationException(failed(4)));

        mvc.perform(post("/api/nutrition-plan").with(user("alice"))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(REQUEST)))
                .andExpect(status().is(422))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.title").value("Meal plan validation failed"))
                .andExpect(jsonPath("$.feedback").value("Feedback for candidate-4"))
                .andExpect(jsonPath("$.violations[0].recipeName").value("candidate-4"))
                .andExpect(jsonPath("$.days").doesNotExist());
    }

    @Test
    void modelOrParseFailureIsAnExplicit502Problem() throws Exception {
        when(planner.createNutritionPlan(any(), any())).thenThrow(new AgentInvocationException("scripted failure"));

        mvc.perform(post("/api/nutrition-plan").with(user("alice"))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(REQUEST)))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.title").value("Meal plan generation failed"))
                .andExpect(jsonPath("$.detail").value(containsString("No plan was returned")))
                .andExpect(jsonPath("$.days").doesNotExist());
    }

    @Test
    void loginIsPublicButApiAndFormRequireAuthentication() throws Exception {
        mvc.perform(get("/login")).andExpect(status().isOk())
                .andExpect(content().string(containsString("scripted-model")));
        mvc.perform(get("/").accept(MediaType.TEXT_HTML)).andExpect(status().is3xxRedirection());
        mvc.perform(post("/api/nutrition-plan").accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(REQUEST)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidRestRequestsAreRejectedBeforeThePlanningService() throws Exception {
        for (var request : RequestValidationTest.invalidRequests().toList()) {
            mvc.perform(post("/api/nutrition-plan").with(user("alice"))
                            .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        }
        verifyNoInteractions(planner);
    }

    @Test
    void invalidBrowserSelectionsRenderHtmlWithoutCallingThePlanningService() throws Exception {
        for (var request : java.util.List.of(
                post("/plan"),
                post("/plan").param("monday", "LUNCH").param("countryCode", "ZZ"),
                post("/plan").param("monday", "LUNCH", "LUNCH"),
                post("/plan").param("monday", "SNACK"))) {
            mvc.perform(request.with(user("alice")).header("HX-Request", "true"))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                    .andExpect(view().name("fragments/plan :: error"))
                    .andExpect(content().string(containsString("role=\"alert\"")));
        }
        verifyNoInteractions(planner);
    }

    @Test
    void formPreservesRequestedDaysMealsCountryInstructionsAndPrincipal() throws Exception {
        when(planner.createNutritionPlan("bob", REQUEST)).thenReturn(plan(1));

        mvc.perform(post("/plan").with(user("bob")).header("HX-Request", "true")
                        .param("monday", "DINNER").param("thursday", "LUNCH")
                        .param("countryCode", "DE").param("additionalInstructions", REQUEST.additionalInstructions()))
                .andExpect(status().isOk())
                .andExpect(view().name("fragments/plan :: plan"))
                .andExpect(content().string(containsString("candidate-1")))
                .andExpect(content().string(containsString("100 kcal")));
        verify(planner).createNutritionPlan("bob", REQUEST);
    }

    @Test
    void formExhaustionRendersErrorAndNeverRendersPlan() throws Exception {
        when(planner.createNutritionPlan(eq("alice"), any())).thenThrow(new PlanValidationException(failed(4)));

        mvc.perform(post("/plan").with(user("alice")).header("HX-Request", "true").param("monday", "DINNER"))
                .andExpect(status().is(422))
                .andExpect(view().name("fragments/plan :: error"))
                .andExpect(content().string(containsString("role=\"alert\"")))
                .andExpect(content().string(containsString("Feedback for candidate-4")))
                .andExpect(content().string(not(containsString("Your Weekly Plan"))));
        mvc.perform(get("/").with(user("alice"))).andExpect(status().isOk())
                .andExpect(content().string(containsString("[400, 422, 502].includes(event.detail.xhr.status)")))
                .andExpect(content().string(containsString("event.detail.shouldSwap = true")));
    }

    @Test
    void formModelFailureShowsAnExplicitErrorFragment() throws Exception {
        when(planner.createNutritionPlan(any(), any())).thenThrow(new AgentInvocationException("scripted failure"));

        mvc.perform(post("/plan").with(user("alice")).param("monday", "DINNER"))
                .andExpect(status().isBadGateway())
                .andExpect(view().name("fragments/plan :: error"))
                .andExpect(content().string(containsString("No plan was returned.")));
    }
}
