package com.example.nutritionplanner;

import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.observability.AgentRequest;
import dev.langchain4j.agentic.observability.AgentResponse;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

interface AgentListeners {

    class LoggingListener implements AgentListener {

        private static final Logger log = LoggerFactory.getLogger(LoggingListener.class);

        @Override
        public boolean inheritedBySubagents() {
            return true;
        }

        @Override
        public void beforeAgentInvocation(AgentRequest request) {
            log.info("Agent '{}' invoked with inputs: {}", request.agentName(), request.inputs().keySet());
        }

        @Override
        public void afterAgentInvocation(dev.langchain4j.agentic.observability.AgentResponse response) {
            log.info("Agent '{}' completed", response.agentName());
        }
    }

    class MicrometerAgentListener implements AgentListener {

        private final MeterRegistry registry;
        private final AtomicInteger activeAgents;
        private final ConcurrentHashMap<Long, Timer.Sample> activeSamples = new ConcurrentHashMap<>();

        public MicrometerAgentListener(MeterRegistry registry) {
            this.registry = registry;
            this.activeAgents = registry.gauge("agent_active", new AtomicInteger(0));
        }

        @Override
        public boolean inheritedBySubagents() {
            return true;
        }

        @Override
        public void beforeAgentInvocation(AgentRequest request) {
            activeAgents.incrementAndGet();
            activeSamples.put(Thread.currentThread().threadId(), Timer.start(registry));

            Counter.builder("agent_invocations_total")
                    .tag("agent", request.agentName())
                    .description("Total agent invocations")
                    .register(registry)
                    .increment();
        }

        @Override
        public void afterAgentInvocation(AgentResponse response) {
            activeAgents.decrementAndGet();

            var sample = activeSamples.remove(Thread.currentThread().threadId());
            if (sample != null) {
                sample.stop(Timer.builder("agent_duration")
                        .tag("agent", response.agentName())
                        .description("Agent execution duration")
                        .register(registry));
            }
        }
    }
}
