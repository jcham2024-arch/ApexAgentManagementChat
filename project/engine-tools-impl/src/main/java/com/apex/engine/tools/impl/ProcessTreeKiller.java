package com.apex.engine.tools.impl;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Kills a process and all descendants. Descendants are enumerated and
 * destroyed first (via {@link ProcessHandle#descendants()}), then the
 * direct process (K5).
 */
public final class ProcessTreeKiller {
    private ProcessTreeKiller() {}

    public static void kill(Process process, Duration waitFor) {
        process.descendants().forEach(ProcessHandle::destroyForcibly);
        process.destroyForcibly();
        try {
            process.waitFor(waitFor.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
