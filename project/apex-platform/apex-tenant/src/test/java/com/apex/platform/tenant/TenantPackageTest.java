package com.apex.platform.tenant;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TenantPackageTest {

    @Test
    void tenantPackageExists() {
        assertThat("com.apex.platform.tenant").isNotEmpty();
    }
}
