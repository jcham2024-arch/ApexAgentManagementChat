package com.apex.engine.sandbox;

/**
 * Executes a command inside a specific isolation tier. Each tier has
 * one implementation; the caller selects the tier by the tool's risk
 * level. Implementations are responsible for their own isolation
 * mechanism (ProcessBuilder, Docker+gVisor, Firecracker). Tier 1.
 */
public interface SandboxPort {
    SandboxTier tier();
    SandboxResult execute(SandboxCommand command);
}
