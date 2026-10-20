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
@DisplayName("ragfoundry-sample 端到端装配与检索（补充）")
class SampleAppExtraTest {

    @Autowired private Retriever retriever;

    @Test
    @DisplayName("检索结果携带非空切片原文")
    void retrieveReturnsNonEmptyChunkText() {
        retriever.index(Document.of("extra1", "苹果 香蕉 樱桃 都是水果", Map.of()));
        List<ScoredChunk> hits = retriever.retrieve("苹果", 1);
        assertThat(hits).isNotEmpty();
        assertThat(hits.get(0).chunk().text()).isNotEmpty();
    }

    @Test
    @DisplayName("检索结果保留来源文档元数据与切片策略元数据")
    void retrieveResultCarriesSourceMetadata() {
        // 用唯一词元，并在结果集中定位本篇文档（确定性嵌入非语义，仅断言「命中且保留元数据」）
        retriever.index(Document.of("extra2", "猕猴桃 火龙果 枇杷 metadataToken", Map.of("lang", "zh")));
        List<ScoredChunk> hits = retriever.retrieve("metadataToken", 100);
        assertThat(hits)
                .anySatisfy(
                        h -> {
                            assertThat(h.chunk().documentId()).isEqualTo("extra2");
                            assertThat(h.chunk().metadata()).containsEntry("lang", "zh");
                            assertThat(h.chunk().metadata()).containsKey("chunk_strategy");
                        });
    }

    @Test
    @DisplayName("索引文档后已索引切片数严格增加")
    void indexedChunkCountIncreasesAfterIndexing() {
        int before = retriever.indexedChunkCount();
        retriever.index(Document.of("extra3", "苹果 香蕉 樱桃 苹果 香蕉 樱桃", Map.of()));
        assertThat(retriever.indexedChunkCount()).isGreaterThan(before);
    }

    @Test
    @DisplayName("topK=1 只返回一条结果")
    void topKOneReturnsSingle() {
        retriever.index(Document.of("extra4a", "苹果 香蕉", Map.of()));
        retriever.index(Document.of("extra4b", "火箭 飞船", Map.of()));
        assertThat(retriever.retrieve("苹果", 1)).hasSize(1);
    }

    @Test
    @DisplayName("多文档建库后切片数累加")
    void multipleDocumentsAccumulate() {
        int before = retriever.indexedChunkCount();
        retriever.index(Document.of("extra5a", "苹果 香蕉", Map.of()));
        retriever.index(Document.of("extra5b", "火箭 飞船", Map.of()));
        assertThat(retriever.indexedChunkCount()).isGreaterThanOrEqualTo(before + 1);
    }

    @Test
    @DisplayName("中文查询命中中文文档")
    void chineseQueryReturnsChineseDoc() {
        // 用唯一词元「猕猴桃」，断言该文档出现在检索结果中（确定性嵌入非语义，不卡首条排名）
        retriever.index(Document.of("extra6a", "猕猴桃 火龙果 枇杷 山竹", Map.of()));
        retriever.index(Document.of("extra6b", "火箭 飞船 卫星 轨道", Map.of()));
        List<ScoredChunk> hits = retriever.retrieve("猕猴桃", 100);
        assertThat(hits).anySatisfy(h -> assertThat(h.chunk().documentId()).isEqualTo("extra6a"));
    }

    @Test
    @DisplayName("重复索引同一文档，已索引切片数保持稳定")
    void reindexSameDocKeepsCountStable() {
        Document d = Document.of("extra7", "苹果 香蕉 樱桃", Map.of());
        retriever.index(d);
        int first = retriever.indexedChunkCount();
        retriever.index(d);
        assertThat(retriever.indexedChunkCount()).isEqualTo(first);
    }

    @Test
    @DisplayName("相关查询的相似度得分为正")
    void retrieveScorePositiveForRelevantMatch() {
        retriever.index(Document.of("extra8", "苹果 香蕉 樱桃 水果 维生素", Map.of()));
        List<ScoredChunk> hits = retriever.retrieve("苹果 香蕉 水果", 1);
        assertThat(hits).isNotEmpty();
        assertThat(hits.get(0).score()).isGreaterThan(0f);
    }

    @Test
    @DisplayName("检索结果条数不超过 topK 上限")
    void retrieveRespectsTopKBound() {
        retriever.index(Document.of("extra9a", "苹果 香蕉", Map.of()));
        retriever.index(Document.of("extra9b", "火箭 飞船", Map.of()));
        retriever.index(Document.of("extra9c", "猫 狗 动物", Map.of()));
        List<ScoredChunk> hits = retriever.retrieve("苹果 火箭 猫", 2);
        assertThat(hits).hasSize(2);
    }

    @Test
    @DisplayName("索引两篇不同文档，切片数至少增加两篇各自的切片数")
    void indexTwoDifferentDocsAddsBothChunkCounts() {
        int before = retriever.indexedChunkCount();
        retriever.index(Document.of("extra10a", "苹果 香蕉 樱桃", Map.of()));
        retriever.index(Document.of("extra10b", "苹果 橙子 梨", Map.of()));
        assertThat(retriever.indexedChunkCount()).isGreaterThan(before + 1);
    }
}
