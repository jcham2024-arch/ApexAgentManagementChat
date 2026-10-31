package com.apex.engine.tools.impl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Enforces that every path a tool touches is inside a canonical
 * workspace root. Uses {@link Path#toRealPath()} to resolve symlinks
 * and {@code ..} before checking containment. Rejects any path that
 * resolves outside the root (K4).
 */
public final class RealPathJail {

    private final Path canonicalRoot;

    public RealPathJail(Path workspaceRoot) throws IOException {
        this.canonicalRoot = workspaceRoot.toRealPath();
        if (!Files.isDirectory(canonicalRoot)) {
            throw new IOException("workspace root is not a directory: " + workspaceRoot);
        }
    }

    public Path canonicalRoot() {
        return canonicalRoot;
    }

    /** Resolves a candidate path that is expected to exist. */
    public Path resolveExisting(String candidate) {
        try {
            Path p = toAbsolute(candidate);
            Path real = p.toRealPath();
            requireInside(real);
            return real;
        } catch (IOException e) {
            throw new ToolException("path", "cannot resolve: " + candidate + ": " + e.getMessage());
        }
    }

    /** Resolves a candidate path for writing; the parent must exist. */
    public Path resolveForWrite(String candidate) {
        try {
            Path p = toAbsolute(candidate);
            Path parent = p.getParent();
            if (parent == null) throw new ToolException("path", "no parent: " + candidate);
            Path realParent = parent.toRealPath();
            requireInside(realParent);
            Path leaf = p.getFileName();
            if (leaf == null || leaf.toString().isEmpty()) {
                throw new ToolException("path", "no filename: " + candidate);
            }
            return realParent.resolve(leaf);
        } catch (IOException e) {
            throw new ToolException("path", "cannot resolve: " + candidate + ": " + e.getMessage());
        }
    }

    private Path toAbsolute(String candidate) {
        if (candidate == null || candidate.isBlank()) {
            throw new ToolException("path", "path is empty");
        }
        Path p = Path.of(candidate);
        return p.isAbsolute() ? p : canonicalRoot.resolve(p);
    }

    private void requireInside(Path real) {
        if (!real.startsWith(canonicalRoot)) {
            throw new ToolException("path", "path escapes workspace: " + real);
        }
    }
}
