package com.apex.chat.common.identity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class TenantIdTest {

    @Test
    void acceptsNonBlankValue() {
        assertThat(new TenantId("tenant-1").value()).isEqualTo("tenant-1");
    }

    @Test
    void rejectsNull() {
        assertThatNullPointerException()
                .isThrownBy(() -> new TenantId(null))
                .withMessage("TenantId value");
    }

    @Test
    void rejectsBlank() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new TenantId("   "))
                .withMessage("TenantId must not be blank");
    }

    @Test
    void equalityIsValueBased() {
        TenantId first = new TenantId("tenant-1");
        TenantId second = new TenantId("tenant-1");

        assertThat(first).isEqualTo(second).hasSameHashCodeAs(second);
        assertThat(first).isNotEqualTo(new TenantId("tenant-2"));
    }
}
