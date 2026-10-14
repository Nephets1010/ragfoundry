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

import java.util.ArrayList;
import java.util.List;

/** 把文本映射成 {@link Embedding} 的模型抽象。具体实现可以是本地哈希、调用远端 API，或任何向量化器。 */
public interface EmbeddingModel {

    /** 文本 → 嵌入。 */
    Embedding embed(String text);

    /** 嵌入维度（同一模型对所有文本返回相同维度）。 */
    int dimensions();

    /**
     * 批量文本 → 嵌入。默认顺序调用 {@link #embed(String)}，保持与单条接口完全兼容； 实现类可覆写此方法以利用并发（虚拟线程）或缓存来加速建库，调用方无需改动。
     */
    default List<Embedding> embedAll(List<String> texts) {
        List<Embedding> out = new ArrayList<>(texts.size());
        for (String text : texts) {
            out.add(embed(text));
        }
        return out;
    }
}
