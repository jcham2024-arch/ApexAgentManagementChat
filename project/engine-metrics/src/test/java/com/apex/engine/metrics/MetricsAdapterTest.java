package com.apex.engine.metrics;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MetricsAdapterTest {

    @Test
    void counterAndHistogram() {
        MetricsAdapter adapter = MetricsAdapter.create();
        adapter.counter("steps.executed", 3L);
        adapter.counter("steps.executed", 2L);
        adapter.histogram("step.duration.ms", 10.0);
        adapter.histogram("step.duration.ms", 20.0);

        assertThat(adapter.counterValue("steps.executed")).isEqualTo(5L);
        assertThat(adapter.counterValue("missing")).isZero();

        MetricsAdapter.HistogramSnapshot snap =
                adapter.histogramSnapshot().get("step.duration.ms");
        assertThat(snap.count()).isEqualTo(2L);
        assertThat(snap.min()).isEqualTo(10L);
        assertThat(snap.max()).isEqualTo(20L);
    }
}
