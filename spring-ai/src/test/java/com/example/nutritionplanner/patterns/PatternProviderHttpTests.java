package com.example.nutritionplanner.patterns;

import com.example.NutritionPlannerApplication;
import com.example.nutritionplanner.LocalChatServer;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(40)
class PatternProviderHttpTests {
    @ParameterizedTest
    @ValueSource(strings = {"openai", "azure", "ollama"})
    void realProviderSupportsTheOptInClientsMemoryToolTurnsAndNativeConfirmation(String profile) throws Exception {
        try (var server = new LocalChatServer()) {
            new WebApplicationContextRunner().withInitializer(new ConfigDataApplicationContextInitializer())
                    .withUserConfiguration(NutritionPlannerApplication.class)
                    .withPropertyValues("spring.profiles.active=" + profile + ",patterns", "spring.ai.mcp.server.enabled=false",
                            "OPENAI_API_KEY=offline-test-key", "OPENAI_MODEL_NAME=offline-model",
                            "AZURE_OPENAI_API_KEY=offline-test-key", "AZURE_OPENAI_ENDPOINT=" + server.baseUrl(),
                            "AZURE_OPENAI_DEPLOYMENT_NAME=offline-model", "spring.ai.openai.base-url=" + server.baseUrl(),
                            "OLLAMA_BASE_URL=" + server.baseUrl(), "OLLAMA_MODEL_NAME=offline-model")
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        assertThat(server.requests()).isEmpty();
                        var lab = context.getBean(PatternLabService.class);
                        var memory = context.getBean(PatternMemoryService.class);
                        var confirmations = context.getBean(PatternConfirmationService.class);

                        server.text("{\"specialist\":\"BUDGET\",\"rationale\":\"Use economical ingredients\"}")
                                .text("{\"advice\":\"Try dried lentils\"}");
                        assertThat(lab.route(new PatternRequest("Budget cooking ideas")).modelCalls()).isEqualTo(2);
                        server.text("""
                                        {"tasks":[{"specialist":"BUDGET","task":"Suggest ingredients"},
                                                  {"specialist":"QUICK_MEALS","task":"Suggest a technique"}]}
                                        """).text("{\"advice\":\"One worker result\"}")
                                .text("{\"advice\":\"Another worker result\"}")
                                .text("{\"advice\":\"Combine the cooking suggestions\"}");
                        assertThat(lab.orchestrate(new PatternRequest("Quick budget meals")).modelCalls()).isEqualTo(4);

                        var session = memory.create("alice");
                        for (int turn = 1; turn <= 4; turn++) {
                            server.tool("readDemoPantry", "{}").text("A pantry suggestion " + turn);
                            assertThat(memory.turn("alice", session.id(), new PatternRequest("Pantry idea " + turn))
                                    .modelCalls()).isEqualTo(2);
                        }
                        var compacted = memory.get("alice", session.id());
                        assertThat(compacted.loggedEvents()).isEqualTo(17);
                        assertThat(compacted.activeTurns()).isEqualTo(2);
                        assertThat(compacted.compactions()).isEqualTo(1);
                        assertThat(compacted.activeToolCalls()).isEqualTo(2);
                        assertThat(compacted.activeToolResults()).isEqualTo(2);
                        for (var decision : PatternConfirmationService.Decision.values()) {
                            server.tool("saveDemoNote", "{\"text\":\"Rinse the lentils\"}");
                            var pending = confirmations.start("alice", new PatternRequest("Remember to rinse lentils"));
                            assertThat(pending.status()).isEqualTo(PatternConfirmationService.Status.PENDING);
                            assertThat(pending.saved()).isNull();
                            server.text(decision == PatternConfirmationService.Decision.APPROVE
                                    ? "Saved the temporary note." : "Nothing was saved.");
                            var completed = confirmations.decide("alice", pending.id(),
                                    new PatternConfirmationService.DecisionRequest(decision));
                            assertThat(completed.status()).isEqualTo(PatternConfirmationService.Status.COMPLETED);
                            assertThat(completed.modelCalls()).isEqualTo(2);
                            if (decision == PatternConfirmationService.Decision.APPROVE) {
                                assertThat(completed.saved()).isEqualTo(pending.proposed());
                            } else {
                                assertThat(completed.saved()).isNull();
                            }
                            assertThat(server.requests().getLast().body().path("tools").isEmpty()).isTrue();
                        }
                        assertThat(server.requests()).hasSize(18).allSatisfy(request -> {
                            assertThat(request.path()).contains(profile.equals("ollama") ? "/api/chat" : "/chat/completions");
                            assertThat(request.body().path("model").asText()).isEqualTo("offline-model");
                        });
                        assertThat(server.errors()).isEmpty();
                        assertThat(server.remaining()).isZero();
                    });
        }
    }
}
