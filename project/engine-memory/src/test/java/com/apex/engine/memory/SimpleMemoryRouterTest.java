package com.apex.engine.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SimpleMemoryRouterTest {

    private static MemoryBackend backend(MemoryKind kind, MemoryResult result) {
        return new MemoryBackend() {
            @Override
            public MemoryKind kind() {
                return kind;
            }

            @Override
            public void add(WorkingMemoryEntry entry) {
                throw new UnsupportedOperationException();
            }

            @Override
            public MemoryResult query(MemoryQuery query) {
                return result;
            }

            @Override
            public int size() {
                return 0;
            }

            @Override
            public void clear() {
                throw new UnsupportedOperationException();
            }
        };
    }

    private static MemoryHit hit(MemoryKind kind, String source, String content, double score) {
        return new MemoryHit(kind, source, content, score);
    }

    @Test
    void routesToMatchingKindsOnly() {
        MemoryBackend working = backend(MemoryKind.WORKING,
                new MemoryResult(List.of(hit(MemoryKind.WORKING, "s", "w1", 1.0)), Set.of(MemoryKind.WORKING)));
        MemoryBackend episodic = backend(MemoryKind.EPISODIC,
                new MemoryResult(List.of(hit(MemoryKind.EPISODIC, "s", "e1", 1.0)), Set.of(MemoryKind.EPISODIC)));

        SimpleMemoryRouter router = new SimpleMemoryRouter(List.of(working, episodic));
        MemoryQuery q = new MemoryQuery("x", Set.of(MemoryKind.WORKING), 10);
        MemoryResult result = router.route(q);

        assertThat(result.hits()).hasSize(1);
        assertThat(result.hits().get(0).kind()).isEqualTo(MemoryKind.WORKING);
        assertThat(result.queried()).containsExactly(MemoryKind.WORKING);
    }

    @Test
    void deduplicatesHitsByKindSourceContent() {
        MemoryHit h1 = hit(MemoryKind.WORKING, "src", "content", 1.0);
        MemoryHit h2 = hit(MemoryKind.WORKING, "src", "content", 0.9);
        MemoryBackend b1 = backend(MemoryKind.WORKING,
                new MemoryResult(List.of(h1, h2), Set.of(MemoryKind.WORKING)));

        SimpleMemoryRouter router = new SimpleMemoryRouter(List.of(b1));
        MemoryQuery q = new MemoryQuery("x", Set.of(MemoryKind.WORKING), 10);
        MemoryResult result = router.route(q);

        assertThat(result.hits()).hasSize(1);
        assertThat(result.hits().get(0).score()).isEqualTo(1.0);
    }

    @Test
    void sortsByDescendingScore() {
        MemoryHit low = hit(MemoryKind.WORKING, "s1", "low", 0.5);
        MemoryHit high = hit(MemoryKind.WORKING, "s2", "high", 1.0);
        MemoryHit mid = hit(MemoryKind.WORKING, "s3", "mid", 0.75);
        MemoryBackend b = backend(MemoryKind.WORKING,
                new MemoryResult(List.of(low, high, mid), Set.of(MemoryKind.WORKING)));

        SimpleMemoryRouter router = new SimpleMemoryRouter(List.of(b));
        MemoryQuery q = new MemoryQuery("x", Set.of(MemoryKind.WORKING), 10);
        MemoryResult result = router.route(q);

        assertThat(result.hits()).extracting(MemoryHit::score)
                .containsExactly(1.0, 0.75, 0.5);
    }

    @Test
    void capsAtMaxHits() {
        MemoryHit h1 = hit(MemoryKind.WORKING, "s1", "a", 1.0);
        MemoryHit h2 = hit(MemoryKind.WORKING, "s2", "b", 0.9);
        MemoryHit h3 = hit(MemoryKind.WORKING, "s3", "c", 0.8);
        MemoryBackend b = backend(MemoryKind.WORKING,
                new MemoryResult(List.of(h1, h2, h3), Set.of(MemoryKind.WORKING)));

        SimpleMemoryRouter router = new SimpleMemoryRouter(List.of(b));
        MemoryQuery q = new MemoryQuery("x", Set.of(MemoryKind.WORKING), 2);
        MemoryResult result = router.route(q);

        assertThat(result.hits()).hasSize(2);
        assertThat(result.hits().get(0).score()).isEqualTo(1.0);
        assertThat(result.hits().get(1).score()).isEqualTo(0.9);
    }

    @Test
    void skipsFailingBackends() {
        MemoryBackend good = backend(MemoryKind.WORKING,
                new MemoryResult(List.of(hit(MemoryKind.WORKING, "s", "ok", 1.0)), Set.of(MemoryKind.WORKING)));
        MemoryBackend bad = new MemoryBackend() {
            @Override
            public MemoryKind kind() {
                return MemoryKind.WORKING;
            }

            @Override
            public void add(WorkingMemoryEntry entry) {
                throw new UnsupportedOperationException();
            }

            @Override
            public MemoryResult query(MemoryQuery query) {
                throw new RuntimeException("boom");
            }

            @Override
            public int size() {
                return 0;
            }

            @Override
            public void clear() {
                throw new UnsupportedOperationException();
            }
        };

        SimpleMemoryRouter router = new SimpleMemoryRouter(List.of(good, bad));
        MemoryQuery q = new MemoryQuery("x", Set.of(MemoryKind.WORKING), 10);
        MemoryResult result = router.route(q);

        assertThat(result.hits()).hasSize(1);
        assertThat(result.hits().get(0).content()).isEqualTo("ok");
    }

    @Test
    void rejectsNullBackends() {
        assertThatThrownBy(() -> new SimpleMemoryRouter(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsNullQuery() {
        SimpleMemoryRouter router = new SimpleMemoryRouter(List.of());
        assertThatThrownBy(() -> router.route(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void emptyBackendsReturnsEmptyResult() {
        SimpleMemoryRouter router = new SimpleMemoryRouter(List.of());
        MemoryQuery q = new MemoryQuery("x", Set.of(MemoryKind.WORKING), 10);
        MemoryResult result = router.route(q);

        assertThat(result.hits()).isEmpty();
        assertThat(result.queried()).isEmpty();
    }
}
