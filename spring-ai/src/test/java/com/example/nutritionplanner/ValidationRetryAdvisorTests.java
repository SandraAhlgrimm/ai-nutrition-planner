package com.example.nutritionplanner;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ValidationRetryAdvisorTests {
    private static final String ORIGINAL = "MONDAY LUNCH; TUESDAY DINNER; profile vegetarian nuts 1800; quick recipes";
    private final CallAdvisorChain chain = mock(CallAdvisorChain.class);
    private final ChatClientRequest request = ChatClientRequest.builder()
            .prompt(new Prompt(List.of(new SystemMessage("Recipe Curator"), new UserMessage(ORIGINAL)))).build();
    private final ChatClientResponse response = ChatClientResponse.builder()
            .chatResponse(TestPlans.text("{\"value\":\"candidate\"}")).build();

    @Test
    void initialSuccessIsAuditedOnce() {
        verifyPassOn(1);
    }

    @Test
    void failedCandidateIsRevisedAndAudited() {
        verifyPassOn(2);
    }

    @Test
    void finalAllowedRevisionMayPassItsFourthAudit() {
        verifyPassOn(4);
    }

    private void verifyPassOn(int expectedAudits) {
        var audits = new AtomicInteger();
        var advisor = new ValidationRetryAdvisor<>(Candidate.class,
                _ -> verdict(audits.incrementAndGet() == expectedAudits));
        when(chain.nextCall(any())).thenReturn(response);
        when(chain.copy(advisor)).thenReturn(chain);
        assertThat(advisor.adviseCall(request, chain)).isSameAs(response);
        assertThat(audits).hasValue(expectedAudits);
        verify(chain, times(expectedAudits)).nextCall(any());
        verify(chain, times(expectedAudits - 1)).copy(advisor);
        assertThat(advisor.getOrder()).isLessThan(ToolCallingAdvisor.DEFAULT_ORDER);
    }

    @Test
    void exhaustionAuditsFourCandidatesAndThrowsInsteadOfReturningInvalidResponse() {
        var audits = new AtomicInteger();
        var advisor = new ValidationRetryAdvisor<>(Candidate.class, _ -> {
            audits.incrementAndGet();
            return verdict(false);
        });
        when(chain.nextCall(any())).thenReturn(response);
        when(chain.copy(advisor)).thenReturn(chain);
        assertThatThrownBy(() -> advisor.adviseCall(request, chain))
                .isInstanceOfSatisfying(NutritionPlanValidationException.class, exception -> {
                    assertThat(exception.audits()).isEqualTo(4);
                    assertThat(exception.feedback()).contains("Fix sodium");
                });
        assertThat(audits).hasValue(4);
        verify(chain, times(4)).nextCall(any());
        verify(chain, times(3)).copy(advisor);
    }

    @Test
    void everyRevisionKeepsOriginalPromptPersonaAndCollectedAnswersWithoutGrowingFeedback() {
        var advisor = new ValidationRetryAdvisor<>(Candidate.class, _ -> verdict(false), 3,
                () -> "Which cooking style? Quick");
        when(chain.nextCall(any())).thenReturn(response);
        when(chain.copy(advisor)).thenReturn(chain);
        assertThatThrownBy(() -> advisor.adviseCall(request, chain)).isInstanceOf(NutritionPlanValidationException.class);
        var requests = ArgumentCaptor.forClass(ChatClientRequest.class);
        verify(chain, times(4)).nextCall(requests.capture());
        for (var revision : requests.getAllValues().subList(1, 4)) {
            assertThat(revision.prompt().getSystemMessage().getText()).isEqualTo("Recipe Curator");
            assertThat(revision.prompt().getUserMessage().getText())
                    .containsOnlyOnce(ORIGINAL).containsOnlyOnce("# Revision")
                    .contains("candidate", "Fix sodium", "Which cooking style? Quick");
        }
    }

    @Test
    void zeroRevisionsStillAuditsInitialCandidate() {
        var advisor = new ValidationRetryAdvisor<>(Candidate.class, _ -> verdict(false), 0);
        when(chain.nextCall(any())).thenReturn(response);
        assertThatThrownBy(() -> advisor.adviseCall(request, chain)).isInstanceOf(NutritionPlanValidationException.class);
        verify(chain).nextCall(any());
        verify(chain, never()).copy(any());
    }

    @Test
    void invalidRevisionLimitsAreRejected() {
        assertThatThrownBy(() -> new ValidationRetryAdvisor<>(Candidate.class, _ -> verdict(true), 4))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ValidationRetryAdvisor<>(Candidate.class, _ -> verdict(true), -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void missingMalformedAndNullModelResponsesNeverPass() {
        var audits = new AtomicInteger();
        var advisor = new ValidationRetryAdvisor<>(Candidate.class, _ -> {
            audits.incrementAndGet();
            return verdict(true);
        });
        for (var invalid : List.of("", "not JSON", "null")) {
            when(chain.nextCall(any())).thenReturn(ChatClientResponse.builder()
                    .chatResponse(TestPlans.text(invalid)).build());
            assertThatThrownBy(() -> advisor.adviseCall(request, chain)).isInstanceOf(RuntimeException.class);
        }
        when(chain.nextCall(any())).thenReturn(ChatClientResponse.builder().build());
        assertThatThrownBy(() -> advisor.adviseCall(request, chain)).isInstanceOf(RuntimeException.class);
        assertThat(audits).hasValue(0);
    }

    @Test
    void missingAuditAndAuditExceptionPropagate() {
        when(chain.nextCall(any())).thenReturn(response);
        assertThatThrownBy(() -> new ValidationRetryAdvisor<>(Candidate.class, _ -> null).adviseCall(request, chain))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ValidationRetryAdvisor<>(Candidate.class, _ -> {
            throw new IllegalStateException("Auditor unavailable");
        }).adviseCall(request, chain)).hasMessage("Auditor unavailable");
    }

    private ValidationRetryAdvisor.ValidationResult verdict(boolean pass) {
        return new NutritionAuditValidationResult(pass, List.of(), pass ? "" : "Fix sodium");
    }

    record Candidate(String value) {}
}
