package com.apex.engine.store.sqlite;

import com.apex.engine.core.port.model.StepLogEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SqliteStoragePortTest {

    @TempDir
    Path tempDir;

    Path dbPath;
    SqliteStoragePort port;

    @BeforeEach
    void setUp() {
        dbPath = tempDir.resolve("test.db");
        port = new SqliteStoragePort(dbPath);
    }

    static StepLogEntry entry(String stepId, String sessionId, int index) {
        return new StepLogEntry(stepId, sessionId, index, "plan", index * 10L);
    }

    @Test
    void appendAndQueryRoundTrip() {
        StepLogEntry written = entry("s-0", "session-a", 0);

        port.appendStep(written);

        assertThat(port.stepsFor("session-a")).containsExactly(written);
    }

    @Test
    void queryOrdersByStepIndex() {
        port.appendStep(entry("s-2", "session-a", 2));
        port.appendStep(entry("s-0", "session-a", 0));
        port.appendStep(entry("s-1", "session-a", 1));

        List<StepLogEntry> entries = port.stepsFor("session-a");

        assertThat(entries).extracting(StepLogEntry::index)
                .containsExactly(0, 1, 2);
    }

    @Test
    void queryFiltersBySession() {
        port.appendStep(entry("s-a", "a", 0));
        port.appendStep(entry("s-b", "b", 0));

        List<StepLogEntry> entries = port.stepsFor("a");

        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).sessionId()).isEqualTo("a");
    }

    @Test
    void queryEmptySessionReturnsEmpty() {
        assertThat(port.stepsFor("never-seen")).isEmpty();
    }

    @Test
    void duplicateStepIdThrows() {
        StepLogEntry written = entry("s-0", "session-a", 0);
        port.appendStep(written);

        assertThatThrownBy(() -> port.appendStep(written))
                .isInstanceOf(StorageException.class)
                .satisfies(e -> assertThat(((StorageException) e).category())
                        .isEqualTo("duplicate"));
    }

    @Test
    void persistenceAcrossReopen() {
        StepLogEntry written = entry("s-0", "session-a", 0);
        port.appendStep(written);

        SqliteStoragePort reopened = new SqliteStoragePort(dbPath);

        assertThat(reopened.stepsFor("session-a")).containsExactly(written);
    }

    @Test
    void rejectsNullEntryAndNullSession() {
        assertThatThrownBy(() -> port.appendStep(null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> port.stepsFor(null))
                .isInstanceOf(NullPointerException.class);
    }
}
