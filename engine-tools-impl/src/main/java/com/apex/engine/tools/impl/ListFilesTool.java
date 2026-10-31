package com.apex.engine.tools.impl;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Lists files in a workspace directory matching a glob. Depth 1 only.
 * Args: {@code dir} (string, required), {@code pattern} (string,
 * optional, default {@code *}). Output: newline-separated absolute
 * paths, sorted. The phase file's Javadoc said "relative to
 * {@code dir}"; the code it specifies returns absolute paths, so the
 * documentation now matches the code.
 */
public final class ListFilesTool implements KernelTool {

    private final RealPathJail jail;

    public ListFilesTool(RealPathJail jail) {
        this.jail = Objects.requireNonNull(jail, "jail");
    }

    @Override
    public String name() {
        return "list_files";
    }

    @Override
    public String execute(Map<String, Object> args) {
        Object dv = args.get("dir");
        if (!(dv instanceof String dirStr) || dirStr.isBlank()) {
            throw new ToolException("list_files", "missing arg: dir");
        }
        Object pv = args.get("pattern");
        String pattern = pv instanceof String s ? s : "*";

        Path dir = jail.resolveExisting(dirStr);
        if (!Files.isDirectory(dir)) {
            throw new ToolException("list_files", "not a directory: " + dir);
        }
        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);
        try (Stream<Path> stream = Files.list(dir)) {
            return stream
                    .filter(p -> matcher.matches(p.getFileName()))
                    .map(Path::toString)
                    .sorted()
                    .collect(Collectors.joining("\n"));
        } catch (IOException e) {
            throw new ToolException("list_files", "list failed: " + e.getMessage());
        }
    }
}
