package com.apex.engine.tools.impl;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpGetToolTest {

    private final HttpGetTool tool = new HttpGetTool();
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void returnsBodyOnSuccess() throws Exception {
        String url = startServer(200, "hello");

        assertThat(tool.execute(Map.of("url", url))).isEqualTo("hello");
    }

    @Test
    void throwsOnErrorStatus() throws Exception {
        String url = startServer(404, "nope");

        assertThatThrownBy(() -> tool.execute(Map.of("url", url)))
                .isInstanceOf(ToolException.class)
                .hasMessageContaining("HTTP 404");
    }

    @Test
    void rejectsLinkLocalAddress() {
        assertThatThrownBy(() -> tool.execute(
                Map.of("url", "http://169.254.169.254/")))
                .isInstanceOf(ToolException.class)
                .hasMessageContaining("forbidden address");
    }

    @Test
    void rejectsBlankUrl() {
        assertThatThrownBy(() -> tool.execute(Map.of("url", "  ")))
                .isInstanceOf(ToolException.class)
                .hasMessageContaining("missing arg");
    }

    private String startServer(int status, String body) throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/";
    }
}
