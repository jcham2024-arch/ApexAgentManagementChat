package com.apex.engine.store.sqlite;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static com.apex.engine.store.sqlite.SqliteStoragePortTest.entry;
import static org.assertj.core.api.Assertions.assertThat;

/** WAL mode must let writers and a reader share the file. */
class SqliteStoragePortConcurrencyTest {

    @TempDir
    Path tempDir;

    SqliteStoragePort port;
    ExecutorService pool;

    @BeforeEach
    void setUp() {
        port = new SqliteStoragePort(tempDir.resolve("test.db"));
        pool = Executors.newFixedThreadPool(4);
    }

    @AfterEach
    void tearDown() {
        pool.shutdownNow();
    }

    @Test
    void concurrentAppendsToDifferentSessions() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> writers = new ArrayList<>();

        for (String session : List.of("a", "b")) {
            writers.add(pool.submit(() -> {
                start.await();
                for (int i = 0; i < 50; i++) {
                    port.appendStep(entry(session + "-" + i, session, i));
                }
                return null;
            }));
        }

        start.countDown();
        for (Future<?> writer : writers) {
            writer.get(30, TimeUnit.SECONDS);
        }

        assertThat(port.stepsFor("a")).hasSize(50);
        assertThat(port.stepsFor("b")).hasSize(50);
    }

    @Test
    void concurrentAppendsToSameSession() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> writers = new ArrayList<>();

        for (int thread = 0; thread < 2; thread++) {
            int offset = thread * 50;
            writers.add(pool.submit(() -> {
                start.await();
                for (int i = 0; i < 50; i++) {
                    int index = offset + i;
                    port.appendStep(entry("shared-" + index, "shared", index));
                }
                return null;
            }));
        }

        start.countDown();
        for (Future<?> writer : writers) {
            writer.get(30, TimeUnit.SECONDS);
        }

        assertThat(port.stepsFor("shared")).hasSize(100);
    }

    @Test
    void queriesDuringAppendNeverExceedWrittenAndNeverThrow() throws Exception {
        AtomicInteger maxSeen = new AtomicInteger();
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Future<?> appender = pool.submit(() -> {
            for (int i = 0; i < 100; i++) {
                port.appendStep(entry("s-" + i, "s", i));
            }
            return null;
        });

        while (!appender.isDone()) {
            try {
                int size = port.stepsFor("s").size();
                maxSeen.accumulateAndGet(size, Math::max);
            } catch (RuntimeException e) {
                failure.compareAndSet(null, e);
                break;
            }
            Thread.sleep(10L);
        }

        appender.get(30, TimeUnit.SECONDS);

        assertThat(failure.get()).isNull();
        assertThat(maxSeen.get()).isLessThanOrEqualTo(100);
        assertThat(port.stepsFor("s")).hasSize(100);
    }
}
