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

import java.util.Arrays;

/** 一条嵌入向量。不可变包装，提供维度查询、归一化与余弦相似度等便捷方法。 */
public final class Embedding {

    private final float[] vector;

    /** 预计算并缓存 L2 范数；检索时 query.cosineSimilarity 会被对每条向量反复调用，复用它避免重复算 sqrt。 */
    private final float norm;

    private Embedding(float[] vector) {
        this.vector = vector;
        this.norm = EmbeddingMath.l2Norm(vector);
    }

    /** 包装原始向量（复制一份，防止外部后续修改破坏不可变性）。 */
    public static Embedding of(float[] vector) {
        if (vector == null) {
            throw new IllegalArgumentException("向量不能为 null");
        }
        return new Embedding(vector.clone());
    }

    /** 维度。 */
    public int dimensions() {
        return vector.length;
    }

    /** 原始向量副本（调用方拿到的是拷贝，改它不影响本对象）。 */
    public float[] toArray() {
        return vector.clone();
    }

    /** 归一化后的新嵌入（零向量归一化后仍是零向量）。 */
    public Embedding normalize() {
        return new Embedding(EmbeddingMath.normalize(vector));
    }

    /** 与本嵌入的余弦相似度，取值 {@code [-1, 1]}。维度不一致抛 {@link DimensionMismatchException}。 */
    public float cosineSimilarity(Embedding other) {
        // 复用缓存的范数，避免检索时为同一条 query 反复计算 L2 范数。
        if (norm == 0f || other.norm == 0f) {
            return 0f;
        }
        return EmbeddingMath.dotProduct(vector, other.vector) / (norm * other.norm);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Embedding other)) {
            return false;
        }
        return Arrays.equals(vector, other.vector);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(vector);
    }

    @Override
    public String toString() {
        return "Embedding{dimensions=" + vector.length + '}';
    }
}
