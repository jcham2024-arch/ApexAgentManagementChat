package com.apex.platform.app.real;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Plumbing for the end-to-end tests: a mock OpenAI-compatible provider,
 * captured streams, task-file writing and a direct SQLite peek. Split
 * out of E2ETest so both classes stay under the 200-line limit.
 */
abstract class E2ETestSupport {

    private static final String STEP_1 =
            "{\"action\":\"read_file\",\"arguments\":{\"path\":\"ARCHITECTURE.md\"}}";
    private static final String STEP_2 =
            "{\"action\":\"write_file\",\"arguments\":{\"path\":\"REPORT.md\","
            + "\"content\":\"Apex is a multi-agent platform.\"}}";
    private static final String STEP_3 =
            "{\"done\":true,\"summary\":\"wrote report\"}";

    @TempDir
    Path workspace;

    HttpServer server;
    AtomicInteger steps;
    ByteArrayOutputStream outBytes;
    ByteArrayOutputStream errBytes;
    PrintStream out;
    PrintStream err;

    @BeforeEach
    void startMockProvider() throws IOException {
        steps = new AtomicInteger();
        outBytes = new ByteArrayOutputStream();
        errBytes = new ByteArrayOutputStream();
        out = new PrintStream(outBytes, true, StandardCharsets.UTF_8);
        err = new PrintStream(errBytes, true, StandardCharsets.UTF_8);

        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            exchange.getRequestBody().readAllBytes();
            String response = envelope(switch (steps.incrementAndGet()) {
                case 1 -> STEP_1;
                case 2 -> STEP_2;
                default -> STEP_3;
            });
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream body = exchange.getResponseBody()) {
                body.write(bytes);
            }
        });
        server.start();

        Files.writeString(workspace.resolve("ARCHITECTURE.md"),
                "Apex is a multi-agent platform.");
    }

    @AfterEach
    void stopMockProvider() {
        if (server != null) {
            server.stop(0);
        }
    }

    /**
     * The mock must speak the OpenAI contract: the decision JSON is the
     * assistant message content, not the whole body.
     */
    private static String envelope(String decisionJson) {
        String escaped = decisionJson
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
        return "{\"choices\":[{\"message\":{\"role\":\"assistant\","
                + "\"content\":\"" + escaped + "\"}}]}";
    }

    int runMain(Path taskFile) {
        return RealMain.run(new String[] {"--workspace",
                workspace.toString(), taskFile.toString()}, env(),
                new java.io.ByteArrayInputStream(new byte[0]), out, err);
    }

    Map<String, String> env() {
        return Map.of(
                "APEX_LLM_BASE_URL",
                "http://127.0.0.1:" + server.getAddress().getPort() + "/v1",
                "APEX_LLM_API_KEY", "test",
                "APEX_LLM_DEFAULT_MODEL", "mock-model",
                "APEX_LLM_TIMEOUT_SEC", "10",
                "APEX_AUDIT_SECRET", "test-audit-secret-0123456789abcdef");
    }

    Path writeTask(String yaml) throws IOException {
        Path file = workspace.resolve("task.yaml");
        Files.writeString(file, yaml);
        return file;
    }

    /** The default tenant must be stamped on every recorded step. */
    String recordedTenant() throws Exception {
        try (Connection c = DriverManager.getConnection(
                "jdbc:sqlite:" + workspace.resolve(".apex/steps.db"));
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT DISTINCT tenant_id FROM step_log")) {
            assertThat(rs.next()).isTrue();
            return rs.getString(1);
        }
    }

    String stdout() {
        return outBytes.toString(StandardCharsets.UTF_8);
    }

    String stderr() {
        return errBytes.toString(StandardCharsets.UTF_8);
    }
}
