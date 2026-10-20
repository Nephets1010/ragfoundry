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

import java.util.List;

/** 向量索引抽象：写入向量、按相似度检索。具体实现可以是内存、pgvector、Milvus 等。 */
public interface VectorStore {

    /** 写入一条向量，用 id 标识。重复 id 覆盖旧值。 */
    void add(String id, Embedding embedding);

    /** 返回与查询向量最相似的 {@code topK} 条，按得分降序。 */
    List<ScoredId> search(Embedding query, int topK);

    /** 已索引条数。 */
    int size();

    /** 清空。 */
    void clear();
}
