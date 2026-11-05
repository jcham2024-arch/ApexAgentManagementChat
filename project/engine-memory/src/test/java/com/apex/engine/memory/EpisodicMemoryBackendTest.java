package com.apex.engine.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.apex.chat.common.identity.TenantId;
import com.apex.engine.core.port.StoragePort;
import com.apex.engine.core.port.model.StepLogEntry;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;

class EpisodicMemoryBackendTest {

    private static final TenantId TENANT = new TenantId("tenant-1");
    private static final String SESSION = "session-1";

    private static class TestStoragePort implements StoragePort {
        private final List<StepLogEntry> entries = new CopyOnWriteArrayList<>();

        @Override
        public void appendStep(StepLogEntry entry) {
            entries.add(entry);
        }

        @Override
        public List<StepLogEntry> stepsFor(TenantId tenantId, String sessionId) {
            List<StepLogEntry> result = new ArrayList<>();
            for (StepLogEntry entry : entries) {
                if (entry.tenantId().equals(tenantId)
                        && entry.sessionId().equals(sessionId)) {
                    result.add(entry);
                }
            }
            return result;
        }
    }

    @Test
    void kindIsEpisodic() {
        EpisodicMemoryBackend backend = new EpisodicMemoryBackend(
                new TestStoragePort(), TENANT, SESSION);
        assertThat(backend.kind()).isEqualTo(MemoryKind.EPISODIC);
    }

    @Test
    void addAndQueryExactMatch() {
        TestStoragePort storage = new TestStoragePort();
        EpisodicMemoryBackend backend = new EpisodicMemoryBackend(
                storage, TENANT, SESSION);

        Instant t0 = Instant.parse("2026-01-01T00:00:00Z");
        backend.add(new WorkingMemoryEntry("assistant",
                "the quick brown fox", t0));

        MemoryQuery q = new MemoryQuery("quick",
                Set.of(MemoryKind.EPISODIC), 10);
        MemoryResult result = backend.query(q);

        assertThat(result.hits()).hasSize(1);
        assertThat(result.hits().get(0).score()).isEqualTo(1.0);
        assertThat(result.hits().get(0).kind()).isEqualTo(MemoryKind.EPISODIC);
        assertThat(result.hits().get(0).source()).isEqualTo("assistant");
        assertThat(result.hits().get(0).content())
                .isEqualTo("the quick brown fox");
        assertThat(result.queried()).containsExactly(MemoryKind.EPISODIC);
    }

    @Test
    void queryCaseInsensitiveScoresLower() {
        TestStoragePort storage = new TestStoragePort();
        EpisodicMemoryBackend backend = new EpisodicMemoryBackend(
                storage, TENANT, SESSION);

        Instant t0 = Instant.parse("2026-01-01T00:00:00Z");
        backend.add(new WorkingMemoryEntry("assistant",
                "The QUICK brown fox", t0));

        MemoryQuery q = new MemoryQuery("quick",
                Set.of(MemoryKind.EPISODIC), 10);
        MemoryResult result = backend.query(q);

        assertThat(result.hits()).hasSize(1);
        assertThat(result.hits().get(0).score()).isEqualTo(0.8);
    }

    @Test
    void queryMissReturnsEmpty() {
        TestStoragePort storage = new TestStoragePort();
        EpisodicMemoryBackend backend = new EpisodicMemoryBackend(
                storage, TENANT, SESSION);

        Instant t0 = Instant.parse("2026-01-01T00:00:00Z");
        backend.add(new WorkingMemoryEntry("assistant",
                "the quick brown fox", t0));

        MemoryQuery q = new MemoryQuery("zebra",
                Set.of(MemoryKind.EPISODIC), 10);
        MemoryResult result = backend.query(q);

        assertThat(result.hits()).isEmpty();
        assertThat(result.queried()).containsExactly(MemoryKind.EPISODIC);
    }

    @Test
    void queryRespectsMaxHits() {
        TestStoragePort storage = new TestStoragePort();
        EpisodicMemoryBackend backend = new EpisodicMemoryBackend(
                storage, TENANT, SESSION);

        Instant t0 = Instant.parse("2026-01-01T00:00:00Z");
        backend.add(new WorkingMemoryEntry("assistant", "quick alpha", t0));
        backend.add(new WorkingMemoryEntry("assistant", "quick beta", t0));
        backend.add(new WorkingMemoryEntry("assistant", "quick gamma", t0));

        MemoryQuery q = new MemoryQuery("quick",
                Set.of(MemoryKind.EPISODIC), 2);
        MemoryResult result = backend.query(q);

        assertThat(result.hits()).hasSize(2);
    }

    @Test
    void queryOtherKindReturnsEmpty() {
        TestStoragePort storage = new TestStoragePort();
        EpisodicMemoryBackend backend = new EpisodicMemoryBackend(
                storage, TENANT, SESSION);

        Instant t0 = Instant.parse("2026-01-01T00:00:00Z");
        backend.add(new WorkingMemoryEntry("assistant",
                "the quick brown fox", t0));

        MemoryQuery q = new MemoryQuery("quick",
                Set.of(MemoryKind.WORKING), 10);
        MemoryResult result = backend.query(q);

        assertThat(result.hits()).isEmpty();
        assertThat(result.queried()).isEmpty();
    }

    @Test
    void sizeIncrementsOnAdd() {
        TestStoragePort storage = new TestStoragePort();
        EpisodicMemoryBackend backend = new EpisodicMemoryBackend(
                storage, TENANT, SESSION);

        assertThat(backend.size()).isZero();

        Instant t0 = Instant.parse("2026-01-01T00:00:00Z");
        backend.add(new WorkingMemoryEntry("assistant", "one", t0));
        assertThat(backend.size()).isEqualTo(1);

        backend.add(new WorkingMemoryEntry("assistant", "two", t0));
        assertThat(backend.size()).isEqualTo(2);
    }

    @Test
    void rejectsNullStorage() {
        assertThatThrownBy(() -> new EpisodicMemoryBackend(
                null, TENANT, SESSION))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsNullTenant() {
        TestStoragePort storage = new TestStoragePort();
        assertThatThrownBy(() -> new EpisodicMemoryBackend(
                storage, null, SESSION))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsNullSession() {
        TestStoragePort storage = new TestStoragePort();
        assertThatThrownBy(() -> new EpisodicMemoryBackend(
                storage, TENANT, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsNullEntry() {
        TestStoragePort storage = new TestStoragePort();
        EpisodicMemoryBackend backend = new EpisodicMemoryBackend(
                storage, TENANT, SESSION);
        assertThatThrownBy(() -> backend.add(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void clearThrowsUnsupportedOperation() {
        TestStoragePort storage = new TestStoragePort();
        EpisodicMemoryBackend backend = new EpisodicMemoryBackend(
                storage, TENANT, SESSION);
        assertThatThrownBy(backend::clear)
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void queryRejectsNull() {
        TestStoragePort storage = new TestStoragePort();
        EpisodicMemoryBackend backend = new EpisodicMemoryBackend(
                storage, TENANT, SESSION);
        assertThatThrownBy(() -> backend.query(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void persistsAcrossBackendInstances() {
        TestStoragePort storage = new TestStoragePort();
        Instant t0 = Instant.parse("2026-01-01T00:00:00Z");

        EpisodicMemoryBackend backend1 = new EpisodicMemoryBackend(
                storage, TENANT, SESSION);
        backend1.add(new WorkingMemoryEntry("assistant", "first entry", t0));

        EpisodicMemoryBackend backend2 = new EpisodicMemoryBackend(
                storage, TENANT, SESSION);
        assertThat(backend2.size()).isEqualTo(1);

        MemoryQuery q = new MemoryQuery("first",
                Set.of(MemoryKind.EPISODIC), 10);
        MemoryResult result = backend2.query(q);
        assertThat(result.hits()).hasSize(1);
        assertThat(result.hits().get(0).content()).isEqualTo("first entry");
    }
}
