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

/** 向量库中一条记录的相似度得分结果。 */
public record ScoredId(String id, float score) {

    /** 按得分从高到低排序的比较器（检索结果天然需要降序）。 */
    public static int compareByScoreDesc(ScoredId a, ScoredId b) {
        return Float.compare(b.score, a.score);
    }
}
