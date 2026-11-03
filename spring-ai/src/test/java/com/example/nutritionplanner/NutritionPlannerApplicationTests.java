package com.example.nutritionplanner;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.ui.ModelMap;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.ai.mcp.server.enabled=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(NutritionPlannerApplicationTests.FixedClock.class)
@Timeout(20)
class NutritionPlannerApplicationTests {
    @MockitoBean
    ChatModel model;
    @Autowired
    NutritionPlannerAgent agent;
    @Autowired
    NutritionPlannerUiController ui;
    @Autowired
    MockMvc mvc;
    @Autowired
    MeterRegistry meters;
    @LocalServerPort
    int port;
    private NutritionModelScript script;

    @BeforeEach
    void fakeModel() {
        script = new NutritionModelScript();
        when(model.getOptions()).thenReturn(ToolCallingChatOptions.builder().model("offline-test-model").build());
        when(model.call(any(Prompt.class))).thenAnswer(invocation -> script.respond(invocation.getArgument(0)));
    }

    @Test
    void realWorkflowExecutesSkillsClockNativeToolSearchAndNutritionTools() {
        assertThat(agent.createNutritionPlan("alice", TestPlans.request())).isEqualTo(TestPlans.plan());
        assertThat(script.candidates).hasValue(1);
        assertThat(script.audits).hasValue(1);
        assertThat(script.skills).hasValue(1);
        assertThat(script.months).hasValue(1);
        assertThat(script.totals).hasValue(1);
        assertThat(script.allPrompts).hasSize(7);
        assertThat(meters.find("spring.ai.chat.client").timers()).isNotEmpty();
        assertThat(meters.find("spring.ai.tool").timers()).isNotEmpty();
    }

    @Test
    void browserAnswersAreKeptInEveryRevisionAndTheFourthAuditCanPass() {
        script.interactive = true;
        script.failedAudits = 3;
        assertThat(agent.createNutritionPlan("alice", TestPlans.request(),
                _ -> Map.of(TestPlans.QUESTION, TestPlans.ANSWER))).isEqualTo(TestPlans.plan());
        assertThat(script.candidates).hasValue(4);
        assertThat(script.audits).hasValue(4);
        assertThat(script.generationPrompts).filteredOn(p -> p.getUserMessage().getText().contains("# Revision"))
                .hasSize(3).allSatisfy(prompt -> assertThat(prompt.getUserMessage().getText())
                        .contains(TestPlans.QUESTION, TestPlans.ANSWER, "MONDAY", "LUNCH", "vegetarian", "nuts",
                                "1800", "Under 30 minutes"));
    }

    @Test
    void missingHumanAnswersStopTheToolLoopInsteadOfReachingAnAudit() {
        script.interactive = true;
        assertThatThrownBy(() -> agent.createNutritionPlan("alice", TestPlans.request(), _ -> Map.of()))
                .isInstanceOf(RuntimeException.class);
        assertThat(script.candidates).hasValue(0);
        assertThat(script.audits).hasValue(0);
    }

    @Test
    void malformedModelPlanFailsInsteadOfBeingReturned() {
        script.malformedPlan = true;
        assertThatThrownBy(() -> agent.createNutritionPlan("alice", TestPlans.request()))
                .isInstanceOf(RuntimeException.class);
        assertThat(script.audits).hasValue(0);
    }

    @Test
    void incorrectDayCannotPassEvenWhenTheModelAuditorApproves() {
        script.wrongDay = true;
        assertThatThrownBy(() -> agent.createNutritionPlan("alice", TestPlans.request()))
                .isInstanceOf(NutritionPlanValidationException.class);
        assertThat(script.audits).hasValue(4);
        assertThat(script.candidates).hasValue(4);
    }

    @Test
    void duplicateDayCandidatesReachToolsAndCanBeRevised() {
        script.duplicateCandidates = 1;
        assertThat(agent.createNutritionPlan("alice", TestPlans.request())).isEqualTo(TestPlans.plan());
        assertThat(script.candidates).hasValue(2);
        assertThat(script.audits).hasValue(2);
        assertThat(script.totals).hasValue(2);
        assertThat(script.generationPrompts.getLast().getUserMessage().getText())
                .contains("Each requested day must appear exactly once");
    }

    @Test
    void duplicateDayCandidatesExhaustTheAuditBudgetInsteadOfThrowingInTools() {
        script.duplicateCandidates = 4;
        assertThatThrownBy(() -> agent.createNutritionPlan("alice", TestPlans.request()))
                .isInstanceOf(NutritionPlanValidationException.class);
        assertThat(script.candidates).hasValue(4);
        assertThat(script.audits).hasValue(4);
        assertThat(script.totals).hasValue(4);
    }

    @Test
    void restRequiresAuthenticationAndPreservesLogin() throws Exception {
        mvc.perform(post("/api/nutrition-plan").contentType(MediaType.APPLICATION_JSON).content(TestPlans.REST_REQUEST))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/nutrition-plan").with(httpBasic("alice", "wrong"))
                        .contentType(MediaType.APPLICATION_JSON).content(TestPlans.REST_REQUEST))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/login")).andExpect(status().isOk()).andExpect(content().string(
                org.hamcrest.Matchers.containsString("offline-test-model")));
        mvc.perform(get("/").with(httpBasic("alice", "123456"))).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("meals[MONDAY]")));
        mvc.perform(post("/login").param("username", "alice").param("password", "123456"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/"));
        mvc.perform(get("/mcp")).andExpect(status().isUnauthorized());
        verify(model, never()).call(any(Prompt.class));
    }

    @Test
    void restAcceptsSharedDaysShapeAndReturnsTheExistingPlanShapeWithoutHumanQuestions() throws Exception {
        mvc.perform(post("/api/nutrition-plan").with(httpBasic("alice", "123456"))
                        .contentType(MediaType.APPLICATION_JSON).content(TestPlans.REST_REQUEST))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days[0].day").value("MONDAY"))
                .andExpect(jsonPath("$.days[0].lunch.name").value("Vegetable soup"))
                .andExpect(jsonPath("$.days[0].lunch.nutrition.calories").value(600))
                .andExpect(jsonPath("$.days[0].breakfast").doesNotExist());
        assertThat(script.audits).hasValue(1);
    }

    @Test
    void restRejectsEmptyUnknownDuplicateAndMalformedRequestsWithoutCallingAModel() throws Exception {
        for (var body : List.of(
                "{\"days\":[],\"countryCode\":\"DE\"}",
                "{\"days\":[{\"day\":\"MONDAY\",\"meals\":[]}],\"countryCode\":\"DE\"}",
                "{\"days\":[{\"day\":\"MONDAY\",\"meals\":[\"LUNCH\",\"LUNCH\"]}],\"countryCode\":\"DE\"}",
                TestPlans.REST_REQUEST.replace("\"DE\"", "\"ZZ\""),
                "{\"days\":[{\"day\":\"MONDAY\",\"meals\":[\"LUNCH\"]},{\"day\":\"MONDAY\",\"meals\":[\"LUNCH\"]}],\"countryCode\":\"DE\"}",
                TestPlans.REST_REQUEST.replace("\"LUNCH\"", "\"BRUNCH\""),
                "{\"days\":null,\"countryCode\":\"DE\"}", "{broken")) {
            mvc.perform(post("/api/nutrition-plan").with(httpBasic("alice", "123456"))
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        verify(model, never()).call(any(Prompt.class));
    }

    @Test
    void restExhaustionIs422ProblemDetailWithExactlyFourAudits() throws Exception {
        script.failedAudits = 4;
        mvc.perform(post("/api/nutrition-plan").with(httpBasic("alice", "123456"))
                        .contentType(MediaType.APPLICATION_JSON).content(TestPlans.REST_REQUEST))
                .andExpect(status().is(422)).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Nutrition plan validation failed"))
                .andExpect(jsonPath("$.audits").value(4)).andExpect(jsonPath("$.feedback").exists())
                .andExpect(jsonPath("$.days").doesNotExist());
        assertThat(script.candidates).hasValue(4);
        assertThat(script.audits).hasValue(4);
    }

    @Test
    void browserFormBindingHumanQuestionsOwnershipAndTerminalEventWork() throws Exception {
        script.interactive = true;
        var id = startBrowserPlan();
        mvc.perform(get("/interactions/" + id + "/events").with(user("mallory"))).andExpect(status().isNotFound());
        mvc.perform(post("/interaction/" + id + "/answers").with(user("mallory"))).andExpect(status().isNotFound());
        var events = mvc.perform(get("/interactions/" + id + "/events").with(user("alice")))
                .andExpect(request().asyncStarted()).andReturn();
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(events.getResponse().getContentAsString()).contains(TestPlans.QUESTION, "hx-swap=\"none\""));
        mvc.perform(post("/interaction/" + id + "/answers").with(user("alice"))
                        .param("answers[0].question", TestPlans.QUESTION).param("answers[0].answer", TestPlans.ANSWER))
                .andExpect(status().isNoContent());
        events.getAsyncResult(5000);
        mvc.perform(asyncDispatch(events)).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Vegetable soup")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("event:done")));
        assertThat(script.allPrompts)
                .filteredOn(prompt -> prompt.getSystemMessage().getText().contains("Nutrition Guard"))
                .isNotEmpty()
                .allSatisfy(prompt -> assertThat(prompt.getUserMessage().getText())
                        .contains(TestPlans.QUESTION, TestPlans.ANSWER));
        mvc.perform(post("/interaction/" + id + "/answers").with(user("alice")))
                .andExpect(status().isNotFound());
    }

    @Test
    void invalidBrowserSelectionsReturnAnHtmlErrorWithoutStartingGeneration() throws Exception {
        for (var request : List.of(post("/plan").param("countryCode", "DE"),
                post("/plan").param("meals[MONDAY]", "LUNCH").param("countryCode", "ZZ"),
                post("/plan").param("meals[MONDAY]", "BRUNCH").param("countryCode", "DE"))) {
            mvc.perform(request.with(user("alice")).header("HX-Request", "true"))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                    .andExpect(header().string("HX-Retarget", "#request-errors"))
                    .andExpect(header().string("HX-Reswap", "innerHTML"))
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("role=\"alert\"")));
        }
        verify(model, never()).call(any(Prompt.class));
    }

    @Test
    void invalidHumanAnswerRendersHtmlAndLeavesTheQuestionAvailableForRetry() throws Exception {
        script.interactive = true;
        var id = startBrowserPlan();
        var events = mvc.perform(get("/interactions/" + id + "/events").with(user("alice"))).andReturn();
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(events.getResponse().getContentAsString()).contains(TestPlans.QUESTION));

        mvc.perform(post("/interaction/" + id + "/answers").with(user("alice"))
                        .header("HX-Request", "true").param("answers[0].question", TestPlans.QUESTION))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(header().string("HX-Retarget", "#request-errors"))
                .andExpect(header().string("HX-Reswap", "innerHTML"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("non-blank answer")));
        assertThat(script.candidates).hasValue(0);

        answerBrowserQuestion(id, events);
        events.getAsyncResult(5000);
        mvc.perform(asyncDispatch(events)).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Your Weekly Plan")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("event:done")));
    }

    @Test
    void browserExhaustionDisplaysErrorRatherThanAnInvalidPlan() throws Exception {
        script.interactive = true;
        script.failedAudits = 4;
        var id = startBrowserPlan();
        var events = mvc.perform(get("/interactions/" + id + "/events").with(user("alice")))
                .andExpect(request().asyncStarted()).andReturn();
        answerBrowserQuestion(id, events);
        events.getAsyncResult(5000);
        mvc.perform(asyncDispatch(events)).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("No valid nutrition plan after 4")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Use less sodium")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("event:done")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Your Weekly Plan"))));
        assertThat(script.audits).hasValue(4);
    }

    @Test
    void browserModelFailureDisplaysAnErrorAndClosesTheStream() throws Exception {
        when(model.call(any(Prompt.class))).thenThrow(new IllegalStateException("Offline provider failure"));
        var id = startBrowserPlan();
        var events = mvc.perform(get("/interactions/" + id + "/events").with(user("alice"))).andReturn();
        events.getAsyncResult(5000);
        mvc.perform(asyncDispatch(events)).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Nutrition planning failed")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("event:done")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Your Weekly Plan"))));
        mvc.perform(get("/interactions/" + id + "/events").with(user("alice"))).andExpect(status().isNotFound());
    }

    @Test
    void browserDisconnectCancelsWaitingQuestionAndRemovesOwnership() throws Exception {
        script.interactive = true;
        var id = startBrowserPlan();
        var events = mvc.perform(get("/interactions/" + id + "/events").with(user("alice"))).andReturn();
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(events.getResponse().getContentAsString()).contains(TestPlans.QUESTION));
        var context = (org.springframework.mock.web.MockAsyncContext) events.getRequest().getAsyncContext();
        for (var listener : context.getListeners()) {
            listener.onError(new jakarta.servlet.AsyncEvent(context, new java.io.IOException("Client disconnected")));
        }
        mvc.perform(get("/interactions/" + id + "/events").with(user("alice"))).andExpect(status().isNotFound());
        assertThat(script.audits).hasValue(0);
    }

    @Test
    void containerTimeoutReleasesWaitingQuestionsWithoutWritingToACompletedEmitter() throws Exception {
        script.interactive = true;
        var id = startBrowserPlan();
        var events = mvc.perform(get("/interactions/" + id + "/events").with(user("alice"))).andReturn();
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(events.getResponse().getContentAsString()).contains(TestPlans.QUESTION));
        var context = (org.springframework.mock.web.MockAsyncContext) events.getRequest().getAsyncContext();
        for (var listener : context.getListeners()) {
            listener.onTimeout(new jakarta.servlet.AsyncEvent(context));
        }
        events.getAsyncResult(5000);
        mvc.perform(asyncDispatch(events)).andExpect(result ->
                assertThat(result.getResolvedException())
                        .isInstanceOf(org.springframework.web.context.request.async.AsyncRequestTimeoutException.class));
        mvc.perform(get("/interactions/" + id + "/events").with(user("alice"))).andExpect(status().isNotFound());
        assertThat(script.audits).hasValue(0);
    }

    @Test
    void applicationDeadlineSendsAnExplicitErrorBeforeClosingTheStream() throws Exception {
        script.interactive = true;
        var id = startBrowserPlan();
        var events = mvc.perform(get("/interactions/" + id + "/events").with(user("alice"))).andReturn();
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(events.getResponse().getContentAsString()).contains(TestPlans.QUESTION));
        org.springframework.test.util.ReflectionTestUtils.invokeMethod(ui, "expireInteraction", id);
        events.getAsyncResult(5000);
        mvc.perform(asyncDispatch(events)).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("timed out")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("event:done")));
        mvc.perform(get("/interactions/" + id + "/events").with(user("alice"))).andExpect(status().isNotFound());
        assertThat(script.audits).hasValue(0);
    }

    @Test
    void nativeMcpStreamableHttpUsesAuthenticatedPrincipalAndNeverAsksBrowserQuestions() {
        try (var client = mcpClient()) {
            client.initialize();
            var tool = client.listTools().tools().stream()
                    .filter(t -> t.name().equals("createNutritionPlan")).findFirst().orElseThrow();
            assertThat(TestPlans.JSON.writeValueAsString(tool.inputSchema())).contains("meals", "countryCode")
                    .doesNotContain("context", "principalName");
            var result = client.callTool(mcpRequest());
            assertThat(result.isError()).isNotEqualTo(true);
            assertThat(TestPlans.JSON.writeValueAsString(result.content())).contains("Vegetable soup");
            assertThat(script.audits).hasValue(1);
        }
    }

    @Test
    void nativeMcpExhaustionIsAnErrorResultNotAnUnauditedPlan() {
        script.failedAudits = 4;
        try (var client = mcpClient()) {
            client.initialize();
            var result = client.callTool(mcpRequest());
            assertThat(result.isError()).isTrue();
            assertThat(TestPlans.JSON.writeValueAsString(result.content())).doesNotContain("Vegetable soup");
            assertThat(script.audits).hasValue(4);
        }
    }

    private io.modelcontextprotocol.client.McpSyncClient mcpClient() {
        return McpClient.sync(HttpClientStreamableHttpTransport.builder("http://127.0.0.1:" + port)
                        .httpRequestCustomizer((builder, method, endpoint, body, context) ->
                                builder.header("Authorization", "Basic YWxpY2U6MTIzNDU2")).build())
                .requestTimeout(Duration.ofSeconds(10)).build();
    }

    private McpSchema.CallToolRequest mcpRequest() {
        return McpSchema.CallToolRequest.builder("createNutritionPlan")
                .arguments(Map.of("request", Map.of("meals", Map.of("MONDAY", List.of("LUNCH")),
                        "countryCode", "DE", "additionalInstructions", "Under 30 minutes"))).build();
    }

    private String startBrowserPlan() throws Exception {
        MvcResult result = mvc.perform(post("/plan").with(user("alice"))
                        .param("meals[MONDAY]", "LUNCH").param("countryCode", "DE")
                        .param("additionalInstructions", "Under 30 minutes"))
                .andExpect(status().isOk()).andReturn();
        ModelMap attributes = result.getModelAndView().getModelMap();
        return (String) attributes.get("interactionId");
    }

    private void answerBrowserQuestion(String id, MvcResult events) throws Exception {
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(events.getResponse().getContentAsString()).contains(TestPlans.QUESTION));
        mvc.perform(post("/interaction/" + id + "/answers").with(user("alice"))
                        .param("answers[0].question", TestPlans.QUESTION).param("answers[0].answer", TestPlans.ANSWER))
                .andExpect(status().isNoContent());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClock {
        @Bean
        @Primary
        Clock testClock() {
            return Clock.fixed(Instant.parse("2026-09-22T12:00:00Z"), ZoneOffset.UTC);
        }
    }
}
