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
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

@DisplayName("EmbeddingMath 数学运算边界与契约")
class EmbeddingMathEdge2Test {

    @Test
    @DisplayName("零向量归一化后仍是零向量（不抛异常、不 NaN）")
    void normalizeZeroVectorStaysZero() {
        float[] out = EmbeddingMath.normalize(new float[] {0f, 0f, 0f});
        assertThat(out).containsOnly(0f);
    }

    @Test
    @DisplayName("维度不一致的点积抛 DimensionMismatchException")
    void dotProductRejectsMismatchedDims() {
        assertThatThrownBy(() -> EmbeddingMath.dotProduct(new float[] {1, 2}, new float[] {1}))
                .isInstanceOf(DimensionMismatchException.class);
    }

    @Test
    @DisplayName("null 向量点积抛 DimensionMismatchException")
    void dotProductRejectsNull() {
        assertThatThrownBy(() -> EmbeddingMath.dotProduct(null, new float[] {1}))
                .isInstanceOf(DimensionMismatchException.class);
    }

    @Test
    @DisplayName("空向量 L2 范数为 0")
    void l2NormOfEmptyIsZero() {
        assertThat(EmbeddingMath.l2Norm(new float[0])).isCloseTo(0f, within(1e-6f));
    }

    @Test
    @DisplayName("正交向量余弦相似度为 0")
    void cosineOrthogonalIsZero() {
        assertThat(EmbeddingMath.cosineSimilarity(new float[] {1, 0}, new float[] {0, 1}))
                .isCloseTo(0f, within(1e-6f));
    }

    @Test
    @DisplayName("同向向量余弦相似度为 1")
    void cosineParallelIsOne() {
        assertThat(EmbeddingMath.cosineSimilarity(new float[] {1, 0}, new float[] {1, 0}))
                .isCloseTo(1f, within(1e-6f));
    }

    @Test
    @DisplayName("反向向量余弦相似度为 -1")
    void cosineOppositeIsNegativeOne() {
        assertThat(EmbeddingMath.cosineSimilarity(new float[] {1, 0}, new float[] {-1, 0}))
                .isCloseTo(-1f, within(1e-6f));
    }

    @Test
    @DisplayName("任一向量为零向量时余弦相似度约定返回 0（不抛、不 NaN）")
    void cosineWithZeroVectorReturnsZero() {
        assertThat(EmbeddingMath.cosineSimilarity(new float[] {0, 0}, new float[] {1, 1}))
                .isCloseTo(0f, within(1e-6f));
    }
}
