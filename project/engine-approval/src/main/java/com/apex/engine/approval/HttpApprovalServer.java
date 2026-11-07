package com.apex.engine.approval;

import com.apex.engine.core.port.ApprovalPort;
import com.apex.engine.core.port.model.ApprovalDecision;
import com.apex.engine.core.port.model.ApprovalRequest;
import com.apex.engine.json.Json;
import com.apex.engine.json.JsonException;

import com.apex.engine.planner.RiskLevel;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executors;

/**
 * HTTP/Webhook adapter implementing {@link ApprovalPort} over REST.
 * Exposes a single POST {@code /approve} endpoint that accepts an
 * {@link ApprovalRequest} JSON body, delegates to an injected
 * {@link ApprovalPort}, and returns the {@link ApprovalDecision} as JSON.
 * Tier 2: uses only the JDK HTTP server and engine-json for JSON handling.
 */
public final class HttpApprovalServer {

    private final HttpServer server;
    private final ApprovalPort approvalPort;

    public HttpApprovalServer(int port, ApprovalPort approvalPort) {
        this.approvalPort = Objects.requireNonNull(approvalPort, "approvalPort");
        try {
            this.server = HttpServer.create(new InetSocketAddress(port), 0);
        } catch (IOException e) {
            throw new IllegalStateException("failed to create HTTP server on port " + port, e);
        }
        this.server.createContext("/approve", new ApprovalHandler());
        this.server.setExecutor(Executors.newFixedThreadPool(4));
    }

    public void start() {
        server.start();
    }

    public void stop() {
        server.stop(0);
    }

    public int getPort() {
        return server.getAddress().getPort();
    }

    private class ApprovalHandler implements HttpHandler {

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\":\"method not allowed\"}");
                return;
            }

            ApprovalRequest request;
            try (InputStream is = exchange.getRequestBody()) {
                String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                Map<String, Object> parsed = Json.parseObject(body);
                request = new ApprovalRequest(
                        String.valueOf(parsed.get("id")),
                        String.valueOf(parsed.get("summary")),
                        RiskLevel.valueOf(String.valueOf(parsed.get("riskLevel")))
                );
            } catch (Exception e) {
                sendResponse(exchange, 400, "{\"error\":\"invalid request: \" + \"" + e.getMessage() + "\"}");
                return;
            }

            ApprovalDecision decision = approvalPort.request(request);

            String body;
            if (decision.approved()) {
                ApprovalDecision.Approve approve = (ApprovalDecision.Approve) decision;
                body = "{\"type\":\"approve\",\"requestId\":\"" + approve.requestId()
                        + "\",\"comment\":\"" + escapeJson(approve.comment()) + "\"}";
            } else if (decision.rejected()) {
                ApprovalDecision.Reject reject = (ApprovalDecision.Reject) decision;
                body = "{\"type\":\"reject\",\"requestId\":\"" + reject.requestId()
                        + "\",\"reason\":\"" + escapeJson(reject.reason()) + "\"}";
            } else {
                ApprovalDecision.Modify modify = (ApprovalDecision.Modify) decision;
                body = "{\"type\":\"modify\",\"requestId\":\"" + modify.requestId()
                        + "\",\"modifiedArguments\":\"" + escapeJson(modify.modifiedArguments())
                        + "\",\"reason\":\"" + escapeJson(modify.reason()) + "\"}";
            }

            sendResponse(exchange, 200, body);
        }

        private void sendResponse(HttpExchange exchange, int code, String body) throws IOException {
            byte[] response = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(code, response.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        }

        private String escapeJson(String s) {
            if (s == null) {
                return "";
            }
            return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
        }
    }
}
