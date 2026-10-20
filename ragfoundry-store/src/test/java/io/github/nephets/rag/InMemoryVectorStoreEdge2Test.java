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
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@DisplayName("InMemoryVectorStore 语义与边界（补充）")
class InMemoryVectorStoreEdge2Test {

    private final InMemoryVectorStore store = new InMemoryVectorStore();
    private final Embedding x = Embedding.of(new float[] {1, 0, 0});
    private final Embedding y = Embedding.of(new float[] {0, 1, 0});

    @Test
    @DisplayName("用自身向量检索自己得分为 1 且排第一")
    void identitySearchReturnsSelfWithScoreOne() {
        store.add("a", x);
        List<ScoredId> top = store.search(x, 1);
        assertThat(top).hasSize(1);
        assertThat(top.get(0).id()).isEqualTo("a");
        assertThat(top.get(0).score()).isCloseTo(1f, within(1e-6f));
    }

    @Test
    @DisplayName("零向量查询对所有条目得分为 0，但仍按 topK 返回")
    void queryZeroVectorReturnsZeroScore() {
        store.add("a", x);
        store.add("b", y);
        List<ScoredId> top = store.search(Embedding.of(new float[] {0, 0, 0}), 5);
        assertThat(top).hasSize(2);
        assertThat(top).allSatisfy(s -> assertThat(s.score()).isCloseTo(0f, within(1e-6f)));
    }

    @Test
    @DisplayName("同一查询两次检索结果顺序完全一致（确定性）")
    void searchDeterministicAcrossCalls() {
        store.add("a", x);
        store.add("b", y);
        store.add("c", Embedding.of(new float[] {0, 0, 1}));
        Embedding q = Embedding.of(new float[] {1, 1, 0});
        List<ScoredId> first = store.search(q, 3);
        List<ScoredId> second = store.search(q, 3);
        assertThat(first.stream().map(ScoredId::id).toList())
                .isEqualTo(second.stream().map(ScoredId::id).toList());
    }

    @Test
    @DisplayName("clear 之后可继续正常写入（容量与计数重置）")
    void addAfterClearWorks() {
        store.add("a", x);
        store.clear();
        assertThat(store.size()).isZero();
        store.add("b", y);
        assertThat(store.size()).isEqualTo(1);
    }

    @Test
    @DisplayName("覆盖写入不改变条目数，且 search 反映最新向量")
    void overwriteKeepsSingleEntry() {
        store.add("k", x);
        store.add("k", y);
        assertThat(store.size()).isEqualTo(1);
        List<ScoredId> top = store.search(y, 1);
        assertThat(top.get(0).id()).isEqualTo("k");
        assertThat(top.get(0).score()).isCloseTo(1f, within(1e-6f));
    }
}
