package com.apex.engine.sandbox;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * A command to execute in a sandbox. {@code binaryPath} must be an
 * absolute path to an allowlisted binary. {@code args} are passed as a
 * tokenized vector — never through a shell. {@code cwd} must exist and
 * be inside the sandbox's authorized root.
 */
public record SandboxCommand(Path binaryPath, List<String> args,
                             Path cwd, int timeoutSeconds) {
    public SandboxCommand {
        Objects.requireNonNull(binaryPath, "binaryPath");
        Objects.requireNonNull(args, "args");
        Objects.requireNonNull(cwd, "cwd");
        args = List.copyOf(args);
        if (timeoutSeconds <= 0) {
            throw new IllegalArgumentException("timeoutSeconds must be positive");
        }
        if (!binaryPath.isAbsolute()) {
            throw new IllegalArgumentException("binaryPath must be absolute");
        }
        if (!cwd.isAbsolute()) {
            throw new IllegalArgumentException("cwd must be absolute");
        }
    }
}
