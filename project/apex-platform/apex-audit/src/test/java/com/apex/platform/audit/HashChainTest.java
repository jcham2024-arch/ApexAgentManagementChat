package com.apex.platform.audit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HashChainTest {

    private static final byte[] KEY = key();

    HashChain chain;

    private static byte[] key() {
        byte[] k = new byte[32];
        Arrays.fill(k, (byte) 0x42);
        return k;
    }

    @BeforeEach
    void setUp() {
        chain = new HashChain(KEY);
    }

    @Test
    void computesDeterministicHash() {
        String first = chain.compute("", "id-1", "login", 1000L);
        String second = chain.compute("", "id-1", "login", 1000L);

        assertThat(first).isEqualTo(second).isNotBlank();
    }

    @Test
    void differentInputsProduceDifferentHashes() {
        assertThat(chain.compute("", "id-1", "login", 1000L))
                .isNotEqualTo(chain.compute("", "id-2", "login", 1000L));
    }

    @Test
    void previousHashAffectsResult() {
        assertThat(chain.compute("", "id-1", "login", 1000L))
                .isNotEqualTo(chain.compute("abc", "id-1", "login", 1000L));
    }

    @Test
    void actionChangeAltersHash() {
        assertThat(chain.compute("", "id-1", "login", 1000L))
                .isNotEqualTo(chain.compute("", "id-1", "logout", 1000L));
    }

    @Test
    void tamperChangesTheHash() {
        String original = chain.compute("", "id-1", "transfer 10", 1000L);
        String tampered = chain.compute("", "id-1", "transfer 1000", 1000L);

        assertThat(tampered).isNotEqualTo(original);
    }

    @Test
    void shortKeyRejected() {
        assertThatThrownBy(() -> new HashChain(new byte[16]))
                .isInstanceOf(AuditLogException.class)
                .satisfies(e -> assertThat(((AuditLogException) e).category())
                        .isEqualTo("config"));
    }
}
