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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RAG 管线跨特性集成（切片 + 确定性嵌入 + 内存检索 + 回填）")
class RagPipelineIntegrationTest {

    private final EmbeddingModel model = new DeterministicEmbedding(32);
    private final ChunkingStrategy chunker = new FixedSizeChunker(50, 0);
    private final VectorStore store = new InMemoryVectorStore();
    private final Retriever retriever = new Retriever(chunker, model, store);

    @Nested
    @DisplayName("多文档检索质量")
    class MultiDoc {
        @Test
        @DisplayName("建库后检索命中正确文档，且结果按相似度降序")
        void retrieveRanksCorrectly() {
            retriever.index(Document.of("fruit", "苹果 香蕉 樱桃 水果 维生素", Map.of()));
            retriever.index(Document.of("space", "火箭 发动机 推力 轨道 卫星", Map.of()));
            retriever.index(Document.of("code", "Java 编程 编译器 变量 函数", Map.of()));

            List<ScoredChunk> hits = retriever.retrieve("火箭如何推进", 3);
            assertThat(hits).isNotEmpty();
            assertThat(hits.get(0).chunk().documentId()).isEqualTo("space");
            for (int i = 1; i < hits.size(); i++) {
                assertThat(hits.get(i - 1).score()).isGreaterThanOrEqualTo(hits.get(i).score());
            }
        }

        @Test
        @DisplayName("检索结果回填原文，文本与查询主题相关")
        void backfillsChunkText() {
            retriever.index(Document.of("fruit", "苹果 香蕉 樱桃", Map.of()));
            List<ScoredChunk> hits = retriever.retrieve("苹果", 1);
            assertThat(hits).isNotEmpty();
            assertThat(hits.get(0).chunk().text()).contains("苹果");
        }
    }

    @Nested
    @DisplayName("建库与计数")
    class Indexing {
        @Test
        @DisplayName("多文档建库后已索引切片数累加")
        void chunkCountAcrossDocs() {
            retriever.index(Document.of("a", "苹果 香蕉 樱桃 西瓜 芒果", Map.of()));
            retriever.index(Document.of("b", "火箭 卫星 飞船 空间站", Map.of()));
            assertThat(retriever.indexedChunkCount()).isGreaterThan(1);
        }

        @Test
        @DisplayName("重复索引同一文档幂等（切片数不翻倍）")
        void reindexIdempotent() {
            Document d = Document.of("dup", "苹果 香蕉 樱桃 西瓜", Map.of());
            retriever.index(d);
            int first = retriever.indexedChunkCount();
            retriever.index(d);
            assertThat(retriever.indexedChunkCount()).isEqualTo(first);
        }
    }

    @Nested
    @DisplayName("边界与健壮性")
    class Robustness {
        @Test
        @DisplayName("空与空白查询直接返回空，不建索引也不报错")
        void blankQuerySafe() {
            retriever.index(Document.of("a", "苹果 香蕉", Map.of()));
            assertThat(retriever.retrieve("", 2)).isEmpty();
            assertThat(retriever.retrieve("   ", 2)).isEmpty();
            assertThat(retriever.retrieve(null, 2)).isEmpty();
        }

        @Test
        @DisplayName("topK 不超过已索引切片总数")
        void topKLimit() {
            retriever.index(Document.of("a", "苹果 香蕉 樱桃", Map.of()));
            retriever.index(Document.of("b", "火箭 飞船", Map.of()));
            List<ScoredChunk> all = retriever.retrieve("苹果 火箭", 100);
            assertThat(all).hasSizeLessThanOrEqualTo(retriever.indexedChunkCount());
        }
    }
}
