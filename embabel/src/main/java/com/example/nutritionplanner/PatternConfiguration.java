package com.example.nutritionplanner;

import com.embabel.agent.core.AgentPlatform;
import com.embabel.agent.spi.loop.LlmMessageResponse;
import com.embabel.agent.spi.loop.LlmMessageSender;
import com.embabel.agent.spi.loop.ToolLoopFactory;
import com.embabel.chat.AssistantMessageWithToolCalls;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@Profile("patterns")
class PatternConfiguration {

    @Bean
    SupervisorExample supervisorExample(AgentPlatform platform) {
        return new SupervisorExample(platform);
    }

    @Bean
    HybridExample hybridExample(AgentPlatform platform) {
        return new HybridExample(platform);
    }

    @Bean
    static BeanPostProcessor boundedPatternToolLoops() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if (!(bean instanceof ToolLoopFactory delegate)) {
                    return bean;
                }
                ToolLoopFactory boundedFactory = (sender, mapper, injection, iterations, decorator, inspectors,
                                                   transformers, callInspectors, context, missingTool, emptyResponse) -> {
                    Object value = context.get(PatternBudget.CONTEXT_KEY);
                    if (!(value instanceof PatternBudget budget)) {
                        return delegate.create(sender, mapper, injection, iterations, decorator, inspectors,
                                transformers, callInspectors, context, missingTool, emptyResponse);
                    }
                    // Supervisor's single outer action hides its inner calls from maxActions.
                    LlmMessageSender boundedSender = (messages, tools) -> {
                        budget.beforeModelCall();
                        LlmMessageResponse response;
                        try {
                            response = sender.call(messages, tools);
                        } catch (RuntimeException failure) {
                            throw budget.fail(PatternFailure.execution(failure));
                        }
                        if (response.getMessage() instanceof AssistantMessageWithToolCalls calls) {
                            budget.acceptToolCalls(calls.getToolCalls().size());
                        }
                        return response;
                    };
                    return delegate.create(boundedSender, mapper, injection, Math.min(iterations, 4), decorator,
                            inspectors, transformers, callInspectors, context, missingTool, emptyResponse);
                };
                return boundedFactory;
            }
        };
    }
}
