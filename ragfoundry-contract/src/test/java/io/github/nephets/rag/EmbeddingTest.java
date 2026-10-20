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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

@DisplayName("Embedding 不可变向量包装")
class EmbeddingTest {

    @Nested
    @DisplayName("构造与基本属性")
    class Basics {
        @Test
        @DisplayName("维度与入参一致")
        void dimensions() {
            assertThat(Embedding.of(new float[] {1, 2, 3}).dimensions()).isEqualTo(3);
        }

        @Test
        @DisplayName("null 入参抛异常")
        void nullRejected() {
            assertThatThrownBy(() -> Embedding.of(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("toArray 返回副本，外部修改不影响内部")
        void toArrayIsCopy() {
            Embedding e = Embedding.of(new float[] {1, 2});
            float[] copy = e.toArray();
            copy[0] = 99f;
            assertThat(e.toArray()).containsExactly(1f, 2f);
        }
    }

    @Nested
    @DisplayName("归一化")
    class Normalize {
        @Test
        @DisplayName("归一化后长度为 1")
        void unit() {
            assertThat(EmbeddingMath.l2Norm(Embedding.of(new float[] {3, 4}).normalize().toArray()))
                    .isCloseTo(1f, within(1e-6f));
        }

        @Test
        @DisplayName("归一化返回新实例")
        void newInstance() {
            Embedding e = Embedding.of(new float[] {3, 4});
            assertThat(e.normalize()).isNotSameAs(e);
        }
    }

    @Nested
    @DisplayName("余弦相似度")
    class Cosine {
        @Test
        @DisplayName("相同向量为 1")
        void identical() {
            assertThat(
                            Embedding.of(new float[] {1, 2, 3})
                                    .cosineSimilarity(Embedding.of(new float[] {1, 2, 3})))
                    .isCloseTo(1f, within(1e-6f));
        }

        @Test
        @DisplayName("正交向量为 0")
        void orthogonal() {
            assertThat(
                            Embedding.of(new float[] {1, 0})
                                    .cosineSimilarity(Embedding.of(new float[] {0, 1})))
                    .isCloseTo(0f, within(1e-6f));
        }

        @Test
        @DisplayName("维度不一致抛 DimensionMismatchException")
        void mismatch() {
            assertThatThrownBy(
                            () ->
                                    Embedding.of(new float[] {1, 2})
                                            .cosineSimilarity(Embedding.of(new float[] {1})))
                    .isInstanceOf(DimensionMismatchException.class);
        }
    }

    @Nested
    @DisplayName("相等与哈希")
    class Equality {
        @Test
        @DisplayName("相同向量相等")
        void equal() {
            assertThat(Embedding.of(new float[] {1, 2}))
                    .isEqualTo(Embedding.of(new float[] {1, 2}));
        }

        @Test
        @DisplayName("不同向量不等")
        void notEqual() {
            assertThat(Embedding.of(new float[] {1, 2}))
                    .isNotEqualTo(Embedding.of(new float[] {1, 3}));
        }

        @Test
        @DisplayName("等向量哈希一致")
        void hash() {
            assertThat(Embedding.of(new float[] {1, 2}).hashCode())
                    .isEqualTo(Embedding.of(new float[] {1, 2}).hashCode());
        }
    }

    @Nested
    @DisplayName("边界情形")
    class Edge {
        @Test
        @DisplayName("与零向量余弦相似度为 0（不抛不 NaN）")
        void withZeroVector() {
            assertThat(
                            Embedding.of(new float[] {1, 2, 3})
                                    .cosineSimilarity(Embedding.of(new float[] {0, 0, 0})))
                    .isCloseTo(0f, within(1e-6f));
            assertThat(
                            Embedding.of(new float[] {0, 0, 0})
                                    .cosineSimilarity(Embedding.of(new float[] {1, 2, 3})))
                    .isCloseTo(0f, within(1e-6f));
        }

        @Test
        @DisplayName("维度不一致异常携带期望/实际维度")
        void mismatchCarriesDimensions() {
            assertThatThrownBy(
                            () ->
                                    Embedding.of(new float[] {1, 2})
                                            .cosineSimilarity(Embedding.of(new float[] {1, 2, 3})))
                    .isInstanceOf(DimensionMismatchException.class)
                    .hasMessageContaining("2")
                    .hasMessageContaining("3");
        }

        @Test
        @DisplayName("归一化零向量仍为等长零向量")
        void normalizeZero() {
            assertThat(Embedding.of(new float[] {0, 0}).normalize().toArray())
                    .containsExactly(0f, 0f);
        }
    }
}
