package com.apex.chat.core.telemetry;

import com.apex.chat.common.identity.TenantId;
import com.apex.chat.core.ChatSessionId;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GenerationTelemetryTest {

    private static final Instant TIME = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void createsValidTelemetry() {
        GenerationTelemetry telemetry = new GenerationTelemetry(
                100, 50, 10,
                Duration.ofSeconds(5),
                Duration.ofMillis(500),
                "gpt-4");

        assertThat(telemetry.promptTokens()).isEqualTo(100);
        assertThat(telemetry.completionTokens()).isEqualTo(50);
        assertThat(telemetry.reasoningTokens()).isEqualTo(10);
        assertThat(telemetry.totalTokens()).isEqualTo(160);
        assertThat(telemetry.latency()).isEqualTo(Duration.ofSeconds(5));
        assertThat(telemetry.timeToFirstToken()).isEqualTo(Duration.ofMillis(500));
        assertThat(telemetry.modelId()).isEqualTo("gpt-4");
    }

    @Test
    void rejectsNegativePromptTokens() {
        assertThatThrownBy(() -> new GenerationTelemetry(
                -1, 50, 10, Duration.ofSeconds(5), Duration.ZERO, "model"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNegativeCompletionTokens() {
        assertThatThrownBy(() -> new GenerationTelemetry(
                100, -1, 10, Duration.ofSeconds(5), Duration.ZERO, "model"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNegativeReasoningTokens() {
        assertThatThrownBy(() -> new GenerationTelemetry(
                100, 50, -1, Duration.ofSeconds(5), Duration.ZERO, "model"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsBlankModelId() {
        assertThatThrownBy(() -> new GenerationTelemetry(
                100, 50, 10, Duration.ofSeconds(5), Duration.ZERO, ""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void zeroReasoningTokensIsValid() {
        GenerationTelemetry telemetry = new GenerationTelemetry(
                100, 50, 0, Duration.ofSeconds(5), Duration.ZERO, "model");

        assertThat(telemetry.totalTokens()).isEqualTo(150);
    }
}
