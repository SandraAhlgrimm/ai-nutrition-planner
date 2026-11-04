package com.example.nutritionplanner.patterns;

import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Function;

final class OwnedPatternStates<T> {
    static final int MAX_PER_OWNER = 4;
    static final int MAX_TOTAL = 32;
    private final Map<String, Entry<T>> entries = new ConcurrentHashMap<>();
    private final Clock clock;
    private final Duration lifetime;
    private final Consumer<String> onRemove;

    OwnedPatternStates(Clock clock, Duration lifetime, Consumer<String> onRemove) {
        this.clock = clock;
        this.lifetime = lifetime;
        this.onRemove = onRemove;
    }

    synchronized String create(String owner, Function<String, T> factory) {
        expire();
        if (entries.size() >= MAX_TOTAL
                || entries.values().stream().filter(entry -> entry.owner.equals(owner)).count() >= MAX_PER_OWNER) {
            throw PatternFailure.limit("At most four states per user and 32 states per pattern may be retained");
        }
        var id = UUID.randomUUID().toString();
        entries.put(id, new Entry<>(owner, clock.instant().plus(lifetime), factory.apply(id)));
        return id;
    }

    <R> R use(String owner, String id, Function<T, R> action) {
        var entry = entries.get(id);
        if (entry == null || !entry.owner.equals(owner)) throw missing();
        if (!entry.lock.tryLock()) {
            throw new PatternFailure(HttpStatus.CONFLICT, "This pattern state is already in use");
        }
        try {
            if (entries.get(id) != entry || !clock.instant().isBefore(entry.expiresAt)) {
                remove(id, entry);
                throw missing();
            }
            return action.apply(entry.value);
        } finally {
            entry.lock.unlock();
        }
    }

    void discard(String owner, String id) {
        use(owner, id, _ -> {
            var entry = entries.get(id);
            if (entry != null) remove(id, entry);
            return true;
        });
    }

    void expire() {
        entries.forEach((id, entry) -> {
            if (!clock.instant().isBefore(entry.expiresAt) && entry.lock.tryLock()) {
                try {
                    remove(id, entry);
                } finally {
                    entry.lock.unlock();
                }
            }
        });
    }

    private void remove(String id, Entry<T> entry) {
        if (entries.remove(id, entry)) onRemove.accept(id);
    }

    private static PatternFailure missing() {
        return new PatternFailure(HttpStatus.NOT_FOUND, "Pattern state not found or expired");
    }

    private static final class Entry<T> {
        final String owner;
        final Instant expiresAt;
        final T value;
        final ReentrantLock lock = new ReentrantLock();

        Entry(String owner, Instant expiresAt, T value) {
            this.owner = owner;
            this.expiresAt = expiresAt;
            this.value = value;
        }
    }
}
