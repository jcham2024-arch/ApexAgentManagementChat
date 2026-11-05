package com.apex.chat.core.telemetry;

import com.apex.chat.common.identity.TenantId;
import com.apex.chat.core.ChatSessionId;
import com.apex.chat.core.event.ChatEventRegistry;
import com.apex.chat.core.event.ChatEventPublisher;
import com.apex.engine.core.port.TelemetryPort;
import com.apex.engine.core.port.model.TelemetrySpan;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Tier 0 implementation of {@link TelemetryPort}. Converts engine-level
 * {@link TelemetrySpan} records into {@link TelemetryEvent} instances and
 * publishes them via {@link ChatEventRegistry}.
 *
 * <p>Uses an {@link AtomicLong} for the per-session sequence counter to
 * guarantee monotonic ordering. All monetary values default to zero until
 * a pricing source is wired in (Phase C5+).
 */
public final class TelemetryPortImpl implements TelemetryPort {

    private final ChatEventRegistry registry;
    private final ChatSessionId sessionId;
    private final TenantId tenantId;
    private final AtomicLong sequence;

    public TelemetryPortImpl(ChatEventRegistry registry, ChatSessionId sessionId,
                             TenantId tenantId) {
        this.registry = registry != null ? registry : ChatEventPublisher.instance();
        this.sessionId = sessionId;
        this.tenantId = tenantId;
        this.sequence = new AtomicLong(0);
    }

    @Override
    public void record(TelemetrySpan span) {
        GenerationTelemetry generation = new GenerationTelemetry(
            0, 0, 0,
            Duration.ofMillis(span.durationMs()),
            Duration.ZERO,
            span.name()
        );
        CostEstimation cost = new CostEstimation(
            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, "USD", "default"
        );
        TelemetryEvent event = new TelemetryEvent(
            sessionId, tenantId, sequence.getAndIncrement(),
            Instant.now(), generation, cost
        );
        registry.publish(event);
    }
}
