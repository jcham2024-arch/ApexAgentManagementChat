package com.apex.engine.tools.impl;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Runs an allowlisted binary with tokenized arguments via
 * {@link ProcessBuilder} (no shell — K2). On timeout, kills the full
 * process tree (K5). Output capped at 50 KB. Non-zero exit is an
 * error.
 *
 * <p>Args: {@code cmd} (string, required — registry key),
 * {@code args} (array of strings, optional), {@code cwd} (string,
 * optional — must be inside workspace), {@code timeoutSec} (number,
 * optional — default 30, max 120).
 */
public final class RunCommandTool implements KernelTool {

    private static final int OUTPUT_CAP_BYTES = 50_000;
    private static final int DEFAULT_TIMEOUT_SEC = 30;
    private static final int MAX_TIMEOUT_SEC = 120;

    private final Map<String, Path> commandRegistry;
    private final RealPathJail jail;

    public RunCommandTool(Map<String, Path> commandRegistry, RealPathJail jail) {
        this.commandRegistry = Map.copyOf(commandRegistry);
        this.jail = Objects.requireNonNull(jail, "jail");
    }

    @Override
    public String name() {
        return "run_command";
    }

    @Override
    public String execute(Map<String, Object> args) {
        String cmd = requireString(args, "cmd");
        List<String> cmdArgs = optionalStringList(args, "args");
        String cwdStr = optionalString(args, "cwd");
        int timeoutSec = clampTimeout(optionalNumber(args, "timeoutSec"));

        Path binary = commandRegistry.get(cmd);
        if (binary == null) {
            throw new ToolException("run_command", "command not allowed: " + cmd);
        }
        if (!Files.isExecutable(binary)) {
            throw new ToolException("run_command", "not executable: " + binary);
        }

        Path workingDir = (cwdStr == null) ? jail.canonicalRoot() : jail.resolveExisting(cwdStr);
        if (!Files.isDirectory(workingDir)) {
            throw new ToolException("run_command", "not a dir: " + workingDir);
        }

        List<String> vector = new ArrayList<>(cmdArgs.size() + 1);
        vector.add(binary.toString());
        vector.addAll(cmdArgs);

        ProcessBuilder pb = new ProcessBuilder(vector);
        pb.directory(workingDir.toFile());
        pb.redirectErrorStream(true);

        Process process;
        try {
            process = pb.start();
        } catch (IOException e) {
            throw new ToolException("run_command", "start failed: " + e.getMessage());
        }

        boolean finished;
        try {
            finished = process.waitFor(timeoutSec, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            ProcessTreeKiller.kill(process, Duration.ofSeconds(2));
            throw new ToolException("run_command", "interrupted");
        }
        if (!finished) {
            ProcessTreeKiller.kill(process, Duration.ofSeconds(2));
            throw new ToolException("run_command", "timeout after " + timeoutSec + "s");
        }

        String output = readCapped(process.getInputStream());
        int exit = process.exitValue();
        if (exit != 0) {
            throw new ToolException("run_command", "exit " + exit + ": " + output);
        }
        return output;
    }

    private static String readCapped(InputStream in) {
        StringBuilder sb = new StringBuilder();
        byte[] buffer = new byte[4096];
        try {
            int read;
            while ((read = in.read(buffer)) >= 0) {
                sb.append(new String(buffer, 0, read, StandardCharsets.UTF_8));
                if (sb.length() > OUTPUT_CAP_BYTES) {
                    sb.setLength(OUTPUT_CAP_BYTES);
                    sb.append("\n[TRUNCATED]");
                    break;
                }
            }
        } catch (IOException e) {
            throw new ToolException("run_command", "read failed: " + e.getMessage());
        }
        return sb.toString();
    }

    private static String requireString(Map<String, Object> args, String key) {
        Object v = args.get(key);
        if (!(v instanceof String s) || s.isBlank()) {
            throw new ToolException("run_command", "missing arg: " + key);
        }
        return s;
    }

    private static String optionalString(Map<String, Object> args, String key) {
        Object v = args.get(key);
        return v instanceof String s ? s : null;
    }

    private static List<String> optionalStringList(Map<String, Object> args, String key) {
        Object v = args.get(key);
        if (v == null) return List.of();
        if (!(v instanceof List<?> list)) {
            throw new ToolException("run_command", "arg not array: " + key);
        }
        List<String> out = new ArrayList<>(list.size());
        for (Object o : list) {
            if (!(o instanceof String s)) {
                throw new ToolException("run_command", "element not string: " + key);
            }
            out.add(s);
        }
        return out;
    }

    private static int optionalNumber(Map<String, Object> args, String key) {
        Object v = args.get(key);
        if (v == null) return DEFAULT_TIMEOUT_SEC;
        if (v instanceof Long l) return l.intValue();
        if (v instanceof Double d) return d.intValue();
        throw new ToolException("run_command", "arg not number: " + key);
    }

    private static int clampTimeout(int t) {
        if (t <= 0) return DEFAULT_TIMEOUT_SEC;
        return Math.min(t, MAX_TIMEOUT_SEC);
    }
}
