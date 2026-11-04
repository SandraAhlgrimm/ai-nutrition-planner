package com.example.nutritionplanner.patterns;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.http.HttpStatus;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Timeout(10)
class PatternConfirmationTests {
    private final PatternTestModel model = new PatternTestModel();
    private final OwnedPatternStatesTests.MutableClock clock = new OwnedPatternStatesTests.MutableClock();
    private final PatternConfirmationService confirmations = new PatternConfirmationService(model.models(), clock);
    private static final PatternRequest REQUEST = new PatternRequest("Remember to rinse the lentils");

    @ParameterizedTest
    @EnumSource(PatternConfirmationService.Decision.class)
    void nativePauseAndDecisionsOnlyExecuteTheApprovedStoredCall(PatternConfirmationService.Decision decision) {
        model.tool("saveDemoNote", "{\"text\":\"Rinse the lentils\"}");
        var pending = confirmations.start("alice", REQUEST);
        assertThat(pending.status()).isEqualTo(PatternConfirmationService.Status.PENDING);
        assertThat(pending.proposed().text()).isEqualTo("Rinse the lentils");
        assertThat(pending.saved()).isNull();
        assertThat(pending.decision()).isNull();
        assertThat(pending.modelCalls()).isEqualTo(1);
        assertThat(model.prompts).hasSize(1);
        assertThat(confirmations.get("alice", pending.id())).isEqualTo(pending);
        model.text(decision == PatternConfirmationService.Decision.APPROVE ? "The demo note was saved." : "Nothing was saved.");

        var completed = confirmations.decide("alice", pending.id(),
                new PatternConfirmationService.DecisionRequest(decision));

        assertThat(completed.status()).isEqualTo(PatternConfirmationService.Status.COMPLETED);
        assertThat(completed.decision()).isEqualTo(decision);
        assertThat(completed.modelCalls()).isEqualTo(2);
        if (decision == PatternConfirmationService.Decision.APPROVE) {
            assertThat(completed.saved()).isEqualTo(pending.proposed());
        } else {
            assertThat(completed.saved()).isNull();
        }
        assertThat(model.prompts.getLast().getInstructions()).filteredOn(ToolResponseMessage.class::isInstance)
                .hasSize(1).allSatisfy(message -> {
                    var result = ((ToolResponseMessage) message).getResponses().getFirst().responseData();
                    assertThat(result).contains(decision == PatternConfirmationService.Decision.APPROVE
                            ? "Saved this temporary demo note" : "The user denied");
                });
        assertThat(confirmations.get("alice", pending.id())).isEqualTo(completed);
        assertThatThrownBy(() -> confirmations.decide("alice", pending.id(),
                new PatternConfirmationService.DecisionRequest(decision))).isInstanceOfSatisfying(PatternFailure.class,
                error -> assertThat(error.status()).isEqualTo(HttpStatus.CONFLICT));
        assertThat(model.prompts).hasSize(2);
    }

    @Test
    void ownershipAndExpiryAreCheckedBeforeExecutionOrResume() {
        model.tool("saveDemoNote", "{\"text\":\"Rinse lentils\"}");
        var pending = confirmations.start("alice", REQUEST);
        assertThatThrownBy(() -> confirmations.get("bob", pending.id())).isInstanceOfSatisfying(PatternFailure.class,
                error -> assertThat(error.status()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> confirmations.decide("bob", pending.id(), approve()))
                .isInstanceOf(PatternFailure.class);
        assertThat(confirmations.get("alice", pending.id()).saved()).isNull();
        clock.advance(Duration.ofMinutes(5));
        confirmations.expire();
        assertThatThrownBy(() -> confirmations.decide("alice", pending.id(), approve()))
                .isInstanceOfSatisfying(PatternFailure.class,
                        error -> assertThat(error.status()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThat(model.prompts).hasSize(1);
    }

    @Test
    void concurrentApprovalCannotExecuteTheToolTwice() throws Exception {
        model.tool("saveDemoNote", "{\"text\":\"Rinse lentils\"}");
        var pending = confirmations.start("alice", REQUEST);
        var resuming = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        model.respond(_ -> {
            resuming.countDown();
            try {
                release.await();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(exception);
            }
            return PatternTestModel.answer("Saved.");
        });
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(() -> confirmations.decide("alice", pending.id(), approve()));
            try {
                assertThat(resuming.await(3, TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(() -> confirmations.decide("alice", pending.id(), approve()))
                        .isInstanceOfSatisfying(PatternFailure.class,
                                error -> assertThat(error.status()).isEqualTo(HttpStatus.CONFLICT));
            } finally {
                release.countDown();
            }
            assertThat(first.get().saved().text()).isEqualTo("Rinse lentils");
        }
        assertThat(model.prompts).hasSize(2);
    }

    @Test
    void aResumeFailureRemainsExplicitAndCannotReplayAnAlreadyExecutedOperation() {
        model.tool("saveDemoNote", "{\"text\":\"Rinse lentils\"}");
        var pending = confirmations.start("alice", REQUEST);
        assertThatThrownBy(() -> confirmations.decide("alice", pending.id(), approve()))
                .isInstanceOf(IllegalStateException.class);
        var failed = confirmations.get("alice", pending.id());
        assertThat(failed.status()).isEqualTo(PatternConfirmationService.Status.FAILED);
        assertThat(failed.saved().text()).isEqualTo("Rinse lentils");
        assertThat(failed.answer()).isNull();
        assertThat(failed.modelCalls()).isEqualTo(2);
        assertThatThrownBy(() -> confirmations.decide("alice", pending.id(), approve()))
                .isInstanceOf(PatternFailure.class);
        assertThat(model.prompts).hasSize(2);
    }

    @Test
    void aSecondModelRequestedOperationDoesNotInheritTheApproval() {
        model.tool("saveDemoNote", "{\"text\":\"Original note\"}");
        var pending = confirmations.start("alice", REQUEST);
        model.tool("saveDemoNote", "{\"text\":\"Unauthorized replacement\"}");
        assertThatThrownBy(() -> confirmations.decide("alice", pending.id(), approve()))
                .isInstanceOf(PatternFailure.class);
        var failed = confirmations.get("alice", pending.id());
        assertThat(failed.status()).isEqualTo(PatternConfirmationService.Status.FAILED);
        assertThat(failed.saved().text()).isEqualTo("Original note");
        assertThat(model.prompts).hasSize(2);
    }

    @Test
    void missingNativePauseAndInvalidArgumentsDoNotCreateFakePendingApprovals() {
        for (int attempt = 0; attempt < 6; attempt++) {
            model.text("Pretending to save a note is not a tool invocation");
            assertThatThrownBy(() -> confirmations.start("alice", REQUEST)).isInstanceOf(PatternFailure.class);
        }
        for (var arguments : new String[]{"{\"text\":\"\"}", "{\"text\":null}",
                "{\"text\":\"" + "x".repeat(281) + "\"}", "{\"text\":\"Valid\",\"owner\":\"bob\"}"}) {
            model.tool("saveDemoNote", arguments);
            assertThatThrownBy(() -> confirmations.start("alice", REQUEST)).isInstanceOf(RuntimeException.class);
        }
        model.tool("saveDemoNote", "{\"text\":\"A valid proposal\"}");
        assertThat(confirmations.start("alice", REQUEST).status()).isEqualTo(PatternConfirmationService.Status.PENDING);
    }

    @Test
    void providerCallsWithoutIdsUseNativePositionalDecisionKeys() {
        model.respond(_ -> PatternTestModel.toolResponse("", "saveDemoNote", "{\"text\":\"Lentils\"}"));
        var pending = confirmations.start("alice", REQUEST);
        model.respond(_ -> PatternTestModel.answer("Saved."));
        assertThat(confirmations.decide("alice", pending.id(), approve()).saved().text()).isEqualTo("Lentils");
    }

    private static PatternConfirmationService.DecisionRequest approve() {
        return new PatternConfirmationService.DecisionRequest(PatternConfirmationService.Decision.APPROVE);
    }
}
