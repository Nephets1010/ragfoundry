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
package io.github.nephets.rag.spring;

import io.github.nephets.rag.ChunkingStrategy;
import io.github.nephets.rag.DeterministicEmbedding;
import io.github.nephets.rag.EmbeddingModel;
import io.github.nephets.rag.FixedSizeChunker;
import io.github.nephets.rag.InMemoryVectorStore;
import io.github.nephets.rag.RecursiveChunker;
import io.github.nephets.rag.Retriever;
import io.github.nephets.rag.SentenceChunker;
import io.github.nephets.rag.VectorStore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * ragfoundry 自动装配。
 *
 * <p>只做一件事：在 classpath 上有核心类、且 {@code ragfoundry.enabled=true} 时，提供 {@link EmbeddingModel} / {@link
 * VectorStore} / {@link ChunkingStrategy} / {@link Retriever} 四个 bean。 全部用
 * {@code @ConditionalOnMissingBean}，因此用户随时可以用自己的真实 embedding / 向量库实现替换。
 */
@AutoConfiguration
@ConditionalOnClass(Retriever.class)
@EnableConfigurationProperties(RagfoundryProperties.class)
@ConditionalOnProperty(prefix = "ragfoundry", name = "enabled", matchIfMissing = true)
public class RagfoundryAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(RagfoundryAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public EmbeddingModel embeddingModel(RagfoundryProperties properties) {
        EmbeddingModel model = new DeterministicEmbedding(properties.getEmbeddingDimensions());
        log.info(
                "装配 EmbeddingModel：{} (dimensions={})",
                model.getClass().getSimpleName(),
                properties.getEmbeddingDimensions());
        return model;
    }

    @Bean
    @ConditionalOnMissingBean
    public VectorStore vectorStore() {
        VectorStore store = new InMemoryVectorStore();
        log.info("装配 VectorStore：{}", store.getClass().getSimpleName());
        return store;
    }

    @Bean
    @ConditionalOnMissingBean
    public ChunkingStrategy chunkingStrategy(RagfoundryProperties properties) {
        RagfoundryProperties.Chunking c = properties.getChunking();
        ChunkingStrategy strategy =
                switch (c.getStrategy().toLowerCase()) {
                    case "fixed" -> new FixedSizeChunker(c.getMaxChars(), c.getOverlap());
                    case "sentence" -> new SentenceChunker(c.getMaxChars());
                    default -> new RecursiveChunker(c.getMaxChars());
                };
        log.info(
                "装配 ChunkingStrategy：{} (strategy={}, maxChars={})",
                strategy.getClass().getSimpleName(),
                c.getStrategy(),
                c.getMaxChars());
        return strategy;
    }

    @Bean
    @ConditionalOnMissingBean
    public Retriever retriever(
            ChunkingStrategy chunkingStrategy,
            EmbeddingModel embeddingModel,
            VectorStore vectorStore) {
        Retriever retriever = new Retriever(chunkingStrategy, embeddingModel, vectorStore);
        log.info("装配 Retriever：{}", retriever.getClass().getSimpleName());
        return retriever;
    }
}
