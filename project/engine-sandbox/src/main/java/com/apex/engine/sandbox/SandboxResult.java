package com.apex.engine.sandbox;

import java.util.Objects;

/**
 * Result of a sandboxed execution. {@code exitCode} is 0 on success.
 * {@code output} is the merged stdout+stderr, capped at the sandbox's
 * output limit. {@code timedOut} is true if the sandbox was killed for
 * exceeding its timeout.
 */
public record SandboxResult(int exitCode, String output, boolean timedOut) {
    public SandboxResult {
        Objects.requireNonNull(output, "output");
    }
}
