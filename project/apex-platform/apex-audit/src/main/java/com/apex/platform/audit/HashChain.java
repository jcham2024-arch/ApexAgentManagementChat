package com.apex.platform.audit;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * HMAC-SHA256 hash of a single audit entry. The input is the
 * pipe-delimited concatenation of {@code previousHash | id | action |
 * at.toEpochMilli()}. Two entries with identical inputs produce
 * identical hashes. Any change to any field changes the hash.
 */
public final class HashChain {

    private static final String ALGORITHM = "HmacSHA256";

    private final byte[] key;

    public HashChain(byte[] key) {
        Objects.requireNonNull(key, "key");
        if (key.length < 32) {
            throw new AuditLogException("config",
                    "audit key must be at least 32 bytes");
        }
        this.key = key.clone();
    }

    public String compute(String previousHash, String id, String action,
                          long atMillis) {
        String input = previousHash + "|" + id + "|" + action + "|" + atMillis;
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(key, ALGORITHM));
            byte[] digest = mac.doFinal(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (GeneralSecurityException e) {
            throw new AuditLogException("config",
                    "HMAC init failed: " + e.getMessage(), e);
        }
    }
}
