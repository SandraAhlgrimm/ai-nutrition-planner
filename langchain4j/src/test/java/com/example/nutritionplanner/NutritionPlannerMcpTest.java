package com.example.nutritionplanner;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Answers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.example.nutritionplanner.PlanFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(NutritionPlannerMcpTest.TestUsers.class)
@Timeout(20)
class NutritionPlannerMcpTest {

    @LocalServerPort
    int port;
    @Autowired
    JsonMapper json;
    @MockitoBean(answers = Answers.CALLS_REAL_METHODS)
    ChatModel chatModel;
    @MockitoBean
    Clock clock;

    private final AtomicInteger generations = new AtomicInteger();
    private final AtomicInteger audits = new AtomicInteger();
    private final AtomicInteger toolResults = new AtomicInteger();
    private final List<String> generationPrompts = new CopyOnWriteArrayList<>();
    private int passingAudit = 1;
    private boolean incompletePlan;
    private boolean modelFailure;
    private boolean malformedAudit;

    @BeforeEach
    void scriptedModel() {
        when(clock.instant()).thenReturn(Instant.parse("2026-11-03T17:00:00Z"));
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        doAnswer(invocation -> respond(invocation.getArgument(0))).when(chatModel).doChat(any(ChatRequest.class));
    }

    @Test
    void discoversTheRealMcpToolAndItsRequestSchemaWithoutCallingTheModel() {
        try (var client = client("alice")) {
            var initialized = client.initialize();
            assertThat(initialized.serverInfo().name()).isEqualTo("langchain4j-nutrition-planner");
            var tools = client.listTools().tools();
            assertThat(tools).singleElement().satisfies(tool -> {
                assertThat(tool.name()).isEqualTo("createNutritionPlan");
                var schema = json.valueToTree(tool.inputSchema());
                assertThat(schema.get("properties").propertyNames())
                        .containsExactlyInAnyOrder("days", "countryCode", "additionalInstructions");
                assertThat(schema.get("additionalProperties").asBoolean()).isFalse();
                assertThat(schema.toString()).doesNotContain("username");
            });
        }
        verifyNoInteractions(chatModel);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 4})
    void mcpInvokesTheNativeWorkflowAndReturnsOnlyAFullyAuditedPlan(int passingAudit) {
        this.passingAudit = passingAudit;
        try (var client = client("alice")) {
            client.initialize();
            var result = client.callTool(toolCall(arguments()));

            assertThat(result.isError()).isFalse();
            assertThat(json.convertValue(result.structuredContent(), WeeklyPlan.class)).isEqualTo(plan(passingAudit));
            assertThat(json.readValue(text(result), WeeklyPlan.class)).isEqualTo(plan(passingAudit));
        }
        assertThat(generations).hasValue(passingAudit);
        assertThat(audits).hasValue(passingAudit);
        assertThat(toolResults).hasValue(passingAudit);
        assertThat(generationPrompts).allSatisfy(prompt -> assertThat(prompt)
                .contains(ALICE.toString(), REQUEST.toString(), REQUEST.additionalInstructions()));
    }

    @Test
    void exhaustionIsAnMcpErrorResultWithoutAPlanAfterExactlyFourAudits() {
        passingAudit = 0;
        try (var client = client("alice")) {
            client.initialize();
            var result = client.callTool(toolCall(arguments()));

            assertThat(result.isError()).isTrue();
            assertThat(result.structuredContent()).isNull();
            assertThat(text(result)).contains("three revisions", "Feedback for candidate-4");
        }
        assertThat(generations).hasValue(4);
        assertThat(audits).hasValue(4);
    }

    @Test
    void mcpCannotBypassTheDeterministicShapeAuditWithAModelPass() {
        incompletePlan = true;
        try (var client = client("alice")) {
            client.initialize();
            var result = client.callTool(toolCall(arguments()));

            assertThat(result.isError()).isTrue();
            assertThat(result.structuredContent()).isNull();
            assertThat(text(result)).contains("Missing requested day: THURSDAY");
        }
        assertThat(generations).hasValue(4);
        assertThat(audits).hasValue(4);
    }

    @Test
    void modelFailureIsAnExplicitMcpErrorRatherThanAnEmptyPlan() {
        modelFailure = true;
        try (var client = client("alice")) {
            client.initialize();
            var result = client.callTool(toolCall(arguments()));

            assertThat(result.isError()).isTrue();
            assertThat(result.structuredContent()).isNull();
            assertThat(text(result)).contains("No plan was returned").doesNotContain("scripted provider failure");
        }
        assertThat(generations).hasValue(0);
    }

    @Test
    void malformedModelAuditIsAnMcpErrorAndDoesNotTriggerPlanRevisions() {
        malformedAudit = true;
        try (var client = client("alice")) {
            client.initialize();
            var result = client.callTool(toolCall(arguments()));

            assertThat(result.isError()).isTrue();
            assertThat(result.structuredContent()).isNull();
            assertThat(text(result)).contains("No plan was returned");
        }
        assertThat(generations).hasValue(1);
        assertThat(audits).hasValue(1);
    }

    @Test
    void invalidRequestsAndIdentityArgumentsAreRejectedBeforeAnyModelCalls() {
        var invalidArguments = new ArrayList<Map<String, Object>>();
        RequestValidationTest.invalidRequests().forEach(request ->
                invalidArguments.add(json.convertValue(request, new TypeReference<>() {})));
        var spoofed = arguments();
        spoofed.put("username", "bob");
        invalidArguments.add(spoofed);
        try (var client = client("alice")) {
            client.initialize();
            for (var input : invalidArguments) {
                var result = client.callTool(toolCall(input));
                assertThat(result.isError()).as("Invalid MCP input: %s", input).isTrue();
                assertThat(result.structuredContent()).isNull();
            }
        }
        verifyNoInteractions(chatModel);
    }

    @Test
    void overlappingAuthenticatedMcpCallsKeepEachUsersProfileIsolated() throws Exception {
        try (var alice = client("alice"); var bob = client("bob");
             var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            alice.initialize();
            bob.initialize();
            var aliceResult = executor.submit(() -> alice.callTool(toolCall(arguments())));
            var bobResult = executor.submit(() -> bob.callTool(toolCall(arguments())));

            assertThat(aliceResult.get(5, TimeUnit.SECONDS).isError()).isFalse();
            assertThat(bobResult.get(5, TimeUnit.SECONDS).isError()).isFalse();
        }
        assertThat(generationPrompts).hasSize(2)
                .anySatisfy(prompt -> assertThat(prompt).contains(ALICE.toString()).doesNotContain(BOB.toString()))
                .anySatisfy(prompt -> assertThat(prompt).contains(BOB.toString()).doesNotContain(ALICE.toString()));
        assertThat(generations).hasValue(2);
        assertThat(audits).hasValue(2);
    }

    @Test
    void httpEndpointRequiresAuthenticationAndRejectsBrowserOrigins() throws Exception {
        try (var http = HttpClient.newHttpClient()) {
            var listTools = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}";
            var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/mcp"))
                    .timeout(Duration.ofSeconds(5))
                    .header("Accept", "application/json, text/event-stream")
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(listTools));
            assertThat(http.send(request.build(), HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(401);
            assertThat(http.send(request.copy().header("Authorization", basic("alice", "wrong")).build(),
                    HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(401);
            assertThat(http.send(request.copy().header("Authorization", basic("alice", "123456"))
                    .header("Origin", "https://untrusted.example").build(),
                    HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(403);
            var response = http.send(request.copy().header("Authorization", basic("alice", "123456")).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.headers().firstValue("mcp-session-id")).isEmpty();
            assertThat(response.headers().firstValue("set-cookie")).isEmpty();
            assertThat(response.body()).contains("createNutritionPlan");
        }
        verifyNoInteractions(chatModel);
    }

    @Test
    void mcpCanBeDisabledWithoutRequiringItsBeans() {
        new ApplicationContextRunner().withUserConfiguration(NutritionPlannerMcpConfiguration.class)
                .withPropertyValues("nutrition-planner.mcp.enabled=false")
                .run(context -> assertThat(context).hasNotFailed()
                        .doesNotHaveBean("nutritionPlannerMcpServer")
                        .doesNotHaveBean("mcpServlet")
                        .doesNotHaveBean("mcpTransport")
                        .doesNotHaveBean("mcpSecurityFilterChain"));
    }

    private ChatResponse respond(ChatRequest request) {
        if (modelFailure) {
            throw new IllegalStateException("scripted provider failure");
        }
        var prompt = request.messages().stream().filter(UserMessage.class::isInstance)
                .map(UserMessage.class::cast).reduce((first, second) -> second).orElseThrow().singleText();
        if (prompt.contains("Return a list of ingredients")) {
            assertThat(prompt).contains("NOVEMBER", "Germany");
            return response("{\"items\":[{\"name\":\"carrot\",\"quantity\":\"200\",\"unit\":\"g\"}]}");
        }
        if (prompt.contains("Create a weekly meal plan") || prompt.contains("Revise the current weekly meal plan")) {
            assertThat(Thread.currentThread().isVirtual()).isTrue();
            generationPrompts.add(prompt);
            int candidate = generations.incrementAndGet();
            var plan = plan(candidate);
            return response(json.writeValueAsString(incompletePlan
                    ? new WeeklyPlan(List.of(plan.days().getFirst())) : plan));
        }
        assertThat(prompt).contains("Audit this candidate", REQUEST.toString(), REQUEST.additionalInstructions());
        if (!(request.messages().getLast() instanceof ToolExecutionResultMessage result)) {
            audits.incrementAndGet();
            return ChatResponse.builder().aiMessage(AiMessage.from(ToolExecutionRequest.builder()
                    .id("count").name("totalMealCount").arguments("{}").build())).build();
        }
        assertThat(result.text()).isEqualTo(incompletePlan ? "1" : "2");
        toolResults.incrementAndGet();
        if (malformedAudit) {
            return response("not valid JSON");
        }
        return response(json.writeValueAsString(passingAudit > 0 && audits.get() >= passingAudit
                ? PASSED : failed(audits.get())));
    }

    private static ChatResponse response(String content) {
        return ChatResponse.builder().aiMessage(AiMessage.from(content)).build();
    }

    private Map<String, Object> arguments() {
        return json.convertValue(REQUEST, new TypeReference<>() {});
    }

    private static McpSchema.CallToolRequest toolCall(Map<String, Object> arguments) {
        return McpSchema.CallToolRequest.builder("createNutritionPlan").arguments(arguments).build();
    }

    private static String text(McpSchema.CallToolResult result) {
        return result.content().stream().filter(McpSchema.TextContent.class::isInstance)
                .map(McpSchema.TextContent.class::cast).map(McpSchema.TextContent::text)
                .reduce("", (first, next) -> first + next);
    }

    private McpSyncClient client(String username) {
        var transport = HttpClientStreamableHttpTransport.builder("http://127.0.0.1:" + port)
                .customizeClient(builder -> builder.version(HttpClient.Version.HTTP_1_1))
                .addHttpRequestCustomizer((builder, method, uri, body, context) ->
                        builder.header("Authorization", basic(username, "123456")))
                .build();
        return McpClient.sync(transport).requestTimeout(Duration.ofSeconds(5)).build();
    }

    private static String basic(String username, String password) {
        return "Basic " + Base64.getEncoder().encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestUsers {
        @Bean
        UserDetailsService testUsers() {
            return new InMemoryUserDetailsManager(
                    User.withUsername("alice").password("{noop}123456").roles("USER").build(),
                    User.withUsername("bob").password("{noop}123456").roles("USER").build());
        }

        @Bean
        @Primary
        UserProfileProperties testProfiles() {
            return PROFILES;
        }
    }
}
