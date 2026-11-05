package com.apex.engine.store;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StorageExceptionTest {

    @Test
    void storesCategoryAndMessage() {
        StorageException e = new StorageException("append", "disk full");

        assertThat(e.category()).isEqualTo("append");
        assertThat(e).hasMessage("disk full");
    }

    @Test
    void storesCause() {
        RuntimeException cause = new RuntimeException("underlying");
        StorageException e = new StorageException("query", "timeout", cause);

        assertThat(e.category()).isEqualTo("query");
        assertThat(e).hasMessage("timeout");
        assertThat(e.getCause()).isSameAs(cause);
    }

    @Test
    void isRuntimeException() {
        assertThat(new StorageException("init", "boom")).isInstanceOf(RuntimeException.class);
    }
}
