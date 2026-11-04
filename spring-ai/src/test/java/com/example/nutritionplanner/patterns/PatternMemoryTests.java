package com.example.nutritionplanner.patterns;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.part.ReasoningPart;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.http.HttpStatus;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Timeout(10)
class PatternMemoryTests {
    private final PatternTestModel model = new PatternTestModel();
    private final OwnedPatternStatesTests.MutableClock clock = new OwnedPatternStatesTests.MutableClock();
    private final PatternMemoryService memory = new PatternMemoryService(model.models(), clock);

    @Test
    void nativeCompactionRetainsCompleteToolTurnsAndTheOriginalLog() {
        var session = memory.create("alice");
        for (int i = 1; i <= 4; i++) {
            model.tool("readDemoPantry", "{}").text("Pantry suggestion " + i);
            var result = memory.turn("alice", session.id(), new PatternRequest("Cooking preference " + i));
            assertThat(result.modelCalls()).isEqualTo(2);
        }
        var compacted = memory.get("alice", session.id());
        assertThat(compacted.turns()).isEqualTo(4);
        assertThat(compacted.loggedEvents()).isEqualTo(17);
        assertThat(compacted.compactions()).isEqualTo(1);
        assertThat(compacted.activeTurns()).isEqualTo(2);
        assertThat(compacted.activeMessages()).isEqualTo(8);
        assertThat(compacted.activeToolCalls()).isEqualTo(2);
        assertThat(compacted.activeToolResults()).isEqualTo(2);
        model.text("Next suggestion");
        memory.turn("alice", session.id(), new PatternRequest("Next turn"));
        var next = model.prompts.getLast();
        assertThat(next.getContents()).contains("Cooking preference 3", "Cooking preference 4")
                .doesNotContain("Cooking preference 1", "Cooking preference 2");
        assertThat(next.getInstructions()).filteredOn(ToolResponseMessage.class::isInstance).hasSize(2);
        assertThat(next.getInstructions()).doesNotHaveDuplicates();
    }

    @Test
    void usersAndSeparateSessionsNeverShareHistory() {
        var first = memory.create("alice");
        var second = memory.create("alice");
        var other = memory.create("bob");
        model.text("Noted");
        memory.turn("alice", first.id(), new PatternRequest("Alice private preference"));
        model.text("Noted separately");
        memory.turn("alice", second.id(), new PatternRequest("Another conversation"));
        assertThat(model.prompts.getLast().getContents()).doesNotContain("Alice private preference");
        model.text("Bob answer");
        memory.turn("bob", other.id(), new PatternRequest("Bob private preference"));
        assertThat(model.prompts.getLast().getContents()).doesNotContain("Alice private preference", "Another conversation");
        assertThatThrownBy(() -> memory.get("bob", first.id())).isInstanceOfSatisfying(PatternFailure.class,
                error -> assertThat(error.status()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> memory.turn("bob", first.id(), new PatternRequest("read")))
                .isInstanceOf(PatternFailure.class);
        assertThat(model.prompts).hasSize(3);
    }

    @Test
    void turnAndLifetimeBoundsPreventFurtherModelCalls() {
        var session = memory.create("alice");
        for (int turn = 0; turn < 8; turn++) {
            model.text("Advice " + turn);
            memory.turn("alice", session.id(), new PatternRequest("Cook " + turn));
        }
        assertThatThrownBy(() -> memory.turn("alice", session.id(), new PatternRequest("Ninth turn")))
                .isInstanceOfSatisfying(PatternFailure.class,
                        error -> assertThat(error.status()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
        clock.advance(Duration.ofMinutes(10));
        memory.expire();
        assertThatThrownBy(() -> memory.get("alice", session.id())).isInstanceOfSatisfying(PatternFailure.class,
                error -> assertThat(error.status()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThat(model.prompts).hasSize(8);
    }

    @Test
    void toolLimitIsAnExplicitErrorAndAFailedTurnCannotBeReused() {
        var session = memory.create("alice");
        model.tool("readDemoPantry", "{}").tool("readDemoPantry", "{}");
        assertThatThrownBy(() -> memory.turn("alice", session.id(), new PatternRequest("Read the pantry repeatedly")))
                .isInstanceOfSatisfying(PatternFailure.class,
                        error -> assertThat(error.status()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
        assertThat(memory.get("alice", session.id()).failed()).isTrue();
        assertThatThrownBy(() -> memory.turn("alice", session.id(), new PatternRequest("Try again")))
                .isInstanceOfSatisfying(PatternFailure.class,
                        error -> assertThat(error.status()).isEqualTo(HttpStatus.CONFLICT));
        assertThat(model.prompts).hasSize(2);
    }

    @Test
    void oversizedNonTextPartsAreRejectedBeforeTheyEnterTheSessionLog() {
        var session = memory.create("alice");
        model.respond(_ -> new ChatResponse(List.of(new Generation(AssistantMessage.builder()
                .content("A short answer").part(ReasoningPart.of("x".repeat(40_000))).build()))));
        assertThatThrownBy(() -> memory.turn("alice", session.id(), new PatternRequest("Cook")))
                .isInstanceOfSatisfying(PatternFailure.class,
                        error -> assertThat(error.status()).isEqualTo(HttpStatus.BAD_GATEWAY));
        var failed = memory.get("alice", session.id());
        assertThat(failed.failed()).isTrue();
        assertThat(failed.loggedEvents()).isEqualTo(1);
    }

    @Test
    void modelFailureClosesTheSessionInsteadOfReturningAPlaceholder() {
        var session = memory.create("alice");
        assertThatThrownBy(() -> memory.turn("alice", session.id(), new PatternRequest("Cook")))
                .isInstanceOf(IllegalStateException.class);
        assertThat(memory.get("alice", session.id()).failed()).isTrue();
        assertThat(model.prompts).hasSize(1);
    }
}
