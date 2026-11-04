package com.example.nutritionplanner.patterns;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"spring.ai.mcp.server.enabled=false", "OPENAI_API_KEY=offline-test-key"})
@ActiveProfiles({"openai", "test", "patterns", "pattern-web-test"})
@AutoConfigureMockMvc
@Import(PatternWebTests.OfflineModel.class)
@Timeout(15)
class PatternWebTests {
    @Autowired MockMvc mvc;
    @Autowired PatternTestModel model;
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @BeforeEach
    void resetModel() {
        model.clear();
    }

    @Test
    void catalogRequiresAuthenticationAndNeverCallsAProvider() throws Exception {
        mvc.perform(get("/api/patterns")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/patterns").with(user("alice"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.patterns.length()").value(4))
                .andExpect(jsonPath("$.patterns[0].id").value("routing"));
        mvc.perform(post("/api/patterns/routing").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\":\"Quick cooking\"}")).andExpect(status().isUnauthorized());
        assertThat(model.prompts).isEmpty();
    }

    @Test
    void invalidInputsAre400NotModelFailures() throws Exception {
        for (var body : List.of("{}", "{\"prompt\":null}", "{\"prompt\":\" \"}", "{broken",
                "{\"prompt\":\"" + "x".repeat(1001) + "\"}")) {
            mvc.perform(post("/api/patterns/routing").with(user("alice"))
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        assertThat(model.prompts).isEmpty();
    }

    @Test
    void runnableStatelessRoutesReturnAdvisoryResultsAndActualCounts() throws Exception {
        model.text("{\"specialist\":\"BUDGET\",\"rationale\":\"Economical cooking\"}")
                .text("{\"advice\":\"Use dried lentils\"}");
        mvc.perform(post("/api/patterns/routing").with(user("alice"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"prompt\":\"Budget lentil ideas\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.specialist").value("BUDGET"))
                .andExpect(jsonPath("$.modelCalls").value(2)).andExpect(jsonPath("$.days").doesNotExist());
        model.text("{\"tasks\":[{\"specialist\":\"QUICK_MEALS\",\"task\":\"Find a quick technique\"}]}")
                .text("{\"advice\":\"Batch cook lentils\"}").text("{\"advice\":\"Reuse cooked lentils tomorrow\"}");
        mvc.perform(post("/api/patterns/orchestrator-workers").with(user("alice"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"prompt\":\"Quick lentil ideas\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.workers.length()").value(1))
                .andExpect(jsonPath("$.modelCalls").value(3)).andExpect(jsonPath("$.days").doesNotExist());
        assertThat(model.prompts).hasSize(5);
    }

    @Test
    void statefulRoutesCheckOwnershipDecisionShapeAndReplay() throws Exception {
        var creation = mvc.perform(post("/api/patterns/memory/sessions").with(user("alice")))
                .andExpect(status().isCreated()).andReturn();
        var session = JSON.readTree(creation.getResponse().getContentAsString()).path("id").asText();
        mvc.perform(get("/api/patterns/memory/sessions/" + session).with(user("bob")))
                .andExpect(status().isNotFound());
        model.tool("readDemoPantry", "{}").text("Cook the illustrative rice and lentils");
        mvc.perform(post("/api/patterns/memory/sessions/" + session + "/turns").with(user("alice"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"prompt\":\"What is in the demo pantry?\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.modelCalls").value(2))
                .andExpect(jsonPath("$.session.activeToolResults").value(1));
        model.tool("saveDemoNote", "{\"text\":\"Rinse lentils\"}");
        var pending = mvc.perform(post("/api/patterns/confirmations").with(user("alice"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"prompt\":\"Remember to rinse lentils\"}"))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.saved").doesNotExist()).andReturn();
        var id = JSON.readTree(pending.getResponse().getContentAsString()).path("id").asText();
        var path = "/api/patterns/confirmations/" + id + "/decision";
        mvc.perform(get("/api/patterns/confirmations/" + id).with(user("bob"))).andExpect(status().isNotFound());
        mvc.perform(post(path).with(user("bob")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVE\"}")).andExpect(status().isNotFound());
        for (var invalid : List.of("{}", "{\"decision\":null}", "{\"decision\":\"AUTO\"}")) {
            mvc.perform(post(path).with(user("alice")).contentType(MediaType.APPLICATION_JSON).content(invalid))
                    .andExpect(status().isBadRequest());
        }
        assertThat(model.prompts).hasSize(3);
        model.text("Nothing was saved.");
        mvc.perform(post(path).with(user("alice")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"DENY\"}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED")).andExpect(jsonPath("$.saved").doesNotExist())
                .andExpect(jsonPath("$.decision").value("DENY")).andExpect(jsonPath("$.modelCalls").value(2));
        mvc.perform(post(path).with(user("alice")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVE\"}")).andExpect(status().isConflict());
        assertThat(model.prompts).hasSize(4);
    }

    @Test
    void modelErrorsProduceAnExplicitProblemInsteadOfAdvice() throws Exception {
        model.text("not JSON");
        mvc.perform(post("/api/patterns/routing").with(user("alice"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"prompt\":\"Quick cooking\"}"))
                .andExpect(status().isBadGateway()).andExpect(jsonPath("$.detail").exists())
                .andExpect(jsonPath("$.advice").doesNotExist());
    }

    @TestConfiguration(proxyBeanMethods = false)
    @Profile("pattern-web-test")
    static class OfflineModel {
        @Bean
        PatternTestModel chatModel() {
            return new PatternTestModel();
        }
    }
}
