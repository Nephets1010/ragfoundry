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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Retriever 行为不变量（独立实例，隔离各测试）")
class RetrieverBehaviorTest {

    private Retriever newRetriever() {
        return new Retriever(
                new RecursiveChunker(64),
                new DeterministicEmbedding(32),
                new InMemoryVectorStore());
    }

    @Test
    @DisplayName("检索命中后回填的切片非空且含正确来源文档")
    void retrieveReturnsPopulatedChunk() {
        Retriever r = newRetriever();
        r.index(Document.of("docA", "苹果 香蕉 樱桃 都是水果", Map.of()));
        r.index(Document.of("docB", "火箭 发动机 推力 轨道", Map.of()));
        List<ScoredChunk> hits = r.retrieve("苹果", 1);
        assertThat(hits).isNotEmpty();
        assertThat(hits.get(0).chunk().text()).isNotEmpty();
        assertThat(hits.get(0).chunk().documentId()).isEqualTo("docA");
    }

    @Test
    @DisplayName("所有相似度得分都落在 [-1, 1] 区间")
    void scoresWithinUnitRange() {
        Retriever r = newRetriever();
        r.index(Document.of("docA", "苹果 香蕉 樱桃", Map.of()));
        r.index(Document.of("docB", "火箭 飞船 卫星", Map.of()));
        r.index(Document.of("docC", "猫 狗 动物", Map.of()));
        List<ScoredChunk> hits = r.retrieve("苹果 火箭 猫", 10);
        assertThat(hits).isNotEmpty();
        assertThat(hits).allSatisfy(h -> assertThat(h.score()).isBetween(-1f, 1f));
    }

    @Test
    @DisplayName("同一查询两次检索结果顺序完全一致（确定性）")
    void repeatedRetrieveDeterministicOrder() {
        Retriever r = newRetriever();
        r.index(Document.of("docA", "苹果 香蕉 樱桃", Map.of()));
        r.index(Document.of("docB", "苹果 橙子 梨", Map.of()));
        r.index(Document.of("docC", "苹果 西瓜 葡萄", Map.of()));
        List<ScoredChunk> first = r.retrieve("苹果", 3);
        List<ScoredChunk> second = r.retrieve("苹果", 3);
        assertThat(first.stream().map(h -> h.chunk().id()).toList())
                .isEqualTo(second.stream().map(h -> h.chunk().id()).toList());
    }

    @Test
    @DisplayName("更小的 topK 结果是更大 topK 结果的前缀")
    void smallerTopKIsPrefixOfLarger() {
        Retriever r = newRetriever();
        r.index(Document.of("docA", "苹果 香蕉 樱桃", Map.of()));
        r.index(Document.of("docB", "苹果 橙子 梨", Map.of()));
        r.index(Document.of("docC", "苹果 西瓜 葡萄", Map.of()));
        List<ScoredChunk> top1 = r.retrieve("苹果", 1);
        List<ScoredChunk> top3 = r.retrieve("苹果", 3);
        assertThat(top1.get(0).chunk().id()).isEqualTo(top3.get(0).chunk().id());
    }

    @Test
    @DisplayName("索引单篇文档后，已索引切片数等于该文档的切片数")
    void indexedChunkCountEqualsDocumentChunkCount() {
        Retriever r = newRetriever();
        Document d = Document.of("docA", "苹果 香蕉 樱桃 西瓜 葡萄 橙子", Map.of());
        int expected = new RecursiveChunker(64).chunk(d).size();
        r.index(d);
        assertThat(r.indexedChunkCount()).isEqualTo(expected);
    }
}
