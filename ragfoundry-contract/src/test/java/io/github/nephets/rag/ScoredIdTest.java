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
import static org.assertj.core.api.Assertions.within;

@DisplayName("ScoredId 向量库单条记录的相似度得分")
class ScoredIdTest {

    @Nested
    @DisplayName("record 语义")
    class RecordSemantics {
        @Test
        @DisplayName("accessor 返回构造时的值")
        void accessors() {
            ScoredId s = new ScoredId("v1", 0.9f);
            assertThat(s.id()).isEqualTo("v1");
            assertThat(s.score()).isCloseTo(0.9f, within(1e-6f));
        }

        @Test
        @DisplayName("equals / hashCode 同时取决于 id 与 score")
        void equalsAndHash() {
            assertThat(new ScoredId("a", 1f)).isEqualTo(new ScoredId("a", 1f));
            assertThat(new ScoredId("a", 1f)).hasSameHashCodeAs(new ScoredId("a", 1f));
            assertThat(new ScoredId("a", 1f)).isNotEqualTo(new ScoredId("b", 1f));
            assertThat(new ScoredId("a", 1f)).isNotEqualTo(new ScoredId("a", 2f));
        }

        @Test
        @DisplayName("toString 同时包含 id 与 score，便于排查")
        void toStringContainsBoth() {
            assertThat(new ScoredId("xyz", 0.5f).toString()).contains("xyz").contains("0.5");
        }
    }

    @Nested
    @DisplayName("compareByScoreDesc 降序比较器")
    class Ordering {
        @Test
        @DisplayName("高分排在前面（低分在前时比较器返回正数）")
        void higherFirst() {
            assertThat(
                            ScoredId.compareByScoreDesc(
                                    new ScoredId("a", 0.2f), new ScoredId("b", 0.9f)))
                    .isGreaterThan(0);
        }

        @Test
        @DisplayName("同分返回 0")
        void equal() {
            assertThat(
                            ScoredId.compareByScoreDesc(
                                    new ScoredId("a", 0.5f), new ScoredId("b", 0.5f)))
                    .isZero();
        }

        @Test
        @DisplayName("低分排在后面（高分在前时比较器返回负数）")
        void lowerLast() {
            assertThat(
                            ScoredId.compareByScoreDesc(
                                    new ScoredId("a", 0.9f), new ScoredId("b", 0.2f)))
                    .isLessThan(0);
        }
    }
}
