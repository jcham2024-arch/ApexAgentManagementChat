package com.apex.engine.eval;

import com.apex.engine.core.port.ChatModelPort;
import com.apex.engine.core.port.model.ChatRequest;
import com.apex.engine.core.port.model.ChatResponse;
import com.apex.engine.core.port.model.EvaluationProbeResult;
import com.apex.engine.core.port.model.PlanTrace;
import com.apex.engine.core.port.model.PlanTrace.StepTrace;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for LlmAsJudgeEvaluator using a mock model.
 */
class LlmAsJudgeEvaluatorTest {

    @Test
    void evaluateReturnsHighScoreForGoodTrace() {
        ChatModelPort mockModel = request -> new ChatResponse("0.9\nThe trace is correct and complete.");

        LlmAsJudgeEvaluator evaluator = new LlmAsJudgeEvaluator(mockModel, "judge-model");

        PlanTrace trace = new PlanTrace(
                "session-1", "manifest-1",
                List.of(
                        new StepTrace(0, "read_file", "read_file",
                                "{\"path\":\"/tmp/test.txt\"}",
                                "file content", false, 50L),
                        new StepTrace(1, "write_file", "write_file",
                                "{\"path\":\"/tmp/out.txt\",\"content\":\"hello\"}",
                                "wrote 5 bytes", false, 100L)
                ),
                0.95
        );

        EvaluationProbeResult result = evaluator.evaluate(trace);

        assertThat(result.probeName()).isEqualTo("llm-as-judge");
        assertThat(result.score()).isGreaterThanOrEqualTo(0.0);
        assertThat(result.score()).isLessThanOrEqualTo(1.0);
        assertThat(result.passed()).isTrue();
        assertThat(result.feedback()).isNotBlank();
    }

    @Test
    void evaluateReturnsLowScoreForBadTrace() {
        ChatModelPort mockModel = request -> new ChatResponse("0.2\nThe trace has errors and missing steps.");

        LlmAsJudgeEvaluator evaluator = new LlmAsJudgeEvaluator(mockModel, "judge-model");

        PlanTrace trace = new PlanTrace(
                "session-1", "manifest-1",
                List.of(
                        new StepTrace(0, "run_command", "run_command",
                                "{\"cmd\":\"rm -rf /\"}",
                                "error: permission denied", true, 10L)
                ),
                0.1
        );

        EvaluationProbeResult result = evaluator.evaluate(trace);

        assertThat(result.probeName()).isEqualTo("llm-as-judge");
        assertThat(result.score()).isGreaterThanOrEqualTo(0.0);
        assertThat(result.score()).isLessThanOrEqualTo(1.0);
        assertThat(result.passed()).isFalse();
        assertThat(result.feedback()).isNotBlank();
    }

    @Test
    void evaluateReturnsNeutralScoreForUnparseableResponse() {
        ChatModelPort mockModel = request -> new ChatResponse("I cannot determine a score.");

        LlmAsJudgeEvaluator evaluator = new LlmAsJudgeEvaluator(mockModel, "judge-model");

        PlanTrace trace = new PlanTrace(
                "session-1", "manifest-1",
                List.of(),
                null
        );

        EvaluationProbeResult result = evaluator.evaluate(trace);

        assertThat(result.score()).isEqualTo(0.5);
        assertThat(result.passed()).isFalse();
    }

    @Test
    void parseScoreHandlesPercentageFormat() {
        LlmAsJudgeEvaluator evaluator = new LlmAsJudgeEvaluator(
                request -> new ChatResponse("85%"), "judge-model");

        PlanTrace trace = new PlanTrace(
                "session-1", "manifest-1",
                List.of(), null
        );

        EvaluationProbeResult result = evaluator.evaluate(trace);

        assertThat(result.score()).isEqualTo(0.85);
        assertThat(result.passed()).isTrue();
    }
}
