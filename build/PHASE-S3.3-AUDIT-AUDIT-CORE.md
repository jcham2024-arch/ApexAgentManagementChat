# PHASE-S3.3-AUDIT-AUDIT-CORE.md — Session Package, Part 3 of 8

**Version:** 1.0 · **Part 3 of 8:** §5.1–§5.3, AuditLogException, HashChain, PiiRedactor.
Parts: SESSION · CORE-PORT · AUDIT-CORE · AUDIT-LOG · CLI · AUDIT-TESTS ·
RUNNER-TESTS · CRITERIA, each `PHASE-S3.3-AUDIT-<part>.md`
(`AGENT_RULES.md` §2.13).

---
## 5. Files — `apex-audit`

Package `com.apex.platform.audit` for all files.

### 5.1 `AuditLogException.java`

```java
package com.apex.platform.audit;

/**
 * Failure from the audit log. Categories: {@code config},
 * {@code tamper}. Never thrown on a normal append — only on
 * construction (bad secret) or verification (broken chain).
 */
public final class AuditLogException extends RuntimeException {
    private final String category;

    public AuditLogException(String category, String message) {
        super(message);
        this.category = category;
    }

    public AuditLogException(String category, String message, Throwable cause) {
        super(message, cause);
        this.category = category;
    }

    public String category() {
        return category;
    }
}
```

### 5.2 `HashChain.java`

```java
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
```

### 5.3 `PiiRedactor.java`

```java
package com.apex.platform.audit;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Regex-based redaction of common PII categories. Replacement markers
 * are fixed strings (e.g. {@code [EMAIL]}) so identical inputs produce
 * identical outputs, preserving hash chain determinism.
 *
 * <p>Categories: email, phone, SSN, AWS access key, GitHub token,
 * OpenAI-style API key, PEM block. Applied in order; later patterns
 * see the output of earlier ones. Not a security boundary on its own
 * — the audit log is not the place where PII is sanitized from the
 * application; it is the place where PII that slipped through is
 * caught before being recorded.
 */
public final class PiiRedactor {

    private static final Pattern EMAIL = Pattern.compile(
            "[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

    private static final Pattern PHONE = Pattern.compile(
            "(?<![\\d-])(\\+?\\d{1,3}[ .-]?)?\\(?\\d{3}\\)?[ .-]?\\d{3}[ .-]?\\d{4}(?![\\d-])");

    private static final Pattern SSN = Pattern.compile(
            "\\b\\d{3}-\\d{2}-\\d{4}\\b");

    private static final Pattern AWS_KEY = Pattern.compile(
            "\\bAKIA[0-9A-Z]{16}\\b");

    private static final Pattern GITHUB_TOKEN = Pattern.compile(
            "\\bghp_[A-Za-z0-9]{36}\\b");

    private static final Pattern OPENAI_KEY = Pattern.compile(
            "\\bsk-[A-Za-z0-9]{20,}\\b");

    private static final Pattern PEM = Pattern.compile(
            "-----BEGIN [A-Z ]+-----.*?-----END [A-Z ]+-----",
            Pattern.DOTALL);

    public String redact(String input) {
        Objects.requireNonNull(input, "input");
        String out = input;
        out = EMAIL.matcher(out).replaceAll("[EMAIL]");
        out = PHONE.matcher(out).replaceAll("[PHONE]");
        out = SSN.matcher(out).replaceAll("[SSN]");
        out = AWS_KEY.matcher(out).replaceAll("[AWS_KEY]");
        out = GITHUB_TOKEN.matcher(out).replaceAll("[GITHUB_TOKEN]");
        out = OPENAI_KEY.matcher(out).replaceAll("[API_KEY]");
        out = PEM.matcher(out).replaceAll("[PEM_BLOCK]");
        return out;
    }

    /** Returns true if {@code input} contains anything the redactor would replace. */
    public boolean containsPii(String input) {
        if (input == null) return false;
        return !input.equals(redact(input));
    }

    /** Unused but exported for future policy decisions. */
    static boolean matchesAnyPattern(String input) {
        if (input == null) return false;
        return EMAIL.matcher(input).find()
                || PHONE.matcher(input).find()
                || SSN.matcher(input).find()
                || AWS_KEY.matcher(input).find()
                || GITHUB_TOKEN.matcher(input).find()
                || OPENAI_KEY.matcher(input).find()
                || PEM.matcher(input).find();
    }
}
```
