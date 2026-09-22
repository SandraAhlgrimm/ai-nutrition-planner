package com.example.nutritionplanner;

import org.jspecify.annotations.Nullable;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;

import java.security.Principal;
import java.util.function.Supplier;

public final class PlannerIdentity {

    private static final ScopedValue<String> USERNAME = ScopedValue.newInstance();

    private PlannerIdentity() {}

    static String username() {
        if (!USERNAME.isBound()) {
            throw new AuthenticationCredentialsNotFoundException("An authenticated nutrition planner user is required");
        }
        return USERNAME.get();
    }

    static <T> T callAs(@Nullable Principal principal, Supplier<T> task) {
        if (principal == null || principal.getName().isBlank()
                || principal instanceof AnonymousAuthenticationToken
                || principal instanceof Authentication authentication && !authentication.isAuthenticated()) {
            throw new AuthenticationCredentialsNotFoundException("An authenticated nutrition planner user is required");
        }
        return ScopedValue.where(USERNAME, principal.getName()).call(task::get);
    }

    public static Runnable propagate(Runnable task) {
        if (!USERNAME.isBound()) {
            return task;
        }
        var username = USERNAME.get();
        return () -> ScopedValue.where(USERNAME, username).run(task);
    }
}
