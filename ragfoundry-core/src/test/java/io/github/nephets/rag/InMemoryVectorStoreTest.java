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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

@DisplayName("InMemoryVectorStore 内存向量索引")
class InMemoryVectorStoreTest {

    private final InMemoryVectorStore store = new InMemoryVectorStore();
    private final Embedding a = Embedding.of(new float[] {1, 0, 0});
    private final Embedding b = Embedding.of(new float[] {0, 1, 0});
    private final Embedding c = Embedding.of(new float[] {0, 0, 1});

    @Nested
    @DisplayName("写入与计数")
    class Write {
        @Test
        @DisplayName("add 后 size 增加")
        void size() {
            assertThat(store.size()).isZero();
            store.add("x", a);
            assertThat(store.size()).isEqualTo(1);
        }

        @Test
        @DisplayName("重复 id 覆盖而非翻倍")
        void overwrite() {
            store.add("x", a);
            store.add("x", b);
            assertThat(store.size()).isEqualTo(1);
        }

        @Test
        @DisplayName("空 id / null 向量被拒")
        void rejectsBadInput() {
            assertThatThrownBy(() -> store.add("", a)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> store.add("x", null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("检索")
    class Search {
        @Test
        @DisplayName("最相似的排第一")
        void topMatch() {
            store.add("a", a);
            store.add("b", b);
            store.add("c", c);
            List<ScoredId> top = store.search(Embedding.of(new float[] {0.9f, 0.1f, 0}), 1);
            assertThat(top).hasSize(1);
            assertThat(top.get(0).id()).isEqualTo("a");
            // 真实余弦相似度 = 0.9 / sqrt(0.9^2 + 0.1^2) ≈ 0.9939
            assertThat(top.get(0).score()).isCloseTo(0.9939f, within(1e-3f));
        }

        @Test
        @DisplayName("topK 限制返回条数且按得分降序")
        void topKOrdered() {
            store.add("a", a);
            store.add("b", b);
            store.add("c", c);
            List<ScoredId> top = store.search(Embedding.of(new float[] {1, 1, 0}), 2);
            assertThat(top).hasSize(2);
            assertThat(top.get(0).id()).isIn("a", "b");
            assertThat(top.get(0).score()).isGreaterThanOrEqualTo(top.get(1).score());
        }

        @Test
        @DisplayName("空库检索返回空")
        void emptyStore() {
            assertThat(store.search(a, 3)).isEmpty();
        }

        @Test
        @DisplayName("topK<=0 抛异常")
        void badTopK() {
            assertThatThrownBy(() -> store.search(a, 0))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("大量向量时只返回 topK 且严格降序（有界堆，非全量排序）")
        void topKHeapFromMany() {
            int n = 2000;
            // 第 i 个向量 (1, i, 0) 与 query (1,0,0) 的余弦 = 1/√(1+i²)，随 i 严格递减且分值分散，
            // 因此 topK 必须命中最小的 k 个下标，且按得分严格降序（验证有界堆而非全量排序）。
            for (int i = 0; i < n; i++) {
                store.add("v" + i, Embedding.of(new float[] {1, i, 0}));
            }
            int k = 10;
            List<ScoredId> top = store.search(Embedding.of(new float[] {1, 0, 0}), k);
            assertThat(top).hasSize(k);
            for (int i = 0; i < top.size(); i++) {
                assertThat(top.get(i).id()).isEqualTo("v" + i);
                if (i + 1 < top.size()) {
                    assertThat(top.get(i).score()).isGreaterThan(top.get(i + 1).score());
                }
            }
        }
    }

    @Nested
    @DisplayName("容量上限与 LRU 淘汰")
    class Capacity {

        @Test
        @DisplayName("容量<=0 的构造器抛异常")
        void rejectsBadCapacity() {
            assertThatThrownBy(() -> new InMemoryVectorStore(0))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new InMemoryVectorStore(-1))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("超过容量后 size 保持在容量上限")
        void sizeStaysAtCapacity() {
            int cap = 100;
            InMemoryVectorStore bounded = new InMemoryVectorStore(cap);
            for (int i = 0; i < cap * 3; i++) {
                bounded.add("v" + i, Embedding.of(new float[] {1, 0, 0}));
            }
            assertThat(bounded.size()).isEqualTo(cap);
        }

        @Test
        @DisplayName("淘汰后保留最近写入的条目，最旧的被逐出")
        void keepsMostRecent() {
            int cap = 100;
            InMemoryVectorStore bounded = new InMemoryVectorStore(cap);
            for (int i = 0; i < cap * 3; i++) {
                bounded.add("v" + i, Embedding.of(new float[] {1, 0, 0}));
            }
            // 最先写入的 v0..v(2*cap-1) 应已被 LRU 淘汰；
            // 最后写入的 v(2*cap)..v(3*cap-1) 必须仍在索引中。
            assertThat(bounded.size()).isEqualTo(cap);
            assertThat(bounded.search(Embedding.of(new float[] {1, 0, 0}), cap * 3))
                    .hasSize(cap)
                    .allSatisfy(
                            s -> {
                                int idx = Integer.parseInt(s.id().substring(1));
                                assertThat(idx).isGreaterThanOrEqualTo(cap * 2);
                            });
        }

        @Test
        @DisplayName("默认构造器容量足够大，常规写入不触发淘汰")
        void defaultCapacityNotEvicted() {
            InMemoryVectorStore store = new InMemoryVectorStore();
            int n = 2000;
            for (int i = 0; i < n; i++) {
                store.add("v" + i, Embedding.of(new float[] {1, 0, 0}));
            }
            assertThat(store.size()).isEqualTo(n);
        }
    }

    @Nested
    @DisplayName("清空")
    class Clear {
        @Test
        @DisplayName("clear 后 size 归零")
        void clears() {
            store.add("a", a);
            store.clear();
            assertThat(store.size()).isZero();
        }
    }
}
