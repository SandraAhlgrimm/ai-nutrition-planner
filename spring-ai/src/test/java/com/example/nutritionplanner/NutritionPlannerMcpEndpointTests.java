package com.example.nutritionplanner;

import org.junit.jupiter.api.Test;
import org.springaicommunity.tool.search.ToolSearcher;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.profiles.active=test")
@AutoConfigureMockMvc
class NutritionPlannerMcpEndpointTests {

    private static final WeeklyPlan PLAN = new WeeklyPlan(java.util.List.of());
    private static final MediaType EVENT_STREAM = MediaType.TEXT_EVENT_STREAM;
    private static final String INITIALIZE_REQUEST = """
            {"jsonrpc":"2.0","id":"init","method":"initialize","params":{"protocolVersion":"2025-03-26","capabilities":{},"clientInfo":{"name":"test-client","version":"1.0"}}}
            """;
    private static final String INITIALIZED_NOTIFICATION = """
            {"jsonrpc":"2.0","method":"notifications/initialized"}
            """;
    private static final String TOOLS_LIST_REQUEST = """
            {"jsonrpc":"2.0","id":"tools","method":"tools/list"}
            """;
    private static final String TOOL_CALL_REQUEST = """
            {"jsonrpc":"2.0","id":"call","method":"tools/call","params":{"name":"createNutritionPlan","arguments":{"meals":{"MONDAY":["LUNCH"]},"countryCode":"DE","additionalInstructions":"Quick meals"}}}
            """;

    @SpyBean
    private NutritionPlannerAgent nutritionPlannerAgent;

    @MockBean
    private ToolSearcher toolSearcher;

    @org.springframework.beans.factory.annotation.Autowired
    private MockMvc mockMvc;

    @Test
    void mcpToolsListIsAvailableWithoutAuthentication() throws Exception {
        var sessionId = initializeSession(null);

        postMcp(TOOLS_LIST_REQUEST, sessionId, null)
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("createNutritionPlan")));
    }

    @Test
    void mcpToolCallUsesAuthenticatedUsername() throws Exception {
        doReturn(PLAN).when(nutritionPlannerAgent).createNutritionPlan(eq("bob"), any(), any());
        var sessionId = initializeSession(user("bob"));

        postMcp(TOOL_CALL_REQUEST, sessionId, user("bob"))
                .andExpect(status().isOk());

        verify(nutritionPlannerAgent).createNutritionPlan(eq("bob"), any(), any());
    }

    @Test
    void mcpToolCallFallsBackToFirstConfiguredUserWithoutAuthentication() throws Exception {
        doReturn(PLAN).when(nutritionPlannerAgent).createNutritionPlan(eq("alice"), any(), any());
        var sessionId = initializeSession(null);

        postMcp(TOOL_CALL_REQUEST, sessionId, null).andExpect(status().isOk());

        verify(nutritionPlannerAgent).createNutritionPlan(eq("alice"), any(), any());
    }

    private String initializeSession(RequestPostProcessor auth) throws Exception {
        var initialize = postMcp(INITIALIZE_REQUEST, null, auth)
                .andExpect(status().isOk())
                .andExpect(header().exists("Mcp-Session-Id"))
                .andReturn();
        var sessionId = initialize.getResponse().getHeader("Mcp-Session-Id");

        postMcp(INITIALIZED_NOTIFICATION, sessionId, auth).andExpect(status().isAccepted());
        return sessionId;
    }

    private org.springframework.test.web.servlet.ResultActions postMcp(
            String body, String sessionId, RequestPostProcessor auth) throws Exception {
        var request = post("/mcp")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(EVENT_STREAM, MediaType.APPLICATION_JSON)
                .content(body);
        if (sessionId != null) {
            request.header("Mcp-Session-Id", sessionId);
        }
        if (auth != null) {
            request.with(auth);
        }
        return mockMvc.perform(request);
    }

    @TestConfiguration
    static class TestChatClientConfiguration {

        @Bean
        ChatClient.Builder chatClientBuilder() {
            var builder = mock(ChatClient.Builder.class);
            when(builder.build()).thenReturn(mock(ChatClient.class));
            return builder;
        }

        @Bean
        ChatModel chatModel() {
            return mock(ChatModel.class);
        }
    }
}
