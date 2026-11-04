package com.example.nutritionplanner.patterns;

import com.example.NutritionPlannerConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

import static com.example.nutritionplanner.patterns.PatternModels.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PatternController.class)
@Import(NutritionPlannerConfiguration.class)
@ActiveProfiles({"test", "patterns"})
class PatternWebTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    JsonMapper json;
    @MockitoBean
    PatternLab lab;

    @Test
    void catalogAndAllInvocationEndpointsRequireAuthentication() throws Exception {
        mvc.perform(get("/api/patterns").accept(MediaType.APPLICATION_JSON)).andExpect(status().isUnauthorized());
        for (String id : List.of("decision-routing", "confidence-cascade", "parallel-mapper")) {
            mvc.perform(post("/api/patterns/" + id).accept(MediaType.APPLICATION_JSON)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"prompt\":\"hello\",\"items\":[\"meal\"]}"))
                    .andExpect(status().isUnauthorized());
        }
        verifyNoInteractions(lab);
    }

    @Test
    void authenticatedCatalogDescribesTheExactNativeCapabilities() throws Exception {
        var catalog = new PatternLab(new PatternTestModels.Chat(request -> "unused"), null, "azure",
                "Azure has no configured decision model.").catalog();
        when(lab.catalog()).thenReturn(catalog);

        mvc.perform(get("/api/patterns").with(user("alice")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.patterns[0].id").value("decision-routing"))
                .andExpect(jsonPath("$.patterns[0].available").value(false))
                .andExpect(jsonPath("$.patterns[1].nativeApi").value("ConfidenceCascadePlanner"))
                .andExpect(jsonPath("$.patterns[2].id").value("parallel-mapper"))
                .andExpect(jsonPath("$.patterns[2].available").value(true));
    }

    @Test
    void mapperUsesOnlyBoundedDocumentedInputAndReturnsAdvisoryDataNotAWeeklyPlan() throws Exception {
        var request = new MapperRequest("quick meals", List.of("soup", "salad"));
        when(lab.map(request)).thenReturn(new MapperResult(true, NOTICE,
                List.of(new MealIdea(0, "soup", "carrot soup"), new MealIdea(1, "salad", "bean salad")),
                "Prepare both.", new Calls(3, 0)));

        mvc.perform(post("/api/patterns/parallel-mapper").with(user("alice"))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.advisoryOnly").value(true))
                .andExpect(jsonPath("$.items[0].index").value(0))
                .andExpect(jsonPath("$.items[1].idea").value("bean salad"))
                .andExpect(jsonPath("$.calls.chatModel").value(3))
                .andExpect(jsonPath("$.days").doesNotExist());
        verify(lab).map(request);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"prompt\":null}", "{\"prompt\":\" \"}", "{\"prompt\":[]}", "null", "[]"})
    void invalidPromptBodiesNeverReachTheLab(String body) throws Exception {
        mvc.perform(post("/api/patterns/decision-routing").with(user("alice"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        verifyNoInteractions(lab);
    }

    @Test
    void lengthAndItemCountBoundariesAreEnforcedBeforeModelInvocation() throws Exception {
        for (String body : List.of(
                json.writeValueAsString(Map.of("prompt", "p".repeat(MAX_PROMPT_LENGTH + 1))),
                "{\"prompt\":\"context\",\"items\":[]}",
                "{\"prompt\":\"context\",\"items\":[\"1\",\"2\",\"3\",\"4\",\"5\"]}",
                "{\"prompt\":\"context\",\"items\":[null]}",
                "{\"prompt\":\"context\",\"items\":[\"\"]}",
                json.writeValueAsString(Map.of("prompt", "context", "items", List.of("i".repeat(MAX_ITEM_LENGTH + 1)))))) {
            mvc.perform(post("/api/patterns/parallel-mapper").with(user("alice"))
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("invalid_pattern_request"));
        }
        verifyNoInteractions(lab);
    }

    @Test
    void maximumPermittedInputReachesTheLab() throws Exception {
        var request = new MapperRequest("p".repeat(MAX_PROMPT_LENGTH),
                java.util.Collections.nCopies(MAX_ITEMS, "i".repeat(MAX_ITEM_LENGTH)));
        when(lab.map(request)).thenReturn(new MapperResult(true, NOTICE, List.of(), "stub", new Calls(5, 0)));
        mvc.perform(post("/api/patterns/parallel-mapper").with(user("alice"))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request)))
                .andExpect(status().isOk());
        verify(lab).map(request);
    }

    @Test
    void absentDecisionModelIsAnExplicit503() throws Exception {
        when(lab.route(any())).thenThrow(new PatternException(HttpStatus.SERVICE_UNAVAILABLE,
                "decision_model_unavailable", "Select an installed decision model."));
        mvc.perform(post("/api/patterns/decision-routing").with(user("alice"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"prompt\":\"cooking\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("decision_model_unavailable"))
                .andExpect(jsonPath("$.advice").doesNotExist());
    }

    @Test
    void cascadeExhaustionIs422AndNeverIncludesTheRejectedCandidate() throws Exception {
        when(lab.cascade(any())).thenThrow(new PatternException(HttpStatus.UNPROCESSABLE_CONTENT,
                "cascade_exhausted", "All three tiers were rejected.",
                Map.of("calls", new Calls(3, 3), "assessments",
                        List.of(new Assessment("detailed", 0.1, false, true))), null));
        mvc.perform(post("/api/patterns/confidence-cascade").with(user("alice"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"prompt\":\"cooking\"}"))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("cascade_exhausted"))
                .andExpect(jsonPath("$.calls.decisionModel").value(3))
                .andExpect(jsonPath("$.assessments[0].applicationFinalCheck").value(true))
                .andExpect(jsonPath("$.advice").doesNotExist());
    }

    @Test
    void providerErrorsAreExplicit502WithoutProviderDetailsInTheResponse() throws Exception {
        when(lab.map(any())).thenThrow(new PatternException(HttpStatus.BAD_GATEWAY, "pattern_execution_failed",
                "The model or native workflow failed. No advisory result was returned.", Map.of(),
                new IllegalStateException("private upstream error")));
        mvc.perform(post("/api/patterns/parallel-mapper").with(user("alice"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"prompt\":\"cooking\",\"items\":[\"soup\"]}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("pattern_execution_failed"))
                .andExpect(jsonPath("$.detail").value("The model or native workflow failed. No advisory result was returned."))
                .andExpect(jsonPath("$.items").doesNotExist());
    }
}
