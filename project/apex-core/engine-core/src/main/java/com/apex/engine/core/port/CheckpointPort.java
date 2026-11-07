package com.apex.engine.core.port;

import com.apex.engine.core.port.model.CheckpointRecord;

import java.util.Optional;

/**
 * Checkpoint port for plan execution persistence. {@link PlanLoop}
 * checkpoints at step boundaries so that execution can be resumed
 * deterministically after interruption (e.g. after an
 * {@link com.apex.engine.core.port.model.GatewayDecision#RequireApproval}
 * gate or a cancellation). Tier 0.
 */
public interface CheckpointPort {

    /**
     * Saves a checkpoint for the given session at the given step index.
     * Replaces any existing checkpoint for the same session. Fail-closed:
     * throws if the snapshot cannot be persisted.
     */
    void saveCheckpoint(String sessionId, int stepIndex, String stateSnapshot);

    /**
     * Restores the most recent checkpoint for the given session.
     * Returns {@link java.util.Optional#empty()} if no checkpoint exists.
     */
    Optional<CheckpointRecord> restoreCheckpoint(String sessionId);

    /** Deletes the checkpoint for the given session (e.g. after completion). */
    void deleteCheckpoint(String sessionId);
}
