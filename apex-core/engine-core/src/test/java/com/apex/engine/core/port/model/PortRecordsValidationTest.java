package com.apex.engine.core.port.model;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Collection immutability and numeric or blank validation for the port
 * records that carry a list, a count, a duration, or a name.
 */
class PortRecordsValidationTest {

    @Test
    void chatRequestCopiesPromptParts() {
        List<String> parts = new ArrayList<>(List.of("hi"));
        ChatRequest request = new ChatRequest("m", parts);

        parts.add("late");

        assertThat(request.promptParts()).containsExactly("hi");
        assertThatThrownBy(() -> request.promptParts().add("nope"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void planRequestCopiesAvailableActionIds() {
        List<String> actions = new ArrayList<>(List.of("read"));
        PlanRequest request = new PlanRequest("goal", actions);

        actions.add("late");

        assertThat(request.availableActionIds()).containsExactly("read");
        assertThatThrownBy(() -> request.availableActionIds().add("nope"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void planResultCopiesChosenActionIds() {
        List<String> chosen = new ArrayList<>(List.of("read"));
        PlanResult result = new PlanResult(chosen);

        chosen.add("late");

        assertThat(result.chosenActionIds()).containsExactly("read");
        assertThatThrownBy(() -> result.chosenActionIds().add("nope"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void toolResultRejectsNegativeDuration() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ToolResult("c1", false, "out", -1L))
                .withMessage("durationMs must be >= 0");
    }

    @Test
    void stepLogEntryRejectsNegativeIndex() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new StepLogEntry("s1", "s", -1, "plan", 0L))
                .withMessage("index must be >= 0");
    }

    @Test
    void stepLogEntryRejectsNegativeDuration() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new StepLogEntry("s1", "s", 0, "plan", -1L))
                .withMessage("durationMs must be >= 0");
    }

    @Test
    void telemetrySpanRejectsNegativeDuration() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new TelemetrySpan("plan", -1L))
                .withMessage("durationMs must be >= 0");
    }

    @Test
    void secretRefRejectsBlankName() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new SecretRef("   "))
                .withMessage("SecretRef name must not be blank");
    }

    @Test
    void approvalDecisionNormalizesNullComment() {
        assertThat(new ApprovalDecision("r1", true, null).comment()).isEmpty();
        assertThat(new ApprovalDecision("r1", false, "no").comment())
                .isEqualTo("no");
    }
}
