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
package io.github.nephets.rag.sample;

import io.github.nephets.rag.Document;
import io.github.nephets.rag.Retriever;
import io.github.nephets.rag.ScoredChunk;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DisplayName("ragfoundry-sample 端到端装配与检索")
class RagfoundrySampleApplicationTest {

    @Autowired private Retriever retriever;

    @Test
    @DisplayName("Spring 容器自动提供可检索的 Retriever")
    void retrieveEndToEnd() {
        retriever.index(Document.of("fruit", "苹果 香蕉 水果 维生素", Map.of()));
        retriever.index(Document.of("space", "火箭 发动机 推力 轨道", Map.of()));

        List<ScoredChunk> hits = retriever.retrieve("哪些水果含维生素", 1);
        assertThat(hits).isNotEmpty();
        assertThat(hits.get(0).chunk().documentId()).isEqualTo("fruit");
    }

    @Test
    @DisplayName("检索结果按相似度降序返回")
    void orderedByScore() {
        retriever.index(Document.of("a", "苹果 香蕉", Map.of()));
        retriever.index(Document.of("b", "苹果 橙子", Map.of()));
        retriever.index(Document.of("c", "苹果 梨", Map.of()));

        List<ScoredChunk> hits = retriever.retrieve("苹果 香蕉", 3);
        assertThat(hits).hasSize(3);
        assertThat(hits.get(0).score()).isGreaterThanOrEqualTo(hits.get(2).score());
    }

    @Test
    @DisplayName("空白或 null 查询返回空结果，不报错")
    void blankQuerySafe() {
        retriever.index(Document.of("a", "苹果 香蕉", Map.of()));
        assertThat(retriever.retrieve("", 3)).isEmpty();
        assertThat(retriever.retrieve(null, 3)).isEmpty();
        assertThat(retriever.retrieve("   ", 3)).isEmpty();
    }

    @Test
    @DisplayName("跨多文档检索命中正确来源")
    void retrieveAcrossMultipleDocs() {
        retriever.index(Document.of("fruit", "苹果 香蕉 樱桃 都是水果", Map.of()));
        retriever.index(Document.of("space", "火箭 发动机 推力 轨道", Map.of()));
        retriever.index(Document.of("code", "Java 变量 编译器 函数", Map.of()));

        List<ScoredChunk> hits = retriever.retrieve("火箭发动机", 2);
        assertThat(hits).isNotEmpty();
        assertThat(hits.get(0).chunk().documentId()).isEqualTo("space");
    }

    @Test
    @DisplayName("重复索引同一文档不破坏检索")
    void reindexIdempotent() {
        Document d = Document.of("dup", "苹果 香蕉 樱桃", Map.of());
        retriever.index(d);
        retriever.index(d);
        List<ScoredChunk> hits = retriever.retrieve("苹果", 1);
        assertThat(hits).isNotEmpty();
        assertThat(hits.get(0).chunk().documentId()).isEqualTo("dup");
    }

    @Test
    @DisplayName("topK 不超过已索引切片总数")
    void topKNotExceedChunks() {
        retriever.index(Document.of("a", "苹果 香蕉 樱桃", Map.of()));
        retriever.index(Document.of("b", "火箭 飞船", Map.of()));
        List<ScoredChunk> hits = retriever.retrieve("苹果 火箭", 100);
        assertThat(hits).hasSizeLessThanOrEqualTo(retriever.indexedChunkCount());
    }
}
