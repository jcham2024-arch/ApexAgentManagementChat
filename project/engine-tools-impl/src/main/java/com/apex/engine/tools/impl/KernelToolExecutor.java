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
