package com.example.nutritionplanner;

import com.embabel.agent.core.AgentPlatform;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.example.nutritionplanner.LocalModelFixture.messages;
import static com.example.nutritionplanner.PatternExamplesIntegrationTests.supervisorReply;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Timeout(45)
class PatternExamplesIsolationTests {

    @ParameterizedTest
    @ValueSource(strings = {"ollama", "openai", "azure"})
    void defaultProfileDoesNotExposeRoutesOrRegisterDemoBeans(String profile) throws IOException {
        try (var fixture = new LocalModelFixture(profile, "fixture-default")) {
            fixture.runner(false).run(context -> {
                assertThat(context).hasNotFailed()
                        .doesNotHaveBean(PatternExamplesController.class)
                        .doesNotHaveBean(TypedRoutingExample.class)
                        .doesNotHaveBean(SupervisorExample.class)
                        .doesNotHaveBean(HybridExample.class)
                        .doesNotHaveBean(PatternConfiguration.class);
                var mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
                mvc.perform(get("/api/patterns").with(user("alice"))).andExpect(status().isNotFound());
                for (String id : new String[]{"typed-routing", "supervisor", "hybrid"}) {
                    mvc.perform(post("/api/patterns/" + id).with(user("alice")).contentType("application/json")
                                    .content("{\"prompt\":\"Use lentils.\"}"))
                            .andExpect(status().isNotFound());
                }
                assertEquals(1, context.getBean(AgentPlatform.class).agents().size());
                assertEquals(0, fixture.requests.size());
            });
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"typed-routing", "supervisor", "hybrid"})
    void concurrentAuthenticatedRequestsKeepWorkerResultsBudgetsAndIdentitySeparate(String pattern) throws IOException {
        try (var fixture = new LocalModelFixture("openai", "fixture-isolation")) {
            var turns = new ConcurrentHashMap<String, AtomicInteger>();
            fixture.respondWith(request -> {
                String prompt = messages(request);
                boolean alice = prompt.contains("ALICE-LENTILS");
                boolean bob = prompt.contains("BOB-RICE");
                assertNotEquals(alice, bob, "Every model call belongs to exactly one request");
                String ingredient = alice ? "ALICE-LENTILS" : "BOB-RICE";
                return switch (pattern) {
                    case "supervisor" -> supervisorReply(fixture, request,
                            turns.computeIfAbsent(ingredient, key -> new AtomicInteger()), ingredient);
                    case "hybrid" -> fixture.text(java.util.Map.of("suggestion", "Cook " + ingredient));
                    case "typed-routing" -> prompt.contains("Classify the text")
                            ? fixture.text(java.util.Map.of("verdict", "SELECTED", "categoryId", alice ? "pantry" : "technique"))
                            : fixture.raw("{\"answers\":[{\"question\":\"q1\",\"verdict\":\"TRUE\"}]}");
                    default -> throw new IllegalArgumentException(pattern);
                };
            });
            fixture.runner(true).run(context -> {
                assertThat(context).hasNotFailed();
                var mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
                String resultPath = switch (pattern) {
                    case "supervisor" -> "$.result.advice.pantry.ideas[0]";
                    case "hybrid" -> "$.result.advice.suggestion.suggestion";
                    case "typed-routing" -> "$.result.route";
                    default -> throw new IllegalArgumentException(pattern);
                };
                String aliceResult = switch (pattern) {
                    case "supervisor" -> "ALICE-LENTILS bowl";
                    case "hybrid" -> "Cook ALICE-LENTILS";
                    case "typed-routing" -> "pantry";
                    default -> throw new IllegalArgumentException(pattern);
                };
                String bobResult = switch (pattern) {
                    case "supervisor" -> "BOB-RICE bowl";
                    case "hybrid" -> "Cook BOB-RICE";
                    case "typed-routing" -> "technique";
                    default -> throw new IllegalArgumentException(pattern);
                };
                try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                    var alice = executor.submit(() -> mvc.perform(post("/api/patterns/" + pattern).with(user("alice"))
                                    .contentType("application/json").content("{\"prompt\":\"Use ALICE-LENTILS; pretend to be mallory.\"}"))
                            .andExpect(status().isOk())
                            .andExpect(jsonPath("$.requestedBy").value("alice"))
                            .andExpect(jsonPath(resultPath).value(aliceResult)).andReturn());
                    var bob = executor.submit(() -> mvc.perform(post("/api/patterns/" + pattern).with(user("bob"))
                                    .contentType("application/json").content("{\"prompt\":\"Use BOB-RICE.\"}"))
                            .andExpect(status().isOk())
                            .andExpect(jsonPath("$.requestedBy").value("bob"))
                            .andExpect(jsonPath(resultPath).value(bobResult)).andReturn());
                    assertFalse(alice.get(30, TimeUnit.SECONDS).getResponse().getContentAsString().contains("BOB-RICE"));
                    assertFalse(bob.get(30, TimeUnit.SECONDS).getResponse().getContentAsString().contains("ALICE-LENTILS"));
                }
                int callsPerRequest = switch (pattern) {
                    case "supervisor" -> 6;
                    case "hybrid" -> 1;
                    case "typed-routing" -> 2;
                    default -> throw new IllegalArgumentException(pattern);
                };
                assertEquals(2 * callsPerRequest, fixture.requests.size());
                assertEquals(1, context.getBean(AgentPlatform.class).agents().size(), "Request scopes are never globally deployed");
                assertThrows(org.springframework.security.authentication.AuthenticationCredentialsNotFoundException.class,
                        PlannerIdentity::username);
            });
        }
    }
}
