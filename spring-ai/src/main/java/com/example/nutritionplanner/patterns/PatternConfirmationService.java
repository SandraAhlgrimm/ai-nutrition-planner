package com.example.nutritionplanner.patterns;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallDecision;
import org.springframework.ai.model.tool.ToolCallDecisions;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

@Service
@Profile("patterns")
public final class PatternConfirmationService {
    static final Duration LIFETIME = Duration.ofMinutes(5);
    private static final JsonMapper JSON = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
    private final OwnedPatternStates<Confirmation> states;
    private final PatternModels models;
    private final ChatClient client;

    PatternConfirmationService(PatternModels models, Clock clock) {
        this.models = models;
        this.client = models.client(List.of());
        this.states = new OwnedPatternStates<>(clock, LIFETIME, _ -> {});
    }

    public ConfirmationView start(String owner, PatternRequest request) {
        var id = states.create(owner, key -> new Confirmation(key, request.prompt()));
        try {
            return states.use(owner, id, state -> {
                var budget = models.budget(1);
                var response = client.prompt().messages(state.prompt.getInstructions()).toolCallbacks(state.tool)
                        .advisors(budget).call().chatResponse();
                state.modelCalls = budget.calls();
                if (!ToolCallDecisions.isAwaitingDecision(response) || response == null || response.getResult() == null) {
                    throw PatternFailure.output("The model did not request confirmation of the demo note");
                }
                var message = response.getResult().getOutput();
                if (message.getToolCalls().size() != 1
                        || !message.getToolCalls().getFirst().name().equals("saveDemoNote")) {
                    throw PatternFailure.output("Only one saveDemoNote call may be confirmed");
                }
                state.proposed = JSON.readValue(message.getToolCalls().getFirst().arguments(), Note.class);
                PatternModels.requiredText(state.proposed.text(), 280, "demo note");
                state.pending = response;
                state.status = Status.PENDING;
                return state.view();
            });
        } catch (RuntimeException exception) {
            states.discard(owner, id);
            throw exception;
        }
    }

    public ConfirmationView get(String owner, String id) {
        return states.use(owner, id, Confirmation::view);
    }

    public ConfirmationView decide(String owner, String id, DecisionRequest request) {
        return states.use(owner, id, state -> {
            if (state.status != Status.PENDING) {
                throw new PatternFailure(HttpStatus.CONFLICT, "This confirmation has already been consumed");
            }
            state.status = Status.RUNNING;
            state.decision = request.decision();
            var budget = models.budget(1);
            try {
                var pending = Objects.requireNonNull(state.pending);
                var key = ToolCallDecisions.toolCallKeys(Objects.requireNonNull(pending.getResult()).getOutput()).getFirst();
                var decision = request.decision() == Decision.APPROVE ? ToolCallDecision.allow(key)
                        : ToolCallDecision.deny(key, "The user denied this demo note. Nothing was saved.");
                var executed = models.tools().executeToolCalls(state.prompt, pending, ToolCallDecisions.of(decision));
                // Resume the original provider messages, but expose no tools: approval authorizes exactly one stored call.
                var response = client.prompt().messages(executed.conversationHistory())
                        .advisors(budget).call().chatResponse();
                state.answer = PatternModels.text(response);
                state.status = Status.COMPLETED;
                return state.viewWithCalls(state.modelCalls + budget.calls());
            } catch (RuntimeException exception) {
                state.status = Status.FAILED;
                throw exception;
            } finally {
                state.modelCalls += budget.calls();
                state.pending = null;
            }
        });
    }

    @Scheduled(fixedDelay = 60_000)
    void expire() {
        states.expire();
    }

    public enum Decision { APPROVE, DENY }
    public enum Status { GENERATING, PENDING, RUNNING, COMPLETED, FAILED }
    public record DecisionRequest(Decision decision) {
        public DecisionRequest {
            if (decision == null) throw PatternFailure.invalid("decision must be APPROVE or DENY");
        }
    }
    public record Note(@JsonProperty(required = true) String text) {}
    public record ConfirmationView(String id, Status status, @Nullable Decision decision, @Nullable Note proposed,
                                   @Nullable Note saved, @Nullable String answer, int modelCalls) {}

    private static final class Confirmation {
        final String id;
        final Prompt prompt;
        final ToolCallback tool;
        Status status = Status.GENERATING;
        @Nullable Decision decision;
        @Nullable ChatResponse pending;
        @Nullable Note proposed;
        @Nullable Note saved;
        @Nullable String answer;
        int modelCalls;

        Confirmation(String id, String request) {
            this.id = id;
            this.tool = FunctionToolCallback.builder("saveDemoNote", (Note note) -> {
                        if (status != Status.RUNNING || decision != Decision.APPROVE || saved != null) {
                            throw new IllegalStateException("A fresh approval is required to save this demo note");
                        }
                        PatternModels.requiredText(note.text(), 280, "demo note");
                        saved = note;
                        return "Saved this temporary demo note: " + note.text();
                    }).description("Save one cooking note of at most 280 characters in this temporary demo only. "
                            + "No files, purchases, email, account settings, or real pantry data are changed.")
                    .inputType(Note.class).toolMetadata(ToolMetadata.builder().requiresConfirmation(true).build()).build();
            List<Message> messages = List.of(new SystemMessage(PatternModels.ADVISORY + """
                    Propose one short cooking note by calling saveDemoNote exactly once.
                    Do not claim it was saved before the tool result confirms that.
                    When resuming with a tool result, report its outcome without requesting another operation.
                    """), new UserMessage(request));
            this.prompt = new Prompt(messages, ToolCallingChatOptions.builder().toolCallbacks(tool).build());
        }

        ConfirmationView view() {
            return viewWithCalls(modelCalls);
        }

        ConfirmationView viewWithCalls(int calls) {
            return new ConfirmationView(id, status, decision, proposed, saved, answer, calls);
        }
    }
}
