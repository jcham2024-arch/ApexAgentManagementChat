package com.apex.engine.tools.impl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
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
            throw new ToolException("path",
                    "cannot resolve: " + candidate + ": " + e.getMessage(), e);
        }
    }

    /**
     * Resolves a candidate path for writing; the parent must exist. An
     * existing leaf is resolved through {@link Path#toRealPath()} so a
     * symlink pointing outside the root cannot be written through.
     */
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
            Path target = realParent.resolve(leaf);
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                Path real = target.toRealPath();
                requireInside(real);
                return real;
            }
            return target;
        } catch (IOException e) {
            throw new ToolException("path",
                    "cannot resolve: " + candidate + ": " + e.getMessage(), e);
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
