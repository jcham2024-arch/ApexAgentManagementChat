package com.apex.engine.core.guard;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class InputInjectionGuardTest {

    @Test
    guardDisabledByDefault() {
        InputInjectionGuard guard = new InputInjectionGuard();
        assertThat(guard.isEnabled()).isFalse();
        assertThat(guard.canInject(false)).isFalse();
    }

    @Test
    guardEnabledExplicitly() {
        InputInjectionGuard guard = new InputInjectionGuard(true);
        assertThat(guard.isEnabled()).isTrue();
        assertThat(guard.canInject(false)).isTrue();
    }

    @Test
    guardBlocksPasswordFieldsByDefault() {
        InputInjectionGuard guard = new InputInjectionGuard();
        assertThat(guard.canInject(true)).isFalse();
    }

    @Test
    guardDoesNotBlockNonPasswordFields() {
        InputInjectionGuard guard = new InputInjectionGuard();
        assertThat(guard.canInject(false)).isTrue();
    }

    @Test
    rateLimitRejectsWhenExceeded() {
        InputInjectionGuard guard = new InputInjectionGuard(true, 2);
        assertThat(guard.canInject(false)).isTrue();
        assertThat(guard.canInject(false)).isTrue();
        assertThat(guard.canInject(false)).isFalse();
    }

    @Test
    guardPrunesOldEntries() throws InterruptedException {
        InputInjectionGuard guard = new InputInjectionGuard(true, 100);
        assertThat(guard.canInject(false)).isTrue();
        Thread.sleep(1100);
        assertThat(guard.canInject(false)).isTrue();
    }
}
