package com.apex.engine.tools.impl;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

/**
 * Reads a UTF-8 text file from inside the workspace. Rejects files
 * larger than 50 KB. Args: {@code path} (string, required).
 */
public final class ReadFileTool implements KernelTool {

    private static final long MAX_BYTES = 50_000L;
    private final RealPathJail jail;

    public ReadFileTool(RealPathJail jail) {
        this.jail = Objects.requireNonNull(jail, "jail");
    }

    @Override
    public String name() {
        return "read_file";
    }

    @Override
    public String execute(Map<String, Object> args) {
        Object v = args.get("path");
        if (!(v instanceof String s) || s.isBlank()) {
            throw new ToolException("read_file", "missing arg: path");
        }
        Path file = jail.resolveExisting(s);
        if (!Files.isRegularFile(file)) {
            throw new ToolException("read_file", "not a file: " + file);
        }
        try {
            long size = Files.size(file);
            if (size > MAX_BYTES) {
                throw new ToolException("read_file", "file too large: " + size + " bytes");
            }
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ToolException("read_file", "read failed: " + e.getMessage(), e);
        }
    }
}
