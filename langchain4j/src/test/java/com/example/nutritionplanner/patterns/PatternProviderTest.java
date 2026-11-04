package com.example.nutritionplanner.patterns;

import com.azure.core.http.HttpClient;
import com.azure.core.http.HttpHeaderName;
import com.azure.core.http.HttpHeaders;
import com.azure.core.http.HttpResponse;
import com.azure.core.util.BinaryData;
import com.example.NutritionPlannerApplication;
import com.sun.net.httpserver.HttpServer;
import dev.langchain4j.model.azure.AzureOpenAiChatModel;
import dev.langchain4j.model.chat.ChatModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import reactor.core.publisher.Mono;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static com.example.nutritionplanner.patterns.PatternModels.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PatternProviderTest {

    @ParameterizedTest
    @ValueSource(strings = {"openai", "ollama"})
    void realProviderSdksAndNativeBuildersReachAuthenticatedRoutesWithoutLiveServices(String provider) throws Exception {
        try (var fixture = new ProviderFixture(provider)) {
            context(provider, fixture.endpoint(), "fixture-decision").run(context -> {
                assertThat(context).hasNotFailed().hasSingleBean(ChatModel.class).hasSingleBean(PatternLab.class);
                assertThat(context.getEnvironment().getProperty("spring.security.user.name")).isEqualTo("alice");
                assertThat(context.getBean(org.springframework.security.core.userdetails.UserDetailsService.class)
                        .loadUserByUsername("alice").getUsername()).isEqualTo("alice");
                var mvc = MockMvcBuilders.webAppContextSetup(context.getSourceApplicationContext())
                        .apply(springSecurity()).build();
                mvc.perform(get("/api/patterns").accept(MediaType.APPLICATION_JSON))
                        .andExpect(status().isUnauthorized());
                mvc.perform(get("/api/patterns").with(httpBasic("alice", "123456")))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.provider").value(provider))
                        .andExpect(jsonPath("$.decisionModel").value("fixture-decision"))
                        .andExpect(jsonPath("$.patterns[0].available").value(true));
                assertThat(fixture.requests).isEmpty();

                mvc.perform(post("/api/patterns/decision-routing").with(httpBasic("alice", "123456"))
                                .contentType(MediaType.APPLICATION_JSON).content("{\"prompt\":\"Mediterranean dinner\"}"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.advice.mediterranean").value("fixture cooking advice"))
                        .andExpect(jsonPath("$.advice.eastAsian").doesNotExist())
                        .andExpect(jsonPath("$.calls.chatModel").value(1))
                        .andExpect(jsonPath("$.calls.decisionModel").value(1));
                mvc.perform(post("/api/patterns/confidence-cascade").with(httpBasic("alice", "123456"))
                                .contentType(MediaType.APPLICATION_JSON).content("{\"prompt\":\"Roasting techniques\"}"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.tier").value("detailed"))
                        .andExpect(jsonPath("$.assessments[2].applicationFinalCheck").value(true))
                        .andExpect(jsonPath("$.calls.chatModel").value(3))
                        .andExpect(jsonPath("$.calls.decisionModel").value(3));
                mvc.perform(post("/api/patterns/parallel-mapper").with(httpBasic("alice", "123456"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"prompt\":\"Weeknight ideas\",\"items\":[\"soup\",\"salad\"]}"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.items.length()").value(2))
                        .andExpect(jsonPath("$.items[0].item").value("soup"))
                        .andExpect(jsonPath("$.items[1].item").value("salad"))
                        .andExpect(jsonPath("$.calls.chatModel").value(3));

                var decisions = fixture.decisions();
                assertThat(decisions).hasSize(4);
                assertThat(fixture.requests).hasSize(11);
                assertThat(decisions).allSatisfy(request -> {
                    assertThat(request.body().get("model").asString()).isEqualTo("fixture-decision");
                    assertThat(request.path()).isEqualTo(provider.equals("ollama") ? "/v1/systemone" : "/v1/decisions");
                    assertThat(request.body().toString()).doesNotContain("alice", "123456", "memoryId");
                });
                if (provider.equals("ollama")) {
                    assertThat(decisions.getFirst().body().get("state").get("prompt").asString())
                            .isEqualTo("Mediterranean dinner");
                    assertThat(decisions.getFirst().authorization()).isNull();
                    assertThat(decisions.getLast().body().get("state").get("candidateAnswer").asString())
                            .isEqualTo("fixture cooking advice");
                    assertThat(fixture.chats()).allSatisfy(request ->
                            assertThat(request.body().get("options").get("num_predict").asInt()).isEqualTo(700));
                } else {
                    assertThat(decisions.getFirst().authorization()).isEqualTo("Bearer fixture-only-key");
                    assertThat(decisions.getLast().body().toString()).contains("candidateAnswer", "fixture cooking advice");
                    assertThat(fixture.chats()).allSatisfy(request -> {
                        var body = request.body();
                        assertThat(body.has("response_format")).as("OpenAI omits response_format for text").isFalse();
                        assertThat(body.has("max_completion_tokens")
                                ? body.get("max_completion_tokens").asInt() : body.get("max_tokens").asInt()).isEqualTo(700);
                    });
                }
                assertThat(fixture.chats()).allSatisfy(request ->
                        assertThat(request.body().get("model").asString()).isEqualTo("fixture-chat"));
            });
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"openai", "ollama", "azure"})
    void noDecisionModelIsCreatedOrContactedWithoutExplicitSelection(String provider) throws Exception {
        try (var fixture = new ProviderFixture(provider)) {
            context(provider, fixture.endpoint(), "").run(context -> {
                assertThat(context).hasNotFailed().hasSingleBean(ChatModel.class).hasSingleBean(PatternLab.class);
                var lab = context.getBean(PatternLab.class);
                assertThat(lab.catalog().patterns()).extracting(Pattern::available).containsExactly(false, false, true);
                assertThat(lab.catalog().decisionModel()).isNull();
                assertThatThrownBy(() -> lab.route(new PromptRequest("Mediterranean dinner")))
                        .isInstanceOfSatisfying(PatternException.class,
                                failure -> assertThat(failure.code()).isEqualTo("decision_model_unavailable"));
                assertThat(fixture.requests).isEmpty();
            });
        }
    }

    @Test
    void azureNeverSilentlyUsesTheOpenAiDecisionEndpoint() throws Exception {
        try (var fixture = new ProviderFixture("azure")) {
            context("azure", fixture.endpoint(), "not-an-azure-decision-model").run(context -> {
                assertThat(context).hasNotFailed();
                var lab = context.getBean(PatternLab.class);
                assertThat(lab.catalog().patterns().getFirst().available()).isFalse();
                assertThat(lab.catalog().patterns().getFirst().requirement()).contains("No native Azure DecisionModel");
                assertThat(fixture.requests).isEmpty();
            });
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"openai", "ollama"})
    void upstreamDecisionFailureIsNotRetriedOrTurnedIntoAnExpertAnswer(String provider) throws Exception {
        try (var fixture = new ProviderFixture(provider)) {
            fixture.decisionStatus = 500;
            context(provider, fixture.endpoint(), "fixture-decision").run(context -> {
                assertThat(context).hasNotFailed();
                var lab = context.getBean(PatternLab.class);
                assertThatThrownBy(() -> lab.route(new PromptRequest("Mediterranean dinner")))
                        .isInstanceOfSatisfying(PatternException.class, failure -> {
                            assertThat(failure.code()).isEqualTo("pattern_execution_failed");
                            assertThat(failure.status().value()).isEqualTo(502);
                        });
                assertThat(fixture.decisions()).hasSize(1);
                assertThat(fixture.chats()).isEmpty();
            });
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"openai", "ollama"})
    void malformedDecisionWireResponsesAreNotConvertedToNoMatch(String provider) throws Exception {
        try (var fixture = new ProviderFixture(provider)) {
            fixture.omitDecisionAnswers = true;
            context(provider, fixture.endpoint(), "fixture-decision").run(context -> {
                assertThat(context).hasNotFailed();
                assertThatThrownBy(() -> context.getBean(PatternLab.class)
                        .route(new PromptRequest("Mediterranean dinner")))
                        .isInstanceOfSatisfying(PatternException.class,
                                failure -> assertThat(failure.status().value()).isEqualTo(502));
                assertThat(fixture.decisions()).hasSize(1);
                assertThat(fixture.chats()).isEmpty();
            });
        }
    }

    @Test
    void azureMapperUsesTheRealSdkWithAnIsolatedInMemoryHttpsTransport() {
        var requests = new CopyOnWriteArrayList<com.azure.core.http.HttpRequest>();
        var data = BinaryData.fromString("""
                {"id":"fixture","model":"fixture-azure","choices":[{"index":0,
                 "message":{"role":"assistant","content":"fixture cooking advice"},"finish_reason":"stop"}],
                 "usage":{"prompt_tokens":10,"completion_tokens":5,"total_tokens":15}}
                """);
        HttpClient transport = request -> {
            requests.add(request);
            var response = mock(HttpResponse.class);
            when(response.getRequest()).thenReturn(request);
            when(response.getStatusCode()).thenReturn(200);
            when(response.getHeaders()).thenReturn(new HttpHeaders().set(HttpHeaderName.CONTENT_TYPE, "application/json"));
            when(response.getHeaderValue(HttpHeaderName.CONTENT_TYPE)).thenReturn("application/json");
            when(response.getBodyAsBinaryData()).thenReturn(data);
            when(response.getBodyAsByteArray()).thenReturn(Mono.just(data.toBytes()));
            when(response.getBodyAsString()).thenReturn(Mono.just(data.toString()));
            when(response.getBody()).thenReturn(data.toFluxByteBuffer());
            when(response.buffer()).thenReturn(response);
            return Mono.just(response);
        };
        var model = AzureOpenAiChatModel.builder()
                .apiKey("fixture-only-key").endpoint("https://example.invalid").deploymentName("fixture-azure")
                .httpClientProvider(() -> transport).maxRetries(0).build();
        var lab = new PatternLab(model, null, "azure", "No native Azure DecisionModel is configured.");

        var result = lab.map(new MapperRequest("weeknight", List.of("soup", "salad")));

        assertThat(result.items()).hasSize(2).extracting(MealIdea::item).containsExactly("soup", "salad");
        assertThat(result.calls()).isEqualTo(new Calls(3, 0));
        assertThat(requests).hasSize(3).allSatisfy(request ->
                assertThat(request.getUrl().getPath()).contains("/deployments/fixture-azure/chat/completions"));
    }

    private static WebApplicationContextRunner context(String provider, String endpoint, String decisionModel) {
        return new WebApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withUserConfiguration(NutritionPlannerApplication.class)
                .withPropertyValues("spring.profiles.active=patterns," + provider,
                        "spring.security.user.name=alice", "spring.security.user.password=123456",
                        "PATTERNS_DECISION_MODEL_NAME=" + decisionModel,
                        "OPENAI_API_KEY=fixture-only-key", "OPENAI_MODEL_NAME=fixture-chat",
                        "langchain4j.open-ai.chat-model.base-url=" + endpoint + "/v1",
                        "OLLAMA_BASE_URL=" + endpoint, "OLLAMA_MODEL_NAME=fixture-chat",
                        "AZURE_OPENAI_API_KEY=fixture-only-key", "AZURE_OPENAI_ENDPOINT=https://example.invalid",
                        "AZURE_OPENAI_DEPLOYMENT_NAME=fixture-azure");
    }

    private record Request(String path, JsonNode body, String authorization) {
    }

    private static final class ProviderFixture implements AutoCloseable {
        private final JsonMapper json = JsonMapper.builder().build();
        private final HttpServer server;
        private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        private final List<Request> requests = new CopyOnWriteArrayList<>();
        private final AtomicInteger grades = new AtomicInteger();
        private final String provider;
        private int decisionStatus = 200;
        private boolean omitDecisionAnswers;

        ProviderFixture(String provider) throws IOException {
            this.provider = provider;
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.setExecutor(executor);
            server.createContext("/", exchange -> {
                var request = new Request(exchange.getRequestURI().getPath(),
                        json.readTree(exchange.getRequestBody().readAllBytes()),
                        exchange.getRequestHeaders().getFirst("Authorization"));
                requests.add(request);
                boolean decision = request.path().endsWith("/decisions") || request.path().endsWith("/systemone");
                int status = decision ? decisionStatus : 200;
                String body = status != 200 ? "{\"error\":{\"message\":\"fixture upstream failure\"}}"
                        : decision ? decisionResponse(request.body()) : chatResponse();
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(status, bytes.length);
                try (var output = exchange.getResponseBody()) {
                    output.write(bytes);
                }
            });
            server.start();
        }

        String endpoint() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        List<Request> decisions() {
            return requests.stream().filter(request ->
                    request.path().endsWith("/decisions") || request.path().endsWith("/systemone")).toList();
        }

        List<Request> chats() {
            return requests.stream().filter(request ->
                    request.path().endsWith("/chat") || request.path().endsWith("/chat/completions")).toList();
        }

        private String decisionResponse(JsonNode body) {
            if (omitDecisionAnswers) {
                return provider.equals("ollama")
                        ? "{\"model\":\"fixture-decision\",\"answers\":{}}"
                        : "{\"model\":\"fixture-decision\",\"answers\":[]}";
            }
            boolean grade = body.get("questions").toString().contains("\"accepted\"");
            var probabilities = new LinkedHashMap<String, Double>();
            if (grade) {
                probabilities.put("accepted", grades.incrementAndGet() == 3 ? 0.9 : 0.1);
            } else {
                probabilities.putAll(Map.of("mediterranean", 0.9, "eastAsian", 0.1, "baking", 0.1));
            }
            if (provider.equals("ollama")) {
                var answers = new LinkedHashMap<String, Object>();
                probabilities.forEach((name, probability) -> answers.put(name, Map.of("type", "noul", "noul", probability)));
                return json.writeValueAsString(Map.of("model", "fixture-decision", "answers", answers,
                        "usage", Map.of("input_tokens", 20, "output_tokens", 0)));
            }
            var answers = new ArrayList<Map<String, Object>>();
            probabilities.forEach((name, probability) -> answers.add(Map.of(
                    "type", "predicate", "name", name, "probability", probability)));
            return json.writeValueAsString(Map.of("model", "fixture-decision", "answers", answers,
                    "usage", Map.of("input_tokens", 20, "output_tokens", 0, "total_tokens", 20)));
        }

        private String chatResponse() {
            return provider.equals("ollama") ? """
                    {"model":"fixture-chat","message":{"role":"assistant","content":"fixture cooking advice"},
                     "done":true,"done_reason":"stop","prompt_eval_count":10,"eval_count":5}
                    """ : """
                    {"id":"fixture","model":"fixture-chat","choices":[{"index":0,
                     "message":{"role":"assistant","content":"fixture cooking advice"},"finish_reason":"stop"}],
                     "usage":{"prompt_tokens":10,"completion_tokens":5,"total_tokens":15}}
                    """;
        }

        @Override
        public void close() {
            server.stop(0);
            executor.shutdownNow();
        }
    }
}
