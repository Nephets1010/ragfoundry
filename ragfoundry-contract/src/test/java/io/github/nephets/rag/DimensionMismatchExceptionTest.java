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

@DisplayName("DimensionMismatchException")
class DimensionMismatchExceptionTest {

    @Test
    @DisplayName("是 IllegalArgumentException 的子类，便于按非法参数统一处理")
    void isIllegalArgument() {
        assertThat(new DimensionMismatchException("x", 3, 4))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("消息含期望与实际维度")
    void messageCarriesDimensions() {
        DimensionMismatchException e = new DimensionMismatchException("不一致", 3, 4);
        assertThat(e)
                .hasMessageContaining("3")
                .hasMessageContaining("4")
                .hasMessageContaining("不一致");
        assertThat(e.expected()).isEqualTo(3);
        assertThat(e.actual()).isEqualTo(4);
    }

    @Test
    @DisplayName("嵌入余弦计算在维度不一致时抛出它")
    void thrownByCosine() {
        assertThatThrownBy(
                        () ->
                                Embedding.of(new float[] {1, 2, 3})
                                        .cosineSimilarity(Embedding.of(new float[] {1})))
                .isInstanceOf(DimensionMismatchException.class)
                .hasMessageContaining("维度");
    }

    @Test
    @DisplayName("expected / actual 可被读取")
    void accessors() {
        DimensionMismatchException e = new DimensionMismatchException("m", 7, 8);
        assertThat(e.expected()).isEqualTo(7);
        assertThat(e.actual()).isEqualTo(8);
    }

    @Test
    @DisplayName("EmbeddingMath 在 null 向量时抛出并标记 -1/-1")
    void nullVectorFromMath() {
        assertThatThrownBy(() -> EmbeddingMath.requireSameDimensions(null, new float[] {1}))
                .isInstanceOf(DimensionMismatchException.class)
                .hasMessageContaining("-1");
    }
}
