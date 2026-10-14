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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * 不依赖任何外部服务的确定性嵌入模型：把文本字符散布进定长向量再做 L2 归一化。
 *
 * <p>它不是「真」语义嵌入（不要用它上生产），但具备可复现、零依赖、相似文本得分更高三个性质， 因此非常适合单测、示例与离线演示。生产请替换为真实 embedding 供应商的实现。
 */
public final class DeterministicEmbedding implements EmbeddingModel {

    private static final Logger log = LoggerFactory.getLogger(DeterministicEmbedding.class);

    private final int dimensions;

    public DeterministicEmbedding(int dimensions) {
        if (dimensions <= 0) {
            throw new IllegalArgumentException("维度必须为正数，实际 " + dimensions);
        }
        this.dimensions = dimensions;
        log.debug("DeterministicEmbedding 初始化完成：dimensions={}", dimensions);
    }

    @Override
    public int dimensions() {
        return dimensions;
    }

    @Override
    public Embedding embed(String text) {
        log.debug("embed 开始：文本长度={}", text == null ? 0 : text.length());
        float[] vector = new float[dimensions];
        if (text == null || text.isEmpty()) {
            return Embedding.of(vector); // 零向量
        }
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        // 滑动窗口：每 2 字节算一个桶，相邻字符相互影响，相似文本更难完全错位
        for (int i = 0; i < bytes.length; i++) {
            int window = (bytes[i] & 0xFF) << 8;
            if (i + 1 < bytes.length) {
                window |= (bytes[i + 1] & 0xFF);
            }
            int bucket = Math.floorMod(window * 31 + i, dimensions);
            vector[bucket] += 1.0f;
        }
        return Embedding.of(EmbeddingMath.normalize(vector));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        return o instanceof DeterministicEmbedding that && dimensions == that.dimensions;
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(new int[] {dimensions});
    }
}
