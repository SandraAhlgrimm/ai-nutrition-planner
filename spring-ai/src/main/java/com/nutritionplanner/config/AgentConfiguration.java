package com.nutritionplanner.config;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Agent Configuration - sets up Spring AI agent infrastructure.
 *
 * Configures:
 * - ChatMemory: Persistent conversation memory for agents across multiple interactions
 * - Advisors are configured in each agent class
 * - ToolCallAdvisor automatically detects and invokes tool calls
 * - PromptChatMemoryAdvisor maintains context across agent interactions
 */
@Configuration
public class AgentConfiguration {

    /**
     * In-memory chat memory for maintaining conversation context across agent interactions.
     * Each agent has access to its own memory scope for maintaining context and learning
     * from previous interactions within a session.
     */
    @Bean
    public ChatMemory chatMemory() {
        return MessageWindowChatMemory.builder().build();
    }
}
