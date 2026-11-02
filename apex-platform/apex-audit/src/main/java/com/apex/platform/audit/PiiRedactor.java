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
