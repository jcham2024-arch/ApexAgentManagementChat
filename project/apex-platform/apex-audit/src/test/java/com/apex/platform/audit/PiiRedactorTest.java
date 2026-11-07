package com.apex.platform.audit;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PiiRedactorTest {

    private final PiiRedactor redactor = new PiiRedactor();

    @Test
    void redactsEmail() {
        assertThat(redactor.redact("contact alice@example.com"))
                .isEqualTo("contact [EMAIL]");
    }

    @Test
    void redactsSsn() {
        assertThat(redactor.redact("ssn 123-45-6789")).isEqualTo("ssn [SSN]");
    }

    @Test
    void redactsAwsKey() {
        assertThat(redactor.redact("key AKIAIOSFODNN7EXAMPLE"))
                .isEqualTo("key [AWS_KEY]");
    }

    @Test
    void redactsGithubToken() {
        String token = "ghp_" + "a".repeat(36);

        assertThat(redactor.redact("token " + token))
                .isEqualTo("token [GITHUB_TOKEN]");
    }

    @Test
    void redactsApiKeyAndPemBlock() {
        assertThat(redactor.redact("sk-" + "b".repeat(24)))
                .isEqualTo("[API_KEY]");
        assertThat(redactor.redact(
                "-----BEGIN PRIVATE KEY-----\nsecret\n-----END PRIVATE KEY-----"))
                .isEqualTo("[PEM_BLOCK]");
    }

    @Test
    void leavesCleanTextUnchanged() {
        assertThat(redactor.redact("user logged in")).isEqualTo("user logged in");
    }

    @Test
    void containsPiiDetects() {
        assertThat(redactor.containsPii("alice@x.com")).isTrue();
        assertThat(redactor.containsPii("hello")).isFalse();
        assertThat(redactor.containsPii(null)).isFalse();
    }
}
