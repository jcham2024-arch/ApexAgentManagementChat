package com.apex.engine.sandbox;

/**
 * Isolation levels for tool execution. PROCESS uses ProcessBuilder
 * with a realpath jail and process-tree kill. CONTAINER uses gVisor
 * (runsc) inside Docker. FIRE_CRACKER uses a microVM. Tier selection
 * is by RiskLevel: L0–L2 → PROCESS, L3–L4 → CONTAINER, L5 →
 * FIRE_CRACKER. Container and Firecracker implementations land in S6;
 * S4.6 proves the selection logic only.
 */
public enum SandboxTier {
    PROCESS,
    CONTAINER,
    FIRE_CRACKER
}
