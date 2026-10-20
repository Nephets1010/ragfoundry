/*
 * Copyright 2026 The ragfoundry Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.nephets.rag;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("InMemoryVectorStore 并发安全")
class InMemoryVectorStoreConcurrencyTest {

    @Test
    @DisplayName("多线程并发写入互不丢失（无竞态丢写）")
    void concurrentAddNoLoss() throws Exception {
        int threads = 8;
        int perThread = 250;
        InMemoryVectorStore store = new InMemoryVectorStore(threads * perThread + 16);
        List<Callable<Void>> tasks = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            final int base = t * perThread;
            tasks.add(
                    () -> {
                        for (int i = 0; i < perThread; i++) {
                            store.add("k" + (base + i), Embedding.of(new float[] {1, 0, 0}));
                        }
                        return null;
                    });
        }
        try (ExecutorService executor = Executors.newFixedThreadPool(threads)) {
            executor.invokeAll(tasks);
        }
        assertThat(store.size()).isEqualTo(threads * perThread);
    }

    @RepeatedTest(5)
    @DisplayName("写入与检索并发执行不抛并发修改异常，且结果自洽")
    void concurrentAddAndSearch() throws Exception {
        int total = 500;
        InMemoryVectorStore store = new InMemoryVectorStore(total + 16);
        AtomicReference<Throwable> searchError = new AtomicReference<>(null);

        Callable<Void> adder =
                () -> {
                    for (int i = 0; i < total; i++) {
                        store.add(
                                "k" + i,
                                Embedding.of(new float[] {i % 3, (i + 1) % 3, (i + 2) % 3}));
                    }
                    return null;
                };
        Callable<Void> searcher =
                () -> {
                    try {
                        for (int r = 0; r < 300; r++) {
                            store.search(Embedding.of(new float[] {1, 0, 0}), 5);
                        }
                    } catch (Throwable e) {
                        searchError.set(e);
                    }
                    return null;
                };

        try (ExecutorService executor = Executors.newFixedThreadPool(4)) {
            for (var f : executor.invokeAll(List.of(adder, searcher, searcher))) {
                f.get();
            }
        }
        assertThat(searchError.get()).describedAs("并发检索不应抛出任何异常").isNull();
        assertThat(store.size()).isEqualTo(total);
    }
}
