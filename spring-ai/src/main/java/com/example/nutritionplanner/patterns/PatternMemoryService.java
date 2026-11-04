package com.example.nutritionplanner.patterns;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.session.CreateSessionRequest;
import org.springframework.ai.session.DefaultSessionService;
import org.springframework.ai.session.EventType;
import org.springframework.ai.session.InMemorySessionRepository;
import org.springframework.ai.session.SessionAttributes;
import org.springframework.ai.session.SessionService;
import org.springframework.ai.session.advisor.SessionAdvisors;
import org.springframework.ai.session.compaction.TurnCountTrigger;
import org.springframework.ai.session.compaction.WindowCompactionStrategy;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

@Service
@Profile("patterns")
public final class PatternMemoryService {
    static final int MAX_TURNS = 8;
    static final Duration LIFETIME = Duration.ofMinutes(10);
    private final SessionService sessions = DefaultSessionService.builder()
            .sessionRepository(InMemorySessionRepository.builder().build()).defaultTimeToLive(LIFETIME).build();
    private final OwnedPatternStates<MemoryState> states;
    private final PatternModels models;
    private final ChatClient client;
    private final ToolCallback pantry = FunctionToolCallback.builder("readDemoPantry",
                    (EmptyRequest _) -> List.of("lentils", "rice", "carrots"))
            .description("Read this demo's fixed, illustrative pantry; not the user's real pantry.")
            .inputType(EmptyRequest.class).build();

    PatternMemoryService(PatternModels models, Clock clock) {
        this.models = models;
        this.states = new OwnedPatternStates<>(clock, LIFETIME, sessions::delete);
        this.client = models.client(SessionAdvisors.builder(sessions)
                .compaction(new TurnCountTrigger(3), WindowCompactionStrategy.builder().lastTurns(2).build())
                .build());
    }

    public MemoryView create(String owner) {
        var id = states.create(owner, sessionId -> {
            sessions.create(CreateSessionRequest.builder().id(sessionId).timeToLive(LIFETIME)
                    .metadata(SessionAttributes.PRINCIPAL_NAME, owner).build());
            return new MemoryState(sessionId);
        });
        return get(owner, id);
    }

    public MemoryView get(String owner, String id) {
        return states.use(owner, id, this::view);
    }

    public MemoryReply turn(String owner, String id, PatternRequest request) {
        return states.use(owner, id, state -> {
            if (state.failed) {
                throw new PatternFailure(HttpStatus.CONFLICT, "This session failed; start a new demo session");
            }
            if (state.turns >= MAX_TURNS) throw PatternFailure.limit("A memory demo allows at most eight turns");
            state.turns++;
            var budget = models.budget(2);
            try {
                var response = client.prompt().system(PatternModels.ADVISORY + """
                                Remember the user's cooking preferences within this session.
                                When the user asks about pantry ingredients, call readDemoPantry once.
                                The tool is illustrative and read-only. Answer in at most 2000 characters.
                                """).user(request.prompt()).toolCallbacks(pantry)
                        .advisors(SessionAdvisors.session(id)).advisors(budget).call().chatResponse();
                return new MemoryReply(PatternModels.text(response), budget.calls(), view(state));
            } catch (RuntimeException exception) {
                state.failed = true;
                throw exception;
            }
        });
    }

    @Scheduled(fixedDelay = 60_000)
    void expire() {
        states.expire();
    }

    private MemoryView view(MemoryState state) {
        var log = sessions.getEvents(state.id);
        var active = sessions.getActiveMessages(state.id);
        var compactions = log.stream().filter(event -> event.getType() == EventType.COMPACTION).count();
        int toolCalls = active.stream().filter(AssistantMessage.class::isInstance)
                .map(AssistantMessage.class::cast).mapToInt(message -> message.getToolCalls().size()).sum();
        int toolResults = active.stream().filter(ToolResponseMessage.class::isInstance)
                .map(ToolResponseMessage.class::cast).mapToInt(message -> message.getResponses().size()).sum();
        return new MemoryView(state.id, state.turns, state.failed, log.size(), active.size(),
                active.stream().filter(message -> message.getMessageType() == MessageType.USER).count(),
                compactions, toolCalls, toolResults);
    }

    public record EmptyRequest() {}
    public record MemoryView(String id, int turns, boolean failed, int loggedEvents, int activeMessages,
                             long activeTurns, long compactions, int activeToolCalls, int activeToolResults) {}
    public record MemoryReply(String answer, int modelCalls, MemoryView session) {}

    private static final class MemoryState {
        final String id;
        int turns;
        boolean failed;

        MemoryState(String id) {
            this.id = id;
        }
    }
}
