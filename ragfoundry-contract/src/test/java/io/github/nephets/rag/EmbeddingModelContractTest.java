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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("EmbeddingModel 接口契约（默认方法 embedAll）")
class EmbeddingModelContractTest {

    /** 一个最小但行为确定的实现，仅用于验证接口的默认方法契约，不依赖任何具体模型。 */
    static class CountingModel implements EmbeddingModel {
        final AtomicInteger embedCalls = new AtomicInteger();
        final int dims;

        CountingModel(int dims) {
            this.dims = dims;
        }

        @Override
        public Embedding embed(String text) {
            embedCalls.incrementAndGet();
            float[] v = new float[dims];
            if (text != null) {
                v[0] = text.length();
            }
            return Embedding.of(v);
        }

        @Override
        public int dimensions() {
            return dims;
        }
    }

    @Nested
    @DisplayName("embedAll 默认顺序实现")
    class EmbedAllDefault {
        @Test
        @DisplayName("返回数量与输入一致，且按输入顺序一一对应")
        void preservesOrderAndSize() {
            CountingModel model = new CountingModel(4);
            List<String> texts = List.of("alpha", "beta", "gamma");
            List<Embedding> out = model.embedAll(texts);
            assertThat(out).hasSize(3);
            assertThat(out.get(0).dimensions()).isEqualTo(4);
            assertThat(out.get(0).toArray()[0]).isEqualTo(5f); // "alpha".length()
            assertThat(out.get(2).toArray()[0]).isEqualTo(5f); // "gamma".length()
        }

        @Test
        @DisplayName("逐条调用 embed（计数等于输入条数），与单条接口语义完全一致")
        void delegatesToEmbed() {
            CountingModel model = new CountingModel(2);
            model.embedAll(List.of("a", "b", "c", "d"));
            assertThat(model.embedCalls.get()).isEqualTo(4);
        }

        @Test
        @DisplayName("空列表返回空列表，不触发任何 embed 调用")
        void emptyInput() {
            CountingModel model = new CountingModel(2);
            assertThat(model.embedAll(List.of())).isEmpty();
            assertThat(model.embedCalls.get()).isZero();
        }

        @Test
        @DisplayName("null 元素被视为空文本（embed 必须容忍 null）")
        void toleratesNullElement() {
            CountingModel model = new CountingModel(2);
            List<Embedding> out = model.embedAll(java.util.Arrays.asList(null, "x"));
            assertThat(out).hasSize(2);
            assertThat(out.get(0).toArray()[0]).isZero(); // null -> 零向量分量
        }
    }

    @Nested
    @DisplayName("embedAll 可被覆写（并发/缓存加速）")
    class EmbedAllOverride {
        @Test
        @DisplayName("覆写后的实现只要保持「数量与顺序」契约，调用方无感")
        void overrideKeepsContract() {
            // 一个把全部文本合成单条批量嵌入、再拆回的（显然低效但合法）覆写，验证契约健壮性
            EmbeddingModel batched =
                    new CountingModel(3) {
                        @Override
                        public List<Embedding> embedAll(List<String> texts) {
                            List<Embedding> r = new ArrayList<>(texts.size());
                            for (String t : texts) {
                                r.add(embed(t));
                            }
                            return r;
                        }
                    };
            List<Embedding> out = batched.embedAll(List.of("一", "二", "三"));
            assertThat(out).hasSize(3);
            assertThat(out.get(0).toArray()[0]).isEqualTo(1f);
            assertThat(out.get(2).toArray()[0]).isEqualTo(1f);
        }
    }

    @Nested
    @DisplayName("接口恒定属性")
    class Invariants {
        @Test
        @DisplayName("dimensions 在模型生命周期内恒定")
        void dimensionsConstant() {
            EmbeddingModel model = new CountingModel(16);
            assertThat(model.dimensions()).isEqualTo(16);
            model.embed("任意文本");
            assertThat(model.dimensions()).isEqualTo(16);
        }
    }
}
