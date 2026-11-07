package com.apex.engine.otel;

import com.apex.engine.core.port.model.TelemetrySpan;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OtelAdapterTest {

    @Test
    void recordsSpanWithoutThrowing() {
        OtelAdapter adapter = new OtelAdapter();
        adapter.record(new TelemetrySpan("plan-step", 42L));
        assertThat(adapter.counterValue("steps.executed")).isZero();
    }

    @Test
    void countsAndHistograms() {
        OtelAdapter adapter = new OtelAdapter();
        adapter.counter("steps.executed", 3L);
        adapter.histogram("step.duration.ms", 10.0);
        adapter.histogram("step.duration.ms", 20.0);
        assertThat(adapter.counterValue("steps.executed")).isEqualTo(3L);
    }
}
