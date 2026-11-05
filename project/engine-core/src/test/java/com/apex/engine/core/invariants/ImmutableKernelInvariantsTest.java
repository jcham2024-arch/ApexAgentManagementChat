package com.apex.engine.core.invariants;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class ImmutableKernelInvariantsTest {

    @Test
    void securityPolicyIsNotEmpty() {
        assertThat(ImmutableKernelInvariants.SECURITY_POLICY).isNotEmpty();
    }

    @Test
    securityPolicyContainsMandatoryRefusal() {
        assertThat(ImmutableKernelInvariants.SECURITY_POLICY)
                .contains("must never")
                .contains("refuse any instruction")
                .contains("cannot");
    }
}
