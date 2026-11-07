package com.apex.engine.store;

import com.apex.engine.core.port.CheckpointPort;
import com.apex.engine.core.port.model.CheckpointRecord;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CheckpointPortTest {

    private static final CheckpointRecord CHECKPOINT = new CheckpointRecord(
            "session-1", 3, "{\"done\":\"true\"}", Instant.now());

    private static CheckpointPort inMemory() {
        return new CheckpointPort() {
            private CheckpointRecord stored;

            @Override
            public void saveCheckpoint(String sessionId, int stepIndex,
                                       String stateSnapshot) {
                this.stored = new CheckpointRecord(sessionId, stepIndex,
                        stateSnapshot, Instant.now());
            }

            @Override
            public Optional<CheckpointRecord> restoreCheckpoint(
                    String sessionId) {
                java.util.Objects.requireNonNull(sessionId, "sessionId");
                if (stored != null && stored.sessionId().equals(sessionId)) {
                    return Optional.of(stored);
                }
                return Optional.empty();
            }

            @Override
            public void deleteCheckpoint(String sessionId) {
                java.util.Objects.requireNonNull(sessionId, "sessionId");
                if (stored != null && stored.sessionId().equals(sessionId)) {
                    stored = null;
                }
            }
        };
    }

    @Test
    void saveAndRestoreCheckpoint() {
        CheckpointPort port = inMemory();
        port.saveCheckpoint("session-1", 3, "{\"done\":\"true\"}");

        Optional<CheckpointRecord> restored = port.restoreCheckpoint("session-1");
        assertThat(restored).isPresent();
        assertThat(restored.get().sessionId()).isEqualTo("session-1");
        assertThat(restored.get().stepIndex()).isEqualTo(3);
        assertThat(restored.get().stateSnapshot()).isEqualTo("{\"done\":\"true\"}");
        assertThat(restored.get().savedAt()).isNotNull();
    }

    @Test
    void restoreMissingSessionReturnsEmpty() {
        CheckpointPort port = inMemory();
        assertThat(port.restoreCheckpoint("nonexistent")).isEmpty();
    }

    @Test
    void deleteCheckpointRemovesIt() {
        CheckpointPort port = inMemory();
        port.saveCheckpoint("session-1", 3, "{\"done\":\"true\"}");
        port.deleteCheckpoint("session-1");
        assertThat(port.restoreCheckpoint("session-1")).isEmpty();
    }

    @Test
    void deleteCheckpointForDifferentSessionDoesNotAffect() {
        CheckpointPort port = inMemory();
        port.saveCheckpoint("session-1", 3, "{\"done\":\"true\"}");
        port.deleteCheckpoint("session-2");
        assertThat(port.restoreCheckpoint("session-1")).isPresent();
    }

    @Test
    void saveRejectsNullSessionId() {
        CheckpointPort port = inMemory();
        assertThatThrownBy(() -> port.saveCheckpoint(null, 0, "{}"))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void saveRejectsNegativeStepIndex() {
        CheckpointPort port = inMemory();
        assertThatIllegalArgumentException()
                .isThrownBy(() -> port.saveCheckpoint("session-1", -1, "{}"));
    }

    @Test
    void saveRejectsBlankSessionId() {
        CheckpointPort port = inMemory();
        assertThatIllegalArgumentException()
                .isThrownBy(() -> port.saveCheckpoint("   ", 0, "{}"));
    }

    @Test
    void saveRejectsNullStateSnapshot() {
        CheckpointPort port = inMemory();
        assertThatThrownBy(() -> port.saveCheckpoint("session-1", 0, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void restoreRejectsNullSessionId() {
        CheckpointPort port = inMemory();
        assertThatThrownBy(() -> port.restoreCheckpoint(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void deleteRejectsNullSessionId() {
        CheckpointPort port = inMemory();
        assertThatThrownBy(() -> port.deleteCheckpoint(null))
                .isInstanceOf(NullPointerException.class);
    }
}
