// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.demanddriventrafficevaluator.modelfeature;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Thread-safety contract of {@link Registry} (issue #16): records are published as immutable
 * snapshots, so readers never see a map that a register() call is changing.
 */
class RegistryTest {

    private static final int REGISTRATIONS = 1_000;
    private static final int READERS = 4;

    private static final class TestRegistry extends Registry<Object> {
        TestRegistry(Map<String, Object> records) {
            super(records);
        }

        @Override
        protected String getRegistryType() {
            return "Test";
        }
    }

    @Test
    void getRecords_isUnmodifiable() {
        TestRegistry registry = new TestRegistry(new HashMap<>(Map.of("a", "A")));

        Map<String, Object> records = registry.getRecords();

        assertThrows(UnsupportedOperationException.class, () -> records.put("b", "B"));
        assertThrows(UnsupportedOperationException.class, () -> records.remove("a"));
        assertThrows(UnsupportedOperationException.class, records::clear);
    }

    @Test
    void constructor_copiesMap_laterChangesToCallerMapAreNotSeen() {
        Map<String, Object> source = new HashMap<>(Map.of("a", "A"));
        TestRegistry registry = new TestRegistry(source);

        source.put("b", "B");

        assertFalse(registry.getRecords().containsKey("b"));
        assertThrows(IllegalArgumentException.class, () -> registry.get("b"));
    }

    @Test
    void register_doesNotChangeEarlierSnapshot_andIsVisibleInNewSnapshot() {
        TestRegistry registry = new TestRegistry(new HashMap<>(Map.of("a", "A")));
        Map<String, Object> before = registry.getRecords();

        registry.register("b", "B");

        assertEquals(Map.of("a", "A"), before);
        assertEquals(Map.of("a", "A", "b", "B"), registry.getRecords());
        assertEquals("B", registry.get("b"));
    }

    @Test
    void register_whileIteratingRecords_doesNotThrowConcurrentModification() {
        TestRegistry registry = new TestRegistry(new HashMap<>(Map.of("a", "A", "b", "B")));

        // Single-threaded, deterministic: a live HashMap fails fast here.
        assertDoesNotThrow(() -> {
            int i = 0;
            for (String key : registry.getRecords().keySet()) {
                registry.register("new-" + key + i++, "X");
            }
        });
        assertEquals(4, registry.getRecords().size());
    }

    @Test
    void get_nameRegisteredWithNullValue_returnsNull() {
        TestRegistry registry = new TestRegistry(new HashMap<>());

        registry.register("n", (Object) null);

        assertNull(registry.get("n"));
        assertThrows(IllegalArgumentException.class, () -> registry.register("n", "N"));
    }

    @Test
    void get_unknownName_throwsWithSameMessage() {
        TestRegistry registry = new TestRegistry(new HashMap<>());

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> registry.get("x"));
        assertEquals("Test with name x not registered", e.getMessage());
    }

    @Test
    void register_duplicateName_throwsAndKeepsOriginal() {
        TestRegistry registry = new TestRegistry(new HashMap<>(Map.of("a", "A")));

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> registry.register("a", "Z"));
        assertEquals("Test with name a already registered", e.getMessage());
        assertEquals("A", registry.get("a"));
    }

    @Test
    void registerWhileReadersGetAndIterate_readersNeverFail() throws Exception {
        Object seed = new Object();
        TestRegistry registry = new TestRegistry(new HashMap<>(Map.of("seed", seed)));
        ConcurrentLinkedQueue<Throwable> failures = new ConcurrentLinkedQueue<>();
        AtomicBoolean writerDone = new AtomicBoolean();
        // Snapshots seen by readers with a size strictly between the start and the end, i.e. reads
        // that overlapped the writer. Without one the test proves nothing, so it must be > 0.
        AtomicInteger overlappingReads = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);

        Thread writer = new Thread(() -> {
            try {
                start.await();
                for (int i = 0; i < REGISTRATIONS; i++) {
                    registry.register("k" + i, "v" + i);
                    if (i == REGISTRATIONS / 2) {
                        // Hold the registry half full until a reader has seen it, so readers overlap
                        // the writer even when they are scheduled late.
                        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
                        while (overlappingReads.get() == 0 && System.nanoTime() < deadline) {
                            Thread.onSpinWait();
                        }
                    }
                }
            } catch (Throwable t) {
                failures.add(t);
            } finally {
                writerDone.set(true);
            }
        });
        List<Thread> readers = new ArrayList<>();
        for (int r = 0; r < READERS; r++) {
            readers.add(new Thread(() -> {
                try {
                    start.await();
                    while (!writerDone.get()) {
                        assertSame(seed, registry.get("seed"));
                        Map<String, Object> snapshot = registry.getRecords();
                        int seen = 0;
                        for (Map.Entry<String, Object> entry : snapshot.entrySet()) {
                            if (entry.getValue() != null) {
                                seen++;
                            }
                        }
                        assertEquals(snapshot.size(), seen);
                        if (seen > 1 && seen < REGISTRATIONS + 1) {
                            overlappingReads.incrementAndGet();
                        }
                    }
                } catch (Throwable t) {
                    failures.add(t);
                }
            }));
        }
        writer.start();
        readers.forEach(Thread::start);
        start.countDown();
        writer.join(TimeUnit.SECONDS.toMillis(60));
        assertFalse(writer.isAlive(), "writer did not finish");
        for (Thread reader : readers) {
            reader.join(TimeUnit.SECONDS.toMillis(60));
            assertFalse(reader.isAlive(), "reader did not finish");
        }

        assertTrue(failures.isEmpty(), () -> "reader/writer failures: " + failures);
        assertTrue(overlappingReads.get() > 0, "no read overlapped the writer");
        assertEquals(REGISTRATIONS + 1, registry.getRecords().size());
        for (int i = 0; i < REGISTRATIONS; i++) {
            assertEquals("v" + i, registry.get("k" + i));
        }
    }
}
