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
import static org.assertj.core.api.Assertions.within;

@DisplayName("DeterministicEmbedding 确定性嵌入模型")
class DeterministicEmbeddingTest {

    private final DeterministicEmbedding model = new DeterministicEmbedding(32);

    @Nested
    @DisplayName("基本性质")
    class Basics {
        @Test
        @DisplayName("维度与构造一致")
        void dimensions() {
            assertThat(model.dimensions()).isEqualTo(32);
            assertThat(model.embed("x").dimensions()).isEqualTo(32);
        }

        @Test
        @DisplayName("维度<=0 抛异常")
        void badDimensions() {
            assertThatThrownBy(() -> new DeterministicEmbedding(0))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("非空文本归一化后长度为 1")
        void normalized() {
            assertThat(EmbeddingMath.l2Norm(model.embed("hello world").toArray()))
                    .isCloseTo(1f, within(1e-6f));
        }
    }

    @Nested
    @DisplayName("确定性")
    class Determinism {
        @Test
        @DisplayName("相同文本两次嵌入完全一致")
        void stable() {
            assertThat(model.embed("ragfoundry")).isEqualTo(model.embed("ragfoundry"));
            assertThat(model.embed("ragfoundry").cosineSimilarity(model.embed("ragfoundry")))
                    .isCloseTo(1f, within(1e-6f));
        }

        @Test
        @DisplayName("不同文本嵌入通常不同")
        void differentTextsDiffer() {
            float sim = model.embed("apple").cosineSimilarity(model.embed("banana"));
            assertThat(sim).isLessThan(1f);
        }

        @Test
        @DisplayName("空文本得到零向量")
        void emptyText() {
            Embedding e = model.embed("");
            assertThat(e.toArray()).containsOnly(0f);
            assertThat(e.cosineSimilarity(model.embed("apple"))).isCloseTo(0f, within(1e-6f));
        }

        @Test
        @DisplayName("null 文本得到零向量")
        void nullText() {
            assertThat(model.embed(null).toArray()).containsOnly(0f);
        }
    }

    @Nested
    @DisplayName("相似度合理性")
    class Similarity {
        @Test
        @DisplayName("共享字符越多越相似（'cat' 比 'dog' 更接近 'cart'）")
        void sharedChars() {
            float catToCart = model.embed("cat").cosineSimilarity(model.embed("cart"));
            float dogToCart = model.embed("dog").cosineSimilarity(model.embed("cart"));
            assertThat(catToCart).isGreaterThan(dogToCart);
        }
    }

    @Nested
    @DisplayName("相等性")
    class Equality {
        @Test
        @DisplayName("同维度实例相等且哈希一致")
        void sameDimensionsEqual() {
            assertThat(new DeterministicEmbedding(16)).isEqualTo(new DeterministicEmbedding(16));
            assertThat(new DeterministicEmbedding(16).hashCode())
                    .isEqualTo(new DeterministicEmbedding(16).hashCode());
        }

        @Test
        @DisplayName("不同维度实例不等")
        void differentDimensionsNotEqual() {
            assertThat(new DeterministicEmbedding(16)).isNotEqualTo(new DeterministicEmbedding(32));
        }
    }

    @Nested
    @DisplayName("批量与跨实例")
    class Batch {
        @Test
        @DisplayName("embedAll 返回与文本等量的嵌入")
        void embedAll() {
            List<Embedding> out = model.embedAll(List.of("a", "b", "c"));
            assertThat(out).hasSize(3);
        }

        @Test
        @DisplayName("不同实例对同文本产出相同嵌入（确定性可复现）")
        void crossInstanceDeterministic() {
            assertThat(new DeterministicEmbedding(32).embed("rag"))
                    .isEqualTo(new DeterministicEmbedding(32).embed("rag"));
        }

        @Test
        @DisplayName("含非 ASCII 文本也能产生非零嵌入且与相关文本相似度为正")
        void nonAsciiProducesSignal() {
            Embedding e = model.embed("中文语义向量");
            assertThat(EmbeddingMath.l2Norm(e.toArray())).isCloseTo(1f, within(1e-6f));
            assertThat(e.cosineSimilarity(model.embed("向量"))).isGreaterThan(0f);
        }
    }
}
