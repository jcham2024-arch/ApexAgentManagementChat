package com.apex.chat.core.telemetry;

import com.apex.chat.common.identity.TenantId;
import com.apex.chat.core.ChatSessionId;
import com.apex.chat.core.SessionState;
import com.apex.chat.core.event.ChatEventType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TelemetryEventTest {

    private static final ChatSessionId SESSION_ID = new ChatSessionId("s-1");
    private static final TenantId TENANT_ID = new TenantId("t-1");
    private static final Instant TIME = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void createsValidTelemetryEvent() {
        GenerationTelemetry generation = new GenerationTelemetry(
                100, 50, 10,
                Duration.ofSeconds(5),
                Duration.ofMillis(500),
                "gpt-4");
        CostEstimation cost = new CostEstimation(
                new BigDecimal("0.001500"),
                new BigDecimal("0.006000"),
                new BigDecimal("0.003000"),
                "USD", "tier-1");

        TelemetryEvent event = new TelemetryEvent(
                SESSION_ID, TENANT_ID, 0, TIME, generation, cost);

        assertThat(event.sessionId()).isEqualTo(SESSION_ID);
        assertThat(event.tenantId()).isEqualTo(TENANT_ID);
        assertThat(event.occurredAt()).isEqualTo(TIME);
        assertThat(event.sequence()).isEqualTo(0);
        assertThat(event.type()).isEqualTo(ChatEventType.TELEMETRY_RECORDED);
        assertThat(event.generation()).isEqualTo(generation);
        assertThat(event.cost()).isEqualTo(cost);
        assertThat(event.cost().total()).isEqualByComparingTo("0.010500");
    }

    @Test
    void rejectsNullSessionId() {
        GenerationTelemetry generation = new GenerationTelemetry(
                100, 50, 10, Duration.ofSeconds(5), Duration.ZERO, "model");
        CostEstimation cost = new CostEstimation(
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                "USD", "tier-1");

        assertThatThrownBy(() -> new TelemetryEvent(
                null, TENANT_ID, 0, TIME, generation, cost))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsNegativeSequence() {
        GenerationTelemetry generation = new GenerationTelemetry(
                100, 50, 10, Duration.ofSeconds(5), Duration.ZERO, "model");
        CostEstimation cost = new CostEstimation(
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                "USD", "tier-1");

        assertThatThrownBy(() -> new TelemetryEvent(
                SESSION_ID, TENANT_ID, -1, TIME, generation, cost))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
