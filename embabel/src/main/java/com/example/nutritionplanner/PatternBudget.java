package com.example.nutritionplanner;

import com.embabel.agent.core.EarlyTerminationPolicy;
import com.embabel.agent.core.ProcessControl;
import com.embabel.agent.core.ProcessOptions;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class PatternBudget {

    static final String CONTEXT_KEY = PatternBudget.class.getName();
    private final int maxModelCalls;
    private final int maxToolCalls;
    private final List<String> actions = new ArrayList<>();
    private int modelCalls;
    private int toolCalls;
    private @Nullable PatternFailure failure;

    PatternBudget(int maxModelCalls, int maxToolCalls) {
        this.maxModelCalls = maxModelCalls;
        this.maxToolCalls = maxToolCalls;
    }

    synchronized void beforeModelCall() {
        check();
        if (modelCalls >= maxModelCalls) {
            throw fail(PatternFailure.Reason.LIMIT_EXCEEDED, "Pattern model-call budget exhausted.");
        }
        modelCalls++;
    }

    synchronized void acceptToolCalls(int count) {
        check();
        if (count > 2 || toolCalls + count > maxToolCalls) {
            throw fail(PatternFailure.Reason.LIMIT_EXCEEDED, "Pattern tool-call or fan-out budget exceeded.");
        }
        toolCalls += count;
    }

    synchronized void enterAction(String name) {
        check();
        if (actions.contains(name)) {
            throw fail(PatternFailure.Reason.LIMIT_EXCEEDED, "Each pattern worker may run only once.");
        }
        actions.add(name);
    }

    synchronized PatternFailure fail(PatternFailure.Reason reason, String message) {
        return fail(new PatternFailure(reason, message));
    }

    synchronized PatternFailure fail(PatternFailure cause) {
        if (failure == null) {
            failure = cause;
        }
        return failure;
    }

    synchronized void check() {
        if (failure != null) {
            throw failure;
        }
    }

    synchronized int modelCalls() {
        return modelCalls;
    }

    synchronized List<String> actions() {
        return List.copyOf(actions);
    }

    ProcessOptions processOptions(int maxActions) {
        return new ProcessOptions().withEphemeral(true)
                .withToolCallContext(Map.of(CONTEXT_KEY, this))
                .withProcessControl(new ProcessControl().withEarlyTerminationPolicy(
                        EarlyTerminationPolicy.maxActions(maxActions)));
    }
}
