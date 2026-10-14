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

@DisplayName("EmbeddingMath 向量基础运算")
class EmbeddingMathTest {

    @Nested
    @DisplayName("点积")
    class DotProduct {
        @Test
        @DisplayName("标准二维点积")
        void twoD() {
            assertThat(EmbeddingMath.dotProduct(new float[] {1, 2}, new float[] {3, 4}))
                    .isEqualTo(11f);
        }

        @Test
        @DisplayName("正交向量点积为 0")
        void orthogonal() {
            assertThat(EmbeddingMath.dotProduct(new float[] {1, 0}, new float[] {0, 1}))
                    .isCloseTo(0f, within(1e-6f));
        }

        @Test
        @DisplayName("维度不一致抛 DimensionMismatchException")
        void mismatch() {
            assertThatThrownBy(() -> EmbeddingMath.dotProduct(new float[] {1, 2}, new float[] {1}))
                    .isInstanceOf(DimensionMismatchException.class)
                    .hasMessageContaining("维度")
                    .hasMessageContaining("2")
                    .hasMessageContaining("1");
        }
    }

    @Nested
    @DisplayName("L2 范数")
    class Norm {
        @Test
        @DisplayName("[3,4] 长度为 5")
        void pythagorean() {
            assertThat(EmbeddingMath.l2Norm(new float[] {3, 4})).isCloseTo(5f, within(1e-6f));
        }

        @Test
        @DisplayName("零向量长度为 0")
        void zero() {
            assertThat(EmbeddingMath.l2Norm(new float[] {0, 0, 0})).isCloseTo(0f, within(1e-6f));
        }
    }

    @Nested
    @DisplayName("余弦相似度")
    class Cosine {
        @Test
        @DisplayName("相同向量余弦为 1")
        void identical() {
            assertThat(EmbeddingMath.cosineSimilarity(new float[] {1, 2, 3}, new float[] {1, 2, 3}))
                    .isCloseTo(1f, within(1e-6f));
        }

        @Test
        @DisplayName("相反向量余弦为 -1")
        void opposite() {
            assertThat(EmbeddingMath.cosineSimilarity(new float[] {1, 0}, new float[] {-1, 0}))
                    .isCloseTo(-1f, within(1e-6f));
        }

        @Test
        @DisplayName("正交向量余弦为 0")
        void orthogonal() {
            assertThat(EmbeddingMath.cosineSimilarity(new float[] {1, 0}, new float[] {0, 1}))
                    .isCloseTo(0f, within(1e-6f));
        }

        @Test
        @DisplayName("任一为零向量时返回 0（不抛不 NaN）")
        void zeroVectorReturnsZero() {
            assertThat(EmbeddingMath.cosineSimilarity(new float[] {0, 0}, new float[] {1, 2}))
                    .isCloseTo(0f, within(1e-6f));
            assertThat(EmbeddingMath.cosineSimilarity(new float[] {1, 2}, new float[] {0, 0}))
                    .isCloseTo(0f, within(1e-6f));
        }

        @Test
        @DisplayName("非归一化向量的余弦仍正确")
        void unnormalized() {
            // [1,1] 与 [1,0] 夹角 45°，cos=√2/2≈0.7071
            assertThat(EmbeddingMath.cosineSimilarity(new float[] {1, 1}, new float[] {1, 0}))
                    .isCloseTo((float) (Math.sqrt(2) / 2), within(1e-5f));
        }
    }

    @Nested
    @DisplayName("归一化")
    class Normalize {
        @Test
        @DisplayName("归一化后长度为 1")
        void unitLength() {
            float[] n = EmbeddingMath.normalize(new float[] {3, 4});
            assertThat(EmbeddingMath.l2Norm(n)).isCloseTo(1f, within(1e-6f));
        }

        @Test
        @DisplayName("零向量归一化后仍是零向量")
        void zeroStaysZero() {
            float[] n = EmbeddingMath.normalize(new float[] {0, 0, 0});
            assertThat(n).containsExactly(0f, 0f, 0f);
        }

        @Test
        @DisplayName("不修改入参（返回新数组）")
        void doesNotMutateInput() {
            float[] in = {3, 4};
            EmbeddingMath.normalize(in);
            assertThat(in).containsExactly(3f, 4f);
        }
    }

    @Nested
    @DisplayName("维度校验")
    class Require {
        @Test
        @DisplayName("null 向量抛 DimensionMismatchException")
        void nullVector() {
            assertThatThrownBy(() -> EmbeddingMath.requireSameDimensions(null, new float[] {1}))
                    .isInstanceOf(DimensionMismatchException.class);
        }

        @Test
        @DisplayName("维度一致不抛")
        void sameOk() {
            EmbeddingMath.requireSameDimensions(new float[] {1, 2}, new float[] {3, 4});
        }
    }
}
