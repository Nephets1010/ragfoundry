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
import io.github.nephets.rag.Document;
import io.github.nephets.rag.Embedding;
import io.github.nephets.rag.EmbeddingModel;
import io.github.nephets.rag.FixedSizeChunker;
import io.github.nephets.rag.InMemoryVectorStore;
import io.github.nephets.rag.RecursiveChunker;
import io.github.nephets.rag.Retriever;
import io.github.nephets.rag.ScoredChunk;
import io.github.nephets.rag.ScoredId;
import io.github.nephets.rag.SentenceChunker;
import io.github.nephets.rag.VectorStore;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RagfoundryAutoConfiguration 自动装配")
class RagfoundryAutoConfigurationTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(RagfoundryAutoConfiguration.class));

    @Test
    @DisplayName("默认开启时四个核心 bean 均注册")
    void beansRegisteredByDefault() {
        runner.run(
                context -> {
                    assertThat(context).hasSingleBean(EmbeddingModel.class);
                    assertThat(context).hasSingleBean(VectorStore.class);
                    assertThat(context).hasSingleBean(ChunkingStrategy.class);
                    assertThat(context).hasSingleBean(Retriever.class);
                });
    }

    @Test
    @DisplayName("ragfoundry.enabled=false 时不注册 Retriever")
    void disabledByProperty() {
        runner.withPropertyValues("ragfoundry.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(Retriever.class));
    }

    @Test
    @DisplayName("embedding-dimensions 配置生效")
    void embeddingDimensionsConfigured() {
        runner.withPropertyValues("ragfoundry.embedding-dimensions=64")
                .run(
                        context -> {
                            EmbeddingModel model = context.getBean(EmbeddingModel.class);
                            assertThat(model).isInstanceOf(DeterministicEmbedding.class);
                            assertThat(model.dimensions()).isEqualTo(64);
                        });
    }

    @Test
    @DisplayName("chunking.strategy=fixed 选出 FixedSizeChunker")
    void fixedStrategy() {
        runner.withPropertyValues(
                        "ragfoundry.chunking.strategy=fixed", "ragfoundry.chunking.overlap=10")
                .run(
                        context -> {
                            ChunkingStrategy s = context.getBean(ChunkingStrategy.class);
                            assertThat(s).isInstanceOf(FixedSizeChunker.class);
                        });
    }

    @Test
    @DisplayName("chunking.strategy=sentence 选出 SentenceChunker")
    void sentenceStrategy() {
        runner.withPropertyValues("ragfoundry.chunking.strategy=sentence")
                .run(
                        context ->
                                assertThat(context.getBean(ChunkingStrategy.class))
                                        .isInstanceOf(SentenceChunker.class));
    }

    @Test
    @DisplayName("chunking.strategy=recursive（默认）选出 RecursiveChunker")
    void recursiveStrategyDefault() {
        runner.run(
                context ->
                        assertThat(context.getBean(ChunkingStrategy.class))
                                .isInstanceOf(RecursiveChunker.class));
    }

    @Test
    @DisplayName("用户已提供 Retriever 时不再创建（@ConditionalOnMissingBean）")
    void respectsUserBean() {
        runner.withUserConfiguration(UserConfig.class)
                .run(
                        context ->
                                assertThat(context)
                                        .hasSingleBean(Retriever.class)
                                        .hasBean("myRetriever"));
    }

    @Test
    @DisplayName("ragfoundry.enabled=true 显式开启时仍注册四个核心 bean")
    void enabledExplicitly() {
        runner.withPropertyValues("ragfoundry.enabled=true")
                .run(
                        context -> {
                            assertThat(context).hasSingleBean(EmbeddingModel.class);
                            assertThat(context).hasSingleBean(Retriever.class);
                        });
    }

    @Test
    @DisplayName("用户自定义 EmbeddingModel 时自动装配退让（@ConditionalOnMissingBean）")
    void customEmbeddingModelWins() {
        runner.withUserConfiguration(CustomEmbeddingConfig.class)
                .run(
                        context -> {
                            assertThat(context).hasSingleBean(EmbeddingModel.class);
                            assertThat(context.getBean(EmbeddingModel.class))
                                    .isInstanceOf(CustomEmbedding.class);
                        });
    }

    @Test
    @DisplayName("用户自定义 VectorStore 时自动装配退让")
    void customVectorStoreWins() {
        runner.withUserConfiguration(CustomStoreConfig.class)
                .run(
                        context -> {
                            assertThat(context).hasSingleBean(VectorStore.class);
                            assertThat(context.getBean(VectorStore.class))
                                    .isInstanceOf(CustomStore.class);
                        });
    }

    @Test
    @DisplayName("用户自定义 ChunkingStrategy 时自动装配退让")
    void customChunkingWins() {
        runner.withUserConfiguration(CustomChunkingConfig.class)
                .run(
                        context -> {
                            assertThat(context).hasSingleBean(ChunkingStrategy.class);
                            assertThat(context.getBean(ChunkingStrategy.class))
                                    .isInstanceOf(SentenceChunker.class);
                        });
    }

    @Test
    @DisplayName("chunking.max-chars 配置生效（recursive 策略采用）")
    void maxCharsConfigured() {
        runner.withPropertyValues(
                        "ragfoundry.chunking.strategy=recursive",
                        "ragfoundry.chunking.max-chars=123")
                .run(
                        context -> {
                            RecursiveChunker c =
                                    (RecursiveChunker) context.getBean(ChunkingStrategy.class);
                            Document doc = Document.of("a", "x".repeat(500), java.util.Map.of());
                            assertThat(c.chunk(doc)).allMatch(ch -> ch.text().length() <= 123);
                        });
    }

    @Test
    @DisplayName("自动装配的 Retriever 可直接建库并检索（端到端）")
    void endToEndRetrieval() {
        runner.run(
                context -> {
                    Retriever retriever = context.getBean(Retriever.class);
                    retriever.index(Document.of("fruit", "苹果 香蕉 樱桃 水果", java.util.Map.of()));
                    retriever.index(Document.of("space", "火箭 发动机 轨道", java.util.Map.of()));
                    List<ScoredChunk> hits = retriever.retrieve("苹果", 1);
                    assertThat(hits).isNotEmpty();
                    assertThat(hits.get(0).chunk().documentId()).isEqualTo("fruit");
                });
    }

    static class CustomEmbeddingConfig {
        @Bean
        EmbeddingModel myModel() {
            return new CustomEmbedding();
        }
    }

    static final class CustomEmbedding implements EmbeddingModel {
        @Override
        public Embedding embed(String text) {
            return new DeterministicEmbedding(8).embed(text);
        }

        @Override
        public int dimensions() {
            return 8;
        }
    }

    static class CustomStoreConfig {
        @Bean
        VectorStore myStore() {
            return new CustomStore();
        }
    }

    static final class CustomStore implements VectorStore {
        private final InMemoryVectorStore delegate = new InMemoryVectorStore();

        @Override
        public void add(String id, Embedding embedding) {
            delegate.add(id, embedding);
        }

        @Override
        public List<ScoredId> search(Embedding query, int topK) {
            return delegate.search(query, topK);
        }

        @Override
        public int size() {
            return delegate.size();
        }

        @Override
        public void clear() {
            delegate.clear();
        }
    }

    static class CustomChunkingConfig {
        @Bean
        ChunkingStrategy myChunker() {
            return new SentenceChunker(100);
        }
    }

    static class UserConfig {
        @Bean
        Retriever myRetriever() {
            return new Retriever(
                    new RecursiveChunker(100),
                    new DeterministicEmbedding(16),
                    new io.github.nephets.rag.InMemoryVectorStore());
        }
    }
}
