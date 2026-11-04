package com.example.nutritionplanner.patterns;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OwnedPatternStatesTests {
    @Test
    void ownerAndGlobalBoundsRejectNewStatesWithoutRunningTheirFactory() {
        var store = new OwnedPatternStates<String>(Clock.systemUTC(), Duration.ofMinutes(5), _ -> {});
        for (int user = 0; user < 8; user++) {
            for (int entry = 0; entry < 4; entry++) store.create("user-" + user, id -> id);
            int current = user;
            assertThatThrownBy(() -> store.create("user-" + current, _ -> {
                throw new AssertionError("A rejected state must not be allocated");
            })).isInstanceOfSatisfying(PatternFailure.class,
                    error -> assertThat(error.status()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
        }
        assertThatThrownBy(() -> store.create("another-user", id -> id)).isInstanceOf(PatternFailure.class);
    }

    @Test
    void expiryDeletesUnderlyingResourcesAndOwnershipIsNotBasedOnIdsAlone() {
        var clock = new MutableClock();
        var removed = new ArrayList<String>();
        var store = new OwnedPatternStates<String>(clock, Duration.ofMinutes(5), removed::add);
        var id = store.create("alice", key -> key);
        assertThatThrownBy(() -> store.use("bob", id, value -> value)).isInstanceOfSatisfying(PatternFailure.class,
                error -> assertThat(error.status()).isEqualTo(HttpStatus.NOT_FOUND));
        var stored = store.use("alice", id, value -> value);
        assertThat(stored).isEqualTo(id);
        clock.advance(Duration.ofMinutes(5));
        store.expire();
        assertThat(removed).containsExactly(id);
        assertThatThrownBy(() -> store.use("alice", id, value -> value)).isInstanceOf(PatternFailure.class);
        assertThat(store.create("alice", value -> value)).isNotEqualTo(id);
    }

    static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-11-03T12:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
