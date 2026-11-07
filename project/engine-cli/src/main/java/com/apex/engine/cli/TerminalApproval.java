package com.apex.engine.cli;

import com.apex.engine.core.port.ApprovalPort;
import com.apex.engine.core.port.model.ApprovalDecision;
import com.apex.engine.core.port.model.ApprovalRequest;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Reads an approval decision from a terminal-style prompt. Prompts via
 * {@code err} (stdout stays JSON-only). Accepts {@code y}/{@code Y} or
 * any string starting with those; everything else (including blank
 * input and EOF) is denial.
 *
 * <p>On EOF or IOException, returns denial — fail-safe. On no TTY
 * (running under CI), the caller should pass an {@link AutoApprove} or
 * {@link AutoDeny} explicitly; this class does not detect TTY.
 */
public final class TerminalApproval implements ApprovalPort {

    private final BufferedReader in;
    private final PrintStream prompt;

    public TerminalApproval(InputStream in, PrintStream prompt) {
        Objects.requireNonNull(in, "in");
        Objects.requireNonNull(prompt, "prompt");
        this.in = new BufferedReader(
                new InputStreamReader(in, StandardCharsets.UTF_8));
        this.prompt = prompt;
    }

    @Override
    public ApprovalDecision request(ApprovalRequest req) {
        Objects.requireNonNull(req, "req");
        prompt.println("=== APPROVAL REQUIRED ===");
        prompt.println("Tool: " + req.summary());
        prompt.println("Risk: " + req.riskLevel());
        prompt.print("Approve? [y/N]: ");
        prompt.flush();
        try {
            String line = in.readLine();
            if (line == null) {
                return new ApprovalDecision(req.id(), false,
                        "no input (EOF)");
            }
            String trimmed = line.trim();
            boolean approved = trimmed.startsWith("y")
                    || trimmed.startsWith("Y");
            return new ApprovalDecision(req.id(), approved,
                    approved ? "user-approved" : "user-denied");
        } catch (IOException e) {
            return new ApprovalDecision(req.id(), false,
                    "IO error: " + e.getMessage());
        }
    }
}
