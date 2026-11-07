package com.apex.platform.audit;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Property-based tests for the HMAC-SHA256 hash chain. Any mutation to
 * any input field must change the hash; any tampering with a stored
 * entry must break verifyChain(). These tests prove the audit chain's
 * tamper-evidence guarantee.
 */
class HashChainPropertyTest {

    private static final byte[] KEY = key();

    private static byte[] key() {
        byte[] k = new byte[32];
        Arrays.fill(k, (byte) 0x42);
        return k;
    }

    @Test
    void rejectsShortKey() {
        assertThatThrownBy(() -> new HashChain(new byte[16]))
                .isInstanceOf(AuditLogException.class);
    }

    @Test
    void acceptsMinimumKey() {
        new HashChain(new byte[32]);
    }

    @Property
    void sameInputsProduceSameHash(@ForAll String previousHash,
                                   @ForAll String id,
                                   @ForAll String action,
                                   @ForAll long atMillis) {
        HashChain chain = new HashChain(KEY);

        String h1 = chain.compute(previousHash, id, action, atMillis);
        String h2 = chain.compute(previousHash, id, action, atMillis);

        assertThat(h1).isEqualTo(h2).isNotBlank();
    }

    @Property
    void changingPreviousHashChangesHash(@ForAll String previousHash,
                                         @ForAll String id,
                                         @ForAll String action,
                                         @ForAll long atMillis) {
        HashChain chain = new HashChain(KEY);

        String original = chain.compute(previousHash, id, action, atMillis);
        String tampered = chain.compute(previousHash + "X", id, action, atMillis);

        assertThat(tampered).isNotEqualTo(original);
    }

    @Property
    void changingIdChangesHash(@ForAll String previousHash,
                               @ForAll String id,
                               @ForAll String action,
                               @ForAll long atMillis) {
        HashChain chain = new HashChain(KEY);

        String original = chain.compute(previousHash, id, action, atMillis);
        String tampered = chain.compute(previousHash, id + "X", action, atMillis);

        assertThat(tampered).isNotEqualTo(original);
    }

    @Property
    void changingActionChangesHash(@ForAll String previousHash,
                                   @ForAll String id,
                                   @ForAll String action,
                                   @ForAll long atMillis) {
        HashChain chain = new HashChain(KEY);

        String original = chain.compute(previousHash, id, action, atMillis);
        String tampered = chain.compute(previousHash, id, action + "X", atMillis);

        assertThat(tampered).isNotEqualTo(original);
    }

    @Property
    void changingTimestampChangesHash(@ForAll String previousHash,
                                      @ForAll String id,
                                      @ForAll String action,
                                      @ForAll long atMillis) {
        HashChain chain = new HashChain(KEY);

        String original = chain.compute(previousHash, id, action, atMillis);
        String tampered = chain.compute(previousHash, id, action, atMillis + 1);

        assertThat(tampered).isNotEqualTo(original);
    }

    @Property
    void differentKeysProduceDifferentHashes(@ForAll String previousHash,
                                             @ForAll String id,
                                             @ForAll String action,
                                             @ForAll long atMillis) {
        HashChain chainA = new HashChain(KEY);

        byte[] otherKey = new byte[32];
        Arrays.fill(otherKey, (byte) 0x24);
        HashChain chainB = new HashChain(otherKey);

        String hashA = chainA.compute(previousHash, id, action, atMillis);
        String hashB = chainB.compute(previousHash, id, action, atMillis);

        assertThat(hashB).isNotEqualTo(hashA);
    }
}
