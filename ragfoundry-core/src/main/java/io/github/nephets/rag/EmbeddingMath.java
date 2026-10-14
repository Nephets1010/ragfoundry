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

/** 向量基础运算。所有方法都接受原始 {@code float[]}，零第三方依赖。 */
public final class EmbeddingMath {

    private EmbeddingMath() {}

    /** 点积。两向量维度必须一致，否则抛 {@link DimensionMismatchException}。 */
    public static float dotProduct(float[] a, float[] b) {
        requireSameDimensions(a, b);
        float sum = 0f;
        for (int i = 0; i < a.length; i++) {
            sum += a[i] * b[i];
        }
        return sum;
    }

    /** L2 范数（长度）。空向量返回 0。 */
    public static float l2Norm(float[] a) {
        float sum = 0f;
        for (float v : a) {
            sum += v * v;
        }
        return (float) Math.sqrt(sum);
    }

    /**
     * 余弦相似度，取值 {@code [-1, 1]}。
     *
     * <p>约定：任一向量长度为 0 时返回 {@code 0f}（零向量与任何方向「不相似」），而不是抛异常或产生 NaN —— 检索场景下偶发的空向量不应让整条查询挂掉。
     */
    public static float cosineSimilarity(float[] a, float[] b) {
        requireSameDimensions(a, b);
        float normA = l2Norm(a);
        float normB = l2Norm(b);
        if (normA == 0f || normB == 0f) {
            return 0f;
        }
        return dotProduct(a, b) / (normA * normB);
    }

    /** 原地归一化（返回新数组）。零向量归一化后仍是零向量。 */
    public static float[] normalize(float[] a) {
        float norm = l2Norm(a);
        if (norm == 0f) {
            return a.clone();
        }
        float[] out = new float[a.length];
        for (int i = 0; i < a.length; i++) {
            out[i] = a[i] / norm;
        }
        return out;
    }

    /** 断言两向量维度一致；不一致抛 {@link DimensionMismatchException}（含可读信息）。 */
    public static void requireSameDimensions(float[] a, float[] b) {
        if (a == null || b == null) {
            throw new DimensionMismatchException("向量不能为 null", -1, -1);
        }
        if (a.length != b.length) {
            throw new DimensionMismatchException("向量维度必须一致才能做相似度计算", a.length, b.length);
        }
    }
}
