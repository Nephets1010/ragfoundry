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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 端到端检索管线：建库时把文档切片、向量化、写入向量库；查询时向量化问题、在库里做余弦检索、回填切片原文。
 *
 * <p>本类只负责「管线和回填」，不持有任何业务状态之外的东西；向量库与嵌入模型都可以从外部注入， 因此换成 pgvector / 真实 embedding 供应商时，本类一行都不用改。
 */
public final class Retriever {

    private static final Logger log = LoggerFactory.getLogger(Retriever.class);

    private final ChunkingStrategy chunkingStrategy;
    private final EmbeddingModel embeddingModel;
    private final VectorStore vectorStore;
    private final Map<String, TextChunk> chunkRegistry = new ConcurrentHashMap<>();

    public Retriever(
            ChunkingStrategy chunkingStrategy,
            EmbeddingModel embeddingModel,
            VectorStore vectorStore) {
        if (chunkingStrategy == null || embeddingModel == null || vectorStore == null) {
            throw new IllegalArgumentException(
                    "chunkingStrategy / embeddingModel / vectorStore 均不能为 null");
        }
        this.chunkingStrategy = chunkingStrategy;
        this.embeddingModel = embeddingModel;
        this.vectorStore = vectorStore;
        log.debug(
                "Retriever 初始化完成：chunkingStrategy={}, embeddingModel={}, vectorStore={}",
                chunkingStrategy.getClass().getSimpleName(),
                embeddingModel.getClass().getSimpleName(),
                vectorStore.getClass().getSimpleName());
    }

    /** 把一篇文档切片并入库（幂等：重复索引同一文档会刷新其切片）。 */
    public void index(Document document) {
        List<TextChunk> chunks = chunkingStrategy.chunk(document);
        // 批量向量化：默认顺序，实现类可覆写 embedAll 以并发/缓存加速，调用方无感。
        List<Embedding> embeddings =
                embeddingModel.embedAll(chunks.stream().map(TextChunk::text).toList());
        for (int i = 0; i < chunks.size(); i++) {
            vectorStore.add(chunks.get(i).id(), embeddings.get(i));
            chunkRegistry.put(chunks.get(i).id(), chunks.get(i));
        }
        log.info(
                "index 完成：documentId={}, 切片数={}, 已索引切片总数={}",
                document.id(),
                chunks.size(),
                chunkRegistry.size());
    }

    /** 检索：返回与问题最相似的 {@code topK} 个切片（按相似度降序）。 */
    public List<ScoredChunk> retrieve(String query, int topK) {
        if (query == null || query.isBlank()) {
            log.debug("retrieve 跳过：查询为空或空白");
            return List.of();
        }
        if (topK <= 0) {
            throw new IllegalArgumentException("topK 必须为正数，实际 " + topK);
        }
        log.info("retrieve 开始：query='{}', topK={}", query, topK);
        Embedding queryEmbedding = embeddingModel.embed(query);
        List<ScoredChunk> result = new ArrayList<>();
        // vectorStore.search 已按相似度降序返回，这里只需按序回填原文，无需再排序。
        for (ScoredId scored : vectorStore.search(queryEmbedding, topK)) {
            TextChunk chunk = chunkRegistry.get(scored.id());
            if (chunk != null) {
                result.add(new ScoredChunk(chunk, scored.score()));
            }
        }
        log.debug("retrieve 结束：命中 {} 个切片", result.size());
        return result;
    }

    /** 已索引切片条数（含不同文档）。 */
    public int indexedChunkCount() {
        return chunkRegistry.size();
    }
}
