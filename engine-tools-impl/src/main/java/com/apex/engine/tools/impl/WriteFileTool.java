package com.apex.engine.tools.impl;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

/**
 * Writes a UTF-8 text file inside the workspace. Parent directory must
 * exist and be inside the root. Args: {@code path}, {@code content}
 * (both strings, required).
 */
public final class WriteFileTool implements KernelTool {

    private final RealPathJail jail;

    public WriteFileTool(RealPathJail jail) {
        this.jail = Objects.requireNonNull(jail, "jail");
    }

    @Override
    public String name() {
        return "write_file";
    }

    @Override
    public String execute(Map<String, Object> args) {
        String path = requireString(args, "path");
        String content = requireString(args, "content");
        Path file = jail.resolveForWrite(path);
        try {
            Files.writeString(file, content, StandardCharsets.UTF_8);
            return "wrote " + content.length() + " chars to " + file;
        } catch (IOException e) {
            throw new ToolException("write_file", "write failed: " + e.getMessage());
        }
    }

    private static String requireString(Map<String, Object> args, String key) {
        Object v = args.get(key);
        if (!(v instanceof String s)) {
            throw new ToolException("write_file", "missing arg: " + key);
        }
        return s;
    }
}
