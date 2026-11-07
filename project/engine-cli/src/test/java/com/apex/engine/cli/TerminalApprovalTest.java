package com.apex.engine.cli;

import com.apex.engine.core.port.model.ApprovalDecision;
import com.apex.engine.core.port.model.ApprovalRequest;
import com.apex.engine.planner.RiskLevel;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Terminal approval behaviour: prompts on the supplied stream, approves
 * only on an explicit y/Y, and denies on everything else including EOF
 * and IO failure. The S3.4 package arrived truncated before its 5.6
 * section, so these cases are derived from the contract in 4.2 and from
 * 1.6 (prompt on err, fail-safe denial).
 */
class TerminalApprovalTest {

    private static final String REQUEST_ID = "req-1";

    private ByteArrayOutputStream promptBytes;
    private PrintStream prompt;

    private TerminalApproval approvalFor(String stdin) {
        promptBytes = new ByteArrayOutputStream();
        prompt = new PrintStream(promptBytes, true, StandardCharsets.UTF_8);
        return new TerminalApproval(stdinStream(stdin), prompt);
    }

    private static InputStream stdinStream(String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }

    private static ApprovalRequest request() {
        return new ApprovalRequest(REQUEST_ID, "tool_call:run_command",
                RiskLevel.L4);
    }

    private String promptText() {
        return promptBytes.toString(StandardCharsets.UTF_8);
    }

    @Test
    void approvesOnLowerCaseY() {
        ApprovalDecision decision = approvalFor("y\n").request(request());

        assertThat(decision.approved()).isTrue();
        assertThat(decision.comment()).isEqualTo("user-approved");
    }

    @Test
    void approvesOnWordStartingWithY() {
        assertThat(approvalFor("Yes please\n").request(request()).approved())
                .isTrue();
    }

    @Test
    void deniesOnN() {
        ApprovalDecision decision = approvalFor("n\n").request(request());

        assertThat(decision.approved()).isFalse();
        assertThat(decision.comment()).isEqualTo("user-denied");
    }

    @Test
    void deniesOnUnexpectedInput() {
        assertThat(approvalFor("maybe later\n").request(request()).approved())
                .isFalse();
    }

    @Test
    void deniesOnBlankLine() {
        assertThat(approvalFor("\n").request(request()).approved()).isFalse();
    }

    @Test
    void deniesOnEof() {
        ApprovalDecision decision = approvalFor("").request(request());

        assertThat(decision.approved()).isFalse();
        assertThat(decision.comment()).contains("EOF");
    }

    @Test
    void deniesOnIoError() {
        promptBytes = new ByteArrayOutputStream();
        prompt = new PrintStream(promptBytes, true, StandardCharsets.UTF_8);
        InputStream failing = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("device gone");
            }
        };

        ApprovalDecision decision =
                new TerminalApproval(failing, prompt).request(request());

        assertThat(decision.approved()).isFalse();
        assertThat(decision.comment()).contains("IO error");
    }

    @Test
    void decisionCarriesTheRequestId() {
        assertThat(approvalFor("y\n").request(request()).requestId())
                .isEqualTo(REQUEST_ID);
    }

    @Test
    void promptNamesTheToolAndRisk() {
        approvalFor("y\n").request(request());

        assertThat(promptText())
                .contains("APPROVAL REQUIRED")
                .contains("tool_call:run_command")
                .contains("L4")
                .contains("[y/N]");
    }
}
