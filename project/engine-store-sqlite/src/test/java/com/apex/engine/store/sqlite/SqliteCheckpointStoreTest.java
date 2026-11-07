package com.apex.engine.store.sqlite;

import com.apex.engine.core.port.CheckpointPort;
import com.apex.engine.core.port.model.CheckpointRecord;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class SqliteCheckpointStoreTest {

    @TempDir
    Path tempDir;

    CheckpointPort store;

    @BeforeEach
    void setUp() {
        store = new SqliteCheckpointStore(tempDir.resolve("checkpoints.db"));
    }

    @Test
    void saveAndRestoreCheckpoint() {
        store.saveCheckpoint("session-1", 3, "{\"done\":\"true\"}");

        Optional<CheckpointRecord> restored = store.restoreCheckpoint("session-1");
        assertThat(restored).isPresent();
        assertThat(restored.get().sessionId()).isEqualTo("session-1");
        assertThat(restored.get().stepIndex()).isEqualTo(3);
        assertThat(restored.get().stateSnapshot()).isEqualTo("{\"done\":\"true\"}");
        assertThat(restored.get().savedAt()).isNotNull();
        assertThat(restored.get().savedAt().isBefore(Instant.now())).isTrue();
        assertThat(restored.get().savedAt().isAfter(Instant.now().minusSeconds(10))).isTrue();
    }

    @Test
    void restoreMissingSessionReturnsEmpty() {
        assertThat(store.restoreCheckpoint("nonexistent")).isEmpty();
    }

    @Test
    void saveReplacesExistingCheckpoint() {
        store.saveCheckpoint("session-1", 1, "{\"step\":1}");
        store.saveCheckpoint("session-1", 5, "{\"step\":5}");

        Optional<CheckpointRecord> restored = store.restoreCheckpoint("session-1");
        assertThat(restored).isPresent();
        assertThat(restored.get().stepIndex()).isEqualTo(5);
        assertThat(restored.get().stateSnapshot()).isEqualTo("{\"step\":5}");
    }

    @Test
    void deleteCheckpointRemovesIt() {
        store.saveCheckpoint("session-1", 3, "{\"done\":\"true\"}");
        store.deleteCheckpoint("session-1");
        assertThat(store.restoreCheckpoint("session-1")).isEmpty();
    }

    @Test
    void multipleSessionsAreIndependent() {
        store.saveCheckpoint("session-1", 1, "{\"step\":1}");
        store.saveCheckpoint("session-2", 2, "{\"step\":2}");

        assertThat(store.restoreCheckpoint("session-1")).isPresent();
        assertThat(store.restoreCheckpoint("session-1").get().stepIndex())
                .isEqualTo(1);
        assertThat(store.restoreCheckpoint("session-2")).isPresent();
        assertThat(store.restoreCheckpoint("session-2").get().stepIndex())
                .isEqualTo(2);
    }

    @Test
    void saveRejectsNullSessionId() {
        assertThatNullPointerException()
                .isThrownBy(() -> store.saveCheckpoint(null, 0, "{}"));
    }

    @Test
    void saveRejectsNegativeStepIndex() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> store.saveCheckpoint("session-1", -1, "{}"));
    }

    @Test
    void saveRejectsNullStateSnapshot() {
        assertThatNullPointerException()
                .isThrownBy(() -> store.saveCheckpoint("session-1", 0, null));
    }

    @Test
    void restoreRejectsNullSessionId() {
        assertThatNullPointerException()
                .isThrownBy(() -> store.restoreCheckpoint(null));
    }

    @Test
    void deleteRejectsNullSessionId() {
        assertThatNullPointerException()
                .isThrownBy(() -> store.deleteCheckpoint(null));
    }
}
