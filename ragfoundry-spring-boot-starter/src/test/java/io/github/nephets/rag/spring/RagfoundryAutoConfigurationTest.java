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
import io.github.nephets.rag.RecursiveChunker;
import io.github.nephets.rag.Retriever;
import io.github.nephets.rag.SentenceChunker;
import io.github.nephets.rag.VectorStore;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;

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
