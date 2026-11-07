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
    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");

    private static class TestStoragePort implements StoragePort {
        private final List<StepLogEntry> entries = new CopyOnWriteArrayList<>();
        @Override
        public void appendStep(StepLogEntry entry) { entries.add(entry); }
        @Override
        public List<StepLogEntry> stepsFor(TenantId tenantId, String sessionId) {
            List<StepLogEntry> result = new ArrayList<>();
            for (StepLogEntry entry : entries) {
                if (entry.tenantId().equals(tenantId) && entry.sessionId().equals(sessionId)) {
                    result.add(entry);
                }
            }
            return result;
        }
        @Override
        public void clear(TenantId tenantId, String sessionId) {
            entries.removeIf(e -> e.tenantId().equals(tenantId) && e.sessionId().equals(sessionId));
        }
    }

    private static EpisodicMemoryBackend backend() {
        return new EpisodicMemoryBackend(new TestStoragePort(), TENANT, SESSION);
    }

    private static WorkingMemoryEntry entry(String content) {
        return new WorkingMemoryEntry("assistant", content, T0);
    }

    @Test
    void kindIsEpisodic() {
        assertThat(backend().kind()).isEqualTo(MemoryKind.EPISODIC);
    }

    @Test
    void addAndQueryExactMatch() {
        EpisodicMemoryBackend b = backend();
        b.add(entry("the quick brown fox"));
        MemoryQuery q = new MemoryQuery("quick", Set.of(MemoryKind.EPISODIC), 10);
        MemoryResult result = b.query(q);
        assertThat(result.hits()).hasSize(1);
        assertThat(result.hits().get(0).score()).isEqualTo(1.0);
        assertThat(result.hits().get(0).kind()).isEqualTo(MemoryKind.EPISODIC);
        assertThat(result.hits().get(0).source()).isEqualTo("assistant");
        assertThat(result.hits().get(0).content()).isEqualTo("the quick brown fox");
        assertThat(result.queried()).containsExactly(MemoryKind.EPISODIC);
    }

    @Test
    void queryCaseInsensitiveScoresLower() {
        EpisodicMemoryBackend b = backend();
        b.add(entry("The QUICK brown fox"));
        MemoryQuery q = new MemoryQuery("quick", Set.of(MemoryKind.EPISODIC), 10);
        assertThat(b.query(q).hits().get(0).score()).isEqualTo(0.8);
    }

    @Test
    void queryMissReturnsEmpty() {
        EpisodicMemoryBackend b = backend();
        b.add(entry("the quick brown fox"));
        MemoryQuery q = new MemoryQuery("zebra", Set.of(MemoryKind.EPISODIC), 10);
        assertThat(b.query(q).hits()).isEmpty();
        assertThat(b.query(q).queried()).containsExactly(MemoryKind.EPISODIC);
    }

    @Test
    void queryRespectsMaxHits() {
        EpisodicMemoryBackend b = backend();
        b.add(new WorkingMemoryEntry("assistant", "quick alpha", T0));
        b.add(new WorkingMemoryEntry("assistant", "quick beta", T0));
        b.add(new WorkingMemoryEntry("assistant", "quick gamma", T0));
        MemoryQuery q = new MemoryQuery("quick", Set.of(MemoryKind.EPISODIC), 2);
        assertThat(b.query(q).hits()).hasSize(2);
    }

    @Test
    void queryOtherKindReturnsEmpty() {
        EpisodicMemoryBackend b = backend();
        b.add(entry("the quick brown fox"));
        MemoryQuery q = new MemoryQuery("quick", Set.of(MemoryKind.WORKING), 10);
        assertThat(b.query(q).hits()).isEmpty();
        assertThat(b.query(q).queried()).isEmpty();
    }

    @Test
    void sizeIncrementsOnAdd() {
        EpisodicMemoryBackend b = backend();
        assertThat(b.size()).isZero();
        b.add(entry("one"));
        assertThat(b.size()).isEqualTo(1);
        b.add(entry("two"));
        assertThat(b.size()).isEqualTo(2);
    }

    @Test
    void rejectsNullStorage() {
        assertThatThrownBy(() -> new EpisodicMemoryBackend(null, TENANT, SESSION))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsNullTenant() {
        assertThatThrownBy(() -> new EpisodicMemoryBackend(new TestStoragePort(), null, SESSION))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsNullSession() {
        assertThatThrownBy(() -> new EpisodicMemoryBackend(new TestStoragePort(), TENANT, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsNullEntry() {
        assertThatThrownBy(() -> backend().add(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void clearRemovesAllEntries() {
        EpisodicMemoryBackend b = backend();
        b.add(new WorkingMemoryEntry("assistant", "entry one", T0));
        b.add(new WorkingMemoryEntry("user", "entry two", T0));
        assertThat(b.size()).isEqualTo(2);
        b.clear();
        assertThat(b.size()).isEqualTo(0);
        MemoryQuery q = new MemoryQuery("entry", Set.of(MemoryKind.EPISODIC), 10);
        assertThat(b.query(q).hits()).isEmpty();
    }

    @Test
    void queryRejectsNull() {
        assertThatThrownBy(() -> backend().query(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void persistsAcrossBackendInstances() {
        TestStoragePort storage = new TestStoragePort();
        EpisodicMemoryBackend b1 = new EpisodicMemoryBackend(storage, TENANT, SESSION);
        b1.add(new WorkingMemoryEntry("assistant", "first entry", T0));
        EpisodicMemoryBackend b2 = new EpisodicMemoryBackend(storage, TENANT, SESSION);
        assertThat(b2.size()).isEqualTo(1);
        MemoryQuery q = new MemoryQuery("first", Set.of(MemoryKind.EPISODIC), 10);
        assertThat(b2.query(q).hits()).hasSize(1);
        assertThat(b2.query(q).hits().get(0).content()).isEqualTo("first entry");
    }
}
