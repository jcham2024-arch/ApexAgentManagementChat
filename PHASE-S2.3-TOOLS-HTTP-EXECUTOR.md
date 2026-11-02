# PHASE-S2.3-TOOLS-HTTP-EXECUTOR.md — Session Package, Part 6 of 8

**Version:** 1.0 · **Sub-phase of S2** · **Part 6 of 8:** §4.10–§4.11, http_get and KernelToolExecutor.
Parts: SESSION · JSON · JAIL · FILE-TOOLS · RUN-COMMAND · HTTP-EXECUTOR ·
METADATA-TESTS · CONTRACTS-CRITERIA, each `PHASE-S2.3-TOOLS-<part>.md`.
Cut for the 200-line rule in `AGENT_RULES.md` §2.13.

---
### 4.10 `HttpGetTool.java`

```java
package com.apex.engine.tools.impl;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * HTTP GET against a public URL or loopback. Blocks link-local
 * (169.254.0.0/16, fe80::/10) and unspecified (0.0.0.0, ::) addresses.
 * Response body capped at 50 KB.
 *
 * <p>Args: {@code url} (string, required), {@code headers} (object of
 * string-to-string, optional).
 */
public final class HttpGetTool implements KernelTool {

    private static final int OUTPUT_CAP_BYTES = 50_000;
    private static final Duration TIMEOUT = Duration.ofSeconds(20);
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    @Override
    public String name() {
        return "http_get";
    }

    @Override
    public String execute(Map<String, Object> args) {
        Object uv = args.get("url");
        if (!(uv instanceof String urlStr) || urlStr.isBlank()) {
            throw new ToolException("http_get", "missing arg: url");
        }
        URI uri;
        try {
            uri = URI.create(urlStr);
        } catch (IllegalArgumentException e) {
            throw new ToolException("http_get", "bad url: " + e.getMessage());
        }
        rejectIfUnsafe(uri.getHost());

        HttpRequest.Builder reqBuilder = HttpRequest.newBuilder(uri)
                .timeout(TIMEOUT)
                .GET();
        Object hv = args.get("headers");
        if (hv instanceof Map<?, ?> headers) {
            for (Map.Entry<?, ?> e : headers.entrySet()) {
                if (e.getKey() instanceof String k && e.getValue() instanceof String v) {
                    reqBuilder.header(k, v);
                }
            }
        }

        try {
            HttpResponse<String> response = CLIENT.send(
                    reqBuilder.build(), HttpResponse.BodyHandlers.ofString());
            String body = response.body();
            if (body.length() > OUTPUT_CAP_BYTES) {
                body = body.substring(0, OUTPUT_CAP_BYTES) + "\n[TRUNCATED]";
            }
            if (response.statusCode() >= 400) {
                throw new ToolException("http_get",
                        "HTTP " + response.statusCode() + ": " + body);
            }
            return body;
        } catch (java.io.IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new ToolException("http_get", "request failed: " + e.getMessage());
        }
    }

    private static void rejectIfUnsafe(String host) {
        Objects.requireNonNull(host, "url has no host");
        try {
            InetAddress addr = InetAddress.getByName(host);
            if (addr.isLinkLocalAddress() || addr.isAnyLocalAddress()) {
                throw new ToolException("http_get", "forbidden address: " + addr);
            }
        } catch (UnknownHostException e) {
            throw new ToolException("http_get", "cannot resolve host: " + host);
        }
    }
}
```

### 4.11 `KernelToolExecutor.java`

```java
package com.apex.engine.tools.impl;

import com.apex.engine.core.port.ToolExecutorPort;
import com.apex.engine.core.port.model.ToolCall;
import com.apex.engine.core.port.model.ToolResult;

import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

/**
 * Default {@link ToolExecutorPort}. Dispatches to the five kernel
 * tools by name. Parses {@code argumentsJson} once, then delegates.
 * Any failure (unknown tool, parse error, tool exception) is reported
 * as {@code ToolResult(isError=true)} with a message.
 */
public final class KernelToolExecutor implements ToolExecutorPort {

    private final Map<String, KernelTool> tools;

    public KernelToolExecutor(RealPathJail jail, Map<String, Path> commandRegistry) {
        Objects.requireNonNull(jail, "jail");
        Objects.requireNonNull(commandRegistry, "commandRegistry");
        this.tools = Map.of(
                "read_file",   new ReadFileTool(jail),
                "write_file",  new WriteFileTool(jail),
                "list_files",  new ListFilesTool(jail),
                "run_command", new RunCommandTool(commandRegistry, jail),
                "http_get",    new HttpGetTool()
        );
    }

    @Override
    public ToolResult execute(ToolCall call) {
        long start = System.nanoTime();
        try {
            KernelTool tool = tools.get(call.toolName());
            if (tool == null) {
                return new ToolResult(call.id(), true,
                        "unknown tool: " + call.toolName(), elapsed(start));
            }
            Map<String, Object> args = Json.parseObject(call.argumentsJson());
            String output = tool.execute(args);
            return new ToolResult(call.id(), false, output, elapsed(start));
        } catch (ToolException e) {
            return new ToolResult(call.id(), true, e.getMessage(), elapsed(start));
        } catch (RuntimeException e) {
            return new ToolResult(call.id(), true,
                    "internal: " + e.getClass().getSimpleName() + ": " + e.getMessage(),
                    elapsed(start));
        }
    }

    private static long elapsed(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }
}
```
