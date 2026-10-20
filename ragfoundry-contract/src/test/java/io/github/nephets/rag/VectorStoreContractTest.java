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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("VectorStore 接口契约")
class VectorStoreContractTest {

    /**
     * 一个遵守契约的参考实现：严格按接口文档（重复 id 覆盖、topK 降序、size/clear 语义）实现。 仅用于验证接口契约本身自洽， 真正的生产实现位于
     * ragfoundry-store。
     */
    static final class ReferenceStore implements VectorStore {
        private final Map<String, Embedding> map = new LinkedHashMap<>();

        @Override
        public void add(String id, Embedding embedding) {
            map.put(id, embedding);
        }

        @Override
        public List<ScoredId> search(Embedding query, int topK) {
            if (topK <= 0) {
                throw new IllegalArgumentException("topK 必须为正数，实际 " + topK);
            }
            List<ScoredId> all = new ArrayList<>();
            for (Map.Entry<String, Embedding> e : map.entrySet()) {
                all.add(new ScoredId(e.getKey(), query.cosineSimilarity(e.getValue())));
            }
            all.sort(Comparator.comparing(ScoredId::score).reversed());
            return all.subList(0, Math.min(topK, all.size()));
        }

        @Override
        public int size() {
            return map.size();
        }

        @Override
        public void clear() {
            map.clear();
        }
    }

    private final VectorStore store = new ReferenceStore();
    private final Embedding a = Embedding.of(new float[] {1, 0, 0});
    private final Embedding b = Embedding.of(new float[] {0, 1, 0});
    private final Embedding c = Embedding.of(new float[] {0, 0, 1});

    @Nested
    @DisplayName("契约：写入与计数")
    class Write {
        @Test
        @DisplayName("add 后 size 增加；重复 id 覆盖而非翻倍")
        void overwrite() {
            store.add("x", a);
            assertThat(store.size()).isEqualTo(1);
            store.add("x", b);
            assertThat(store.size()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("契约：检索降序与 topK 上限")
    class Search {
        @Test
        @DisplayName("结果按相似度严格降序")
        void descending() {
            store.add("a", a);
            store.add("b", b);
            store.add("c", c);
            List<ScoredId> top = store.search(Embedding.of(new float[] {1, 1, 0}), 3);
            assertThat(top).hasSize(3);
            assertThat(top.get(0).score()).isGreaterThanOrEqualTo(top.get(1).score());
            assertThat(top.get(1).score()).isGreaterThanOrEqualTo(top.get(2).score());
        }

        @Test
        @DisplayName("topK 超过库存时最多返回库存条数")
        void topKClampedToSize() {
            store.add("a", a);
            store.add("b", b);
            assertThat(store.search(Embedding.of(new float[] {1, 0, 0}), 10)).hasSize(2);
        }

        @Test
        @DisplayName("空库检索返回空列表")
        void empty() {
            assertThat(store.search(a, 3)).isEmpty();
        }

        @Test
        @DisplayName("topK<=0 抛 IllegalArgumentException")
        void badTopK() {
            store.add("a", a);
            assertThatThrownBy(() -> store.search(a, 0))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("契约：清空")
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
