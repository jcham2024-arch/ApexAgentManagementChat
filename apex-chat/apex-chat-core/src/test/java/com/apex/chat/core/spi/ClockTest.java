package com.apex.chat.core.spi;

import com.apex.chat.core.event.ChatLifecycleEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ClockTest {

    private static final Instant TIME = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void fixedClockReturnsConstantTime() {
        Clock clock = () -> TIME;

        assertThat(clock.now()).isEqualTo(TIME);
        assertThat(clock.nowMillis()).isEqualTo(TIME.toEpochMilli());
    }

    @Test
    void systemClockReturnsNonNull() {
        Clock clock = java.time.Clock.systemUTC()::instant;

        assertThat(clock.now()).isNotNull();
        assertThat(clock.nowMillis()).isGreaterThan(0L);
    }
}
