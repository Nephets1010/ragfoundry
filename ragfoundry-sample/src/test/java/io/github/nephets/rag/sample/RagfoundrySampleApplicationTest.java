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
}
