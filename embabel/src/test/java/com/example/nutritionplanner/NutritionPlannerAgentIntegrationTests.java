package com.example.nutritionplanner;

import com.embabel.agent.core.support.LlmInteraction;
import com.embabel.agent.test.integration.EmbabelMockitoIntegrationTest;
import com.embabel.chat.Message;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static com.example.nutritionplanner.NutritionTestData.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("offline")
@Import(NutritionPlannerAgentIntegrationTests.TestUsers.class)
@TestPropertySource(properties = {
        "nutrition-planner.model=test-model",
        "nutrition-planner.provider=Test",
        "logging.level.com.embabel=WARN",
        "logging.level.Embabel=WARN",
        "embabel.agent.verbosity.debug=false"
})
@Timeout(30)
class NutritionPlannerAgentIntegrationTests extends EmbabelMockitoIntegrationTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class TestUsers {
        @Bean
        UserDetailsService testUsers() {
            return new InMemoryUserDetailsManager(
                    User.withUsername("alice").password("{noop}123456").roles("USER").build(),
                    User.withUsername("bob").password("{noop}123456").roles("USER").build());
        }
    }

    @Autowired NutritionPlanner planner;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @MockitoSpyBean UserProfileProperties profiles;
    @LocalServerPort int port;

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 3})
    void actualGoalWorkflowStopsImmediatelyOnFirstPassingAudit(int failedAudits) {
        stubWorkflow(failedAudits);

        var result = planner.plan(REQUEST, () -> "alice");

        assertEquals(plan("Candidate " + failedAudits), result);
        verifyWorkflowCounts(failedAudits + 1, failedAudits);
    }

    @Test
    void actualGoalWorkflowFailsAfterExactlyFourAuditsAndThreeRevisions() {
        stubWorkflow(4);

        var failure = assertThrows(NutritionPlanRejectedException.class, () -> planner.plan(REQUEST, () -> "alice"));

        assertEquals(4, failure.audits());
        assertEquals(3, failure.revisions());
        assertEquals(FAIL, failure.validationResult());
        verifyWorkflowCounts(4, 3);
    }

    @Test
    void actualExhaustionIsAnHttp422ProblemNotAPlan() throws Exception {
        stubWorkflow(4);

        mvc.perform(post("/api/nutrition-plan").with(httpBasic("alice", "123456"))
                        .contentType("application/json").content(mapper.writeValueAsString(REQUEST)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.audits").value(4))
                .andExpect(jsonPath("$.revisions").value(3))
                .andExpect(jsonPath("$.days").doesNotExist());

        verifyWorkflowCounts(4, 3);
    }

    @Test
    void modelFailureIsExplicitAndNeverTriggersRevisions() {
        whenCreateObject(prompt -> prompt.contains("seasonal produce"), SeasonalIngredients.class)
                .thenThrow(new IllegalStateException("Model returned malformed JSON"));

        var failure = assertThrows(NutritionPlanningException.class, () -> planner.plan(REQUEST, () -> "alice"));

        assertTrue(failure.getCause().toString().contains("Model returned malformed JSON"));
        verify(llmOperations, never()).createObject(anyList(), any(), eq(WeeklyPlan.class), any(), any());
        verify(llmOperations, never()).createObject(anyList(), any(), eq(NutritionAuditValidationResult.class), any(), any());
    }

    @Test
    void frameworkJacksonMapperBindsOptionalMealsAndRejectsMalformedAuditResponses() {
        var frameworkMapper = agentPlatform.getPlatformServices().getObjectMapper();
        var plan = plan("Lentils");
        assertEquals(plan, frameworkMapper.readValue(frameworkMapper.writeValueAsString(plan), WeeklyPlan.class));
        assertThrows(tools.jackson.core.JacksonException.class, () -> frameworkMapper.readValue("""
                {"allPassed":true,"violations":[{"dayOfWeek":"MONDAY","recipeName":"Lentils",
                 "explanation":"Contains nuts","suggestedFix":"Remove nuts"}],"consolidatedFeedback":"Contradiction"}
                """, NutritionAuditValidationResult.class));
    }

    @Test
    void frameworkRunsIndependentProfileAndSeasonalActionsConcurrentlyOnVirtualThreads() {
        stubWorkflow(0);
        var entered = new CountDownLatch(2);
        var profileThread = new AtomicReference<Thread>();
        var seasonalThread = new AtomicReference<Thread>();
        doAnswer(invocation -> {
            profileThread.set(Thread.currentThread());
            entered.countDown();
            assertTrue(entered.await(5, TimeUnit.SECONDS), "Seasonal action must overlap the profile action");
            return invocation.callRealMethod();
        }).when(profiles).getUserProfile("alice");
        whenCreateObject(prompt -> prompt.contains("seasonal produce"), SeasonalIngredients.class).thenAnswer(invocation -> {
            seasonalThread.set(Thread.currentThread());
            entered.countDown();
            assertTrue(entered.await(5, TimeUnit.SECONDS), "Profile action must overlap the seasonal action");
            return SEASONAL;
        });

        assertEquals(plan("Candidate 0"), planner.plan(REQUEST, () -> "alice"));

        assertNotSame(profileThread.get(), seasonalThread.get());
        assertTrue(profileThread.get().isVirtual());
        assertTrue(seasonalThread.get().isVirtual());
    }

    @ParameterizedTest
    @ValueSource(strings = {"alice", "bob"})
    void exportedMcpGoalUsesTheAuthenticatedHttpPrincipalAcrossBothThreadBoundaries(String username) {
        stubWorkflow(0);
        var profile = username.equals("alice") ? ALICE : new UserProfile("bob", List.of("vegan"), List.of("maintenance"),
                2200, List.of("sesame"), List.of("mushrooms"));
        if (username.equals("bob")) {
            doReturn(profile).when(profiles).getUserProfile("bob");
        }
        try (var client = mcpClient(username)) {
            client.initialize();
            var tool = client.listTools().tools().stream()
                    .filter(candidate -> candidate.name().contains("createNutritionPlan")).findFirst().orElseThrow();
            assertEquals("createNutritionPlan", tool.name());
            assertEquals(java.util.Set.of("days", "countryCode", "additionalInstructions"),
                    assertInstanceOf(Map.class, tool.inputSchema().get("properties")).keySet());
            var response = client.callTool(new McpSchema.CallToolRequest(tool.name(), mcpRequest()));
            assertFalse(Boolean.TRUE.equals(response.isError()), response.toString());
            assertTrue(response.content().toString().contains("Candidate 0"), response.toString());
        }
        verify(profiles).getUserProfile(username);
        verifyWorkflowCounts(1, 0, profile);
    }

    @Test
    void mcpExhaustionReturnsAToolErrorWithoutAHiddenFifthAudit() {
        stubWorkflow(4);
        try (var client = mcpClient("alice")) {
            client.initialize();
            var response = client.callTool(new McpSchema.CallToolRequest("createNutritionPlan", mcpRequest()));
            assertTrue(Boolean.TRUE.equals(response.isError()), response.toString());
            assertTrue(response.content().toString().contains("4 audits and 3 revisions"), response.toString());
        }
        verifyWorkflowCounts(4, 3);
    }

    private McpSyncClient mcpClient(String username) {
        var transport = HttpClientStreamableHttpTransport.builder("http://localhost:" + port)
                .requestBuilder(HttpRequest.newBuilder().header("Authorization", "Basic " +
                        Base64.getEncoder().encodeToString((username + ":123456")
                                .getBytes(java.nio.charset.StandardCharsets.UTF_8))))
                .build();
        return McpClient.sync(transport).requestTimeout(Duration.ofSeconds(15)).build();
    }

    private Map<String, Object> mcpRequest() {
        return Map.of("days", List.of(
                        Map.of("day", "MONDAY", "meals", List.of("LUNCH", "DINNER")),
                        Map.of("day", "WEDNESDAY", "meals", List.of("BREAKFAST"))),
                "countryCode", "DE", "additionalInstructions", REQUEST.additionalInstructions());
    }

    private void stubWorkflow(int failedAudits) {
        var audits = new AtomicInteger();
        var revisions = new AtomicInteger();
        whenCreateObject(prompt -> prompt.contains("seasonal produce"), SeasonalIngredients.class).thenReturn(SEASONAL);
        whenCreateObject(prompt -> prompt.contains("Create a weekly meal plan"), WeeklyPlan.class).thenReturn(plan("Candidate 0"));
        whenCreateObject(prompt -> prompt.contains("Revise the recipes"), WeeklyPlan.class)
                .thenAnswer(invocation -> plan("Candidate " + revisions.incrementAndGet()));
        whenCreateObject(prompt -> prompt.contains("Validate these recipes"), NutritionAuditValidationResult.class)
                .thenAnswer(invocation -> audits.getAndIncrement() < failedAudits ? FAIL : PASS);
    }

    private void verifyWorkflowCounts(int audits, int revisions) {
        verifyWorkflowCounts(audits, revisions, ALICE);
    }

    private void verifyWorkflowCounts(int audits, int revisions, UserProfile profile) {
        verifyCreateObject(prompt -> prompt.contains("Germany") && prompt.contains("current_month"),
                SeasonalIngredients.class, interaction -> interaction.getTools().stream()
                        .anyMatch(tool -> tool.getDefinition().getName().equals("current_month")));
        var messages = ArgumentCaptor.<List<Message>>captor();
        var interactions = ArgumentCaptor.forClass(LlmInteraction.class);
        verify(llmOperations, times(audits)).createObject(messages.capture(), interactions.capture(),
                eq(NutritionAuditValidationResult.class), any(), any());
        for (var prompt : messages.getAllValues()) {
            var text = prompt.getFirst().getContent();
            assertTrue(text.contains(REQUEST.days().toString()));
            assertTrue(text.contains(REQUEST.additionalInstructions()));
            assertTrue(text.contains(profile.toString()));
        }
        assertTrue(interactions.getAllValues().stream().allMatch(interaction -> interaction.getTools().stream()
                .anyMatch(tool -> tool.getDefinition().getName().equals("weekly_meal_plan_tools"))));
        assertTrue(interactions.getAllValues().stream().allMatch(interaction -> interaction.getPromptContributors()
                .contains(NutritionPlannerAgent.Personas.NUTRITION_GUARD)));
        var planPrompts = ArgumentCaptor.<List<Message>>captor();
        verify(llmOperations, times(revisions + 1)).createObject(planPrompts.capture(), any(), eq(WeeklyPlan.class), any(), any());
        for (var prompt : planPrompts.getAllValues()) {
            var text = prompt.getFirst().getContent();
            assertTrue(text.contains(REQUEST.days().toString()));
            assertTrue(text.contains(REQUEST.additionalInstructions()));
            assertTrue(text.contains(profile.toString()));
            assertTrue(text.contains(SEASONAL.toString()));
        }
        verifyNoMoreInteractions();
    }
}
