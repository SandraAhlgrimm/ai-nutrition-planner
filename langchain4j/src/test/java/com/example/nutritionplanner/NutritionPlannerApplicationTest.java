package com.example.nutritionplanner;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static com.example.nutritionplanner.PlanFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NutritionPlannerApplicationTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    JsonMapper json;
    @MockitoBean(answers = Answers.CALLS_REAL_METHODS)
    ChatModel chatModel;
    @MockitoBean
    Clock clock;

    @Test
    void realAuthenticationProfileBindingAndComposedBeansReachTheApi() throws Exception {
        when(clock.instant()).thenReturn(Instant.parse("2026-09-22T12:00:00Z"));
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        var generationPrompt = new AtomicReference<String>();
        doAnswer(invocation -> {
            ChatRequest request = invocation.getArgument(0);
            var prompt = request.messages().stream().filter(UserMessage.class::isInstance)
                    .map(UserMessage.class::cast).reduce((first, second) -> second).orElseThrow().singleText();
            String output;
            if (prompt.contains("Return a list of ingredients")) {
                assertThat(prompt).contains("SEPTEMBER", "Germany");
                output = "{\"items\":[{\"name\":\"carrot\",\"quantity\":\"200\",\"unit\":\"g\"}]}";
            } else if (prompt.contains("Create a weekly meal plan")) {
                generationPrompt.set(prompt);
                output = json.writeValueAsString(plan(1));
            } else {
                assertThat(prompt).contains("Audit this candidate", ALICE.toString(), REQUEST.toString());
                output = json.writeValueAsString(PASSED);
            }
            return ChatResponse.builder().aiMessage(AiMessage.from(output)).build();
        }).when(chatModel).doChat(any(ChatRequest.class));

        mvc.perform(post("/login").param("username", "alice").param("password", "123456"))
                .andExpect(status().is3xxRedirection()).andExpect(authenticated().withUsername("alice"));
        mvc.perform(post("/api/nutrition-plan").with(httpBasic("alice", "123456"))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(REQUEST)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days[0].dinner.name").value("candidate-1"));
        assertThat(generationPrompt.get()).contains(ALICE.toString(), REQUEST.toString(), REQUEST.additionalInstructions());
    }
}
