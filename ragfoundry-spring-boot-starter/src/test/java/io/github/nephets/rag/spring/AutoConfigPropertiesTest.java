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
import io.github.nephets.rag.EmbeddingModel;
import io.github.nephets.rag.FixedSizeChunker;
import io.github.nephets.rag.InMemoryVectorStore;
import io.github.nephets.rag.RecursiveChunker;
import io.github.nephets.rag.Retriever;
import io.github.nephets.rag.SentenceChunker;
import io.github.nephets.rag.VectorStore;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RagfoundryAutoConfiguration 配置绑定与退让边界")
class AutoConfigPropertiesTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(RagfoundryAutoConfiguration.class));

    @Test
    @DisplayName("关闭时连 EmbeddingModel 也不注册（整组 bean 退场）")
    void disabledNoEmbeddingModelBean() {
        runner.withPropertyValues("ragfoundry.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(EmbeddingModel.class));
    }

    @Test
    @DisplayName("关闭时连 VectorStore 也不注册")
    void disabledNoVectorStoreBean() {
        runner.withPropertyValues("ragfoundry.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(VectorStore.class));
    }

    @Test
    @DisplayName("未知策略名回退到 recursive（default 分支）")
    void unknownStrategyDefaultsToRecursive() {
        runner.withPropertyValues("ragfoundry.chunking.strategy=weird-unknown")
                .run(
                        context ->
                                assertThat(context.getBean(ChunkingStrategy.class))
                                        .isInstanceOf(RecursiveChunker.class));
    }

    @Test
    @DisplayName("fixed 策略的 overlap 配置真正生效（step=chunkSize-overlap 影响切片数）")
    void overlapAppliedToFixedStrategy() {
        runner.withPropertyValues(
                        "ragfoundry.chunking.strategy=fixed",
                        "ragfoundry.chunking.overlap=2",
                        "ragfoundry.chunking.max-chars=5")
                .run(
                        context -> {
                            FixedSizeChunker chunker =
                                    (FixedSizeChunker) context.getBean(ChunkingStrategy.class);
                            Document doc = Document.of("x", "0123456789", Map.of());
                            // overlap=2 → step=3 → 0-5, 3-8, 6-10 共 3 片（overlap=0 时只有 2 片）
                            assertThat(chunker.chunk(doc)).hasSize(3);
                        });
    }

    @Test
    @DisplayName("sentence 策略的 max-chars 配置生效（单句不超过上限）")
    void sentenceMaxCharsConfigured() {
        runner.withPropertyValues(
                        "ragfoundry.chunking.strategy=sentence", "ragfoundry.chunking.max-chars=4")
                .run(
                        context -> {
                            SentenceChunker chunker =
                                    (SentenceChunker) context.getBean(ChunkingStrategy.class);
                            Document doc = Document.of("x", "苹果。香蕉。猫。", Map.of());
                            assertThat(chunker.chunk(doc)).allMatch(ch -> ch.text().length() <= 4);
                        });
    }

    @Test
    @DisplayName("用户同时提供 EmbeddingModel 与 VectorStore 时，自动装配仍创建 Retriever 并注入用户 bean")
    void customEmbeddingAndStoreBothProvidedCreatesRetriever() {
        runner.withUserConfiguration(BothUserConfig.class)
                .run(
                        context -> {
                            assertThat(context).hasSingleBean(EmbeddingModel.class);
                            assertThat(context).hasSingleBean(VectorStore.class);
                            assertThat(context).hasSingleBean(Retriever.class);
                            assertThat(context.getBean(EmbeddingModel.class))
                                    .isInstanceOf(CustomEmbedding.class);
                            assertThat(context.getBean(VectorStore.class))
                                    .isInstanceOf(CustomStore.class);
                        });
    }

    @Test
    @DisplayName("embedding-dimensions 默认值等于 32")
    void embeddingDimensionsDefaultIsThirtyTwo() {
        runner.run(
                context ->
                        assertThat(context.getBean(EmbeddingModel.class).dimensions())
                                .isEqualTo(32));
    }

    @Test
    @DisplayName("自动装配的 DeterministicEmbedding 维度与配置一致（端到端建库可检索）")
    void defaultEmbeddingIsDeterministicAndUsable() {
        runner.run(
                context -> {
                    EmbeddingModel model = context.getBean(EmbeddingModel.class);
                    assertThat(model).isInstanceOf(DeterministicEmbedding.class);
                    assertThat(model.embed("苹果"))
                            .isEqualTo(new DeterministicEmbedding(32).embed("苹果"));
                });
    }

    static class BothUserConfig {
        @Bean
        EmbeddingModel myModel() {
            return new CustomEmbedding();
        }

        @Bean
        VectorStore myStore() {
            return new CustomStore();
        }
    }

    static final class CustomEmbedding implements EmbeddingModel {
        @Override
        public io.github.nephets.rag.Embedding embed(String text) {
            return new DeterministicEmbedding(8).embed(text);
        }

        @Override
        public int dimensions() {
            return 8;
        }
    }

    static final class CustomStore implements VectorStore {
        private final InMemoryVectorStore delegate = new InMemoryVectorStore();

        @Override
        public void add(String id, io.github.nephets.rag.Embedding embedding) {
            delegate.add(id, embedding);
        }

        @Override
        public List<io.github.nephets.rag.ScoredId> search(
                io.github.nephets.rag.Embedding query, int topK) {
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
}
