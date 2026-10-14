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

@DisplayName("Retriever 端到端检索管线")
class RetrieverTest {

    private final EmbeddingModel model = new DeterministicEmbedding(32);
    private final ChunkingStrategy chunker = new FixedSizeChunker(100, 0);
    private final VectorStore store = new InMemoryVectorStore();
    private final Retriever retriever = new Retriever(chunker, model, store);

    @Nested
    @DisplayName("建库")
    class Indexing {
        @Test
        @DisplayName("索引文档会增加已索引切片数")
        void countsChunks() {
            retriever.index(Document.of("doc1", "苹果 香蕉 樱桃 都是水果", java.util.Map.of()));
            assertThat(retriever.indexedChunkCount()).isGreaterThan(0);
        }

        @Test
        @DisplayName("空白文档不产生切片")
        void blankDocNotIndexed() {
            retriever.index(Document.of("doc0", "   ", java.util.Map.of()));
            assertThat(retriever.indexedChunkCount()).isZero();
        }

        @Test
        @DisplayName("构造参数不能为 null")
        void noNullArgs() {
            assertThatThrownBy(() -> new Retriever(null, model, store))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new Retriever(chunker, null, store))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new Retriever(chunker, model, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("检索")
    class Retrieve {
        @Test
        @DisplayName("查询命中内容相关的文档")
        void relevantFirst() {
            retriever.index(Document.of("fruit", "苹果 香蕉 樱桃 都是水果", java.util.Map.of()));
            retriever.index(Document.of("space", "火箭 发动机 推力 轨道", java.util.Map.of()));
            List<ScoredChunk> hits = retriever.retrieve("苹果的营养", 1);
            assertThat(hits).isNotEmpty();
            assertThat(hits.get(0).chunk().documentId()).isEqualTo("fruit");
        }

        @Test
        @DisplayName("topK 限制返回条数")
        void topK() {
            retriever.index(Document.of("a", "苹果 香蕉", java.util.Map.of()));
            retriever.index(Document.of("b", "苹果 橙子", java.util.Map.of()));
            retriever.index(Document.of("c", "苹果 梨", java.util.Map.of()));
            assertThat(retriever.retrieve("苹果", 2)).hasSize(2);
        }

        @Test
        @DisplayName("结果按相似度降序")
        void ordered() {
            retriever.index(Document.of("a", "苹果 香蕉", java.util.Map.of()));
            retriever.index(Document.of("b", "苹果 橙子", java.util.Map.of()));
            List<ScoredChunk> hits = retriever.retrieve("苹果 香蕉", 2);
            assertThat(hits.get(0).score()).isGreaterThanOrEqualTo(hits.get(1).score());
        }

        @Test
        @DisplayName("空查询返回空")
        void blankQuery() {
            retriever.index(Document.of("a", "苹果 香蕉", java.util.Map.of()));
            assertThat(retriever.retrieve("", 3)).isEmpty();
            assertThat(retriever.retrieve(null, 3)).isEmpty();
        }

        @Test
        @DisplayName("topK<=0 抛异常")
        void badTopK() {
            assertThatThrownBy(() -> retriever.retrieve("x", 0))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
