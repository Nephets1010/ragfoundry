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

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@DisplayName("ScoredChunk 检索结果：切片 + 相似度得分")
class ScoredChunkTest {

    private TextChunk chunk() {
        return TextChunk.of("d1#0", "d1", "苹果", 0, 2, 0, Map.of());
    }

    @Nested
    @DisplayName("record 语义")
    class RecordSemantics {
        @Test
        @DisplayName("accessor 返回构造时的值")
        void accessors() {
            TextChunk c = chunk();
            ScoredChunk s = new ScoredChunk(c, 0.42f);
            assertThat(s.chunk()).isSameAs(c);
            assertThat(s.score()).isCloseTo(0.42f, within(1e-6f));
        }

        @Test
        @DisplayName("equals / hashCode 同时取决于 chunk 与 score")
        void equalsAndHash() {
            TextChunk c = chunk();
            assertThat(new ScoredChunk(c, 1f)).isEqualTo(new ScoredChunk(c, 1f));
            assertThat(new ScoredChunk(c, 1f)).hasSameHashCodeAs(new ScoredChunk(c, 1f));
            assertThat(new ScoredChunk(c, 1f)).isNotEqualTo(new ScoredChunk(c, 2f));
        }

        @Test
        @DisplayName("toString 含切片与 score，便于排查")
        void toStringContainsBoth() {
            assertThat(new ScoredChunk(chunk(), 0.3f).toString())
                    .contains("ScoredChunk")
                    .contains("chunk")
                    .contains("0.3");
        }
    }

    @Nested
    @DisplayName("compareByScoreDesc 降序比较器")
    class Ordering {
        @Test
        @DisplayName("高分排在前面（低分在前时比较器返回正数）")
        void higherFirst() {
            assertThat(
                            ScoredChunk.compareByScoreDesc(
                                    new ScoredChunk(chunk(), 0.2f), new ScoredChunk(chunk(), 0.9f)))
                    .isGreaterThan(0);
        }

        @Test
        @DisplayName("同分返回 0")
        void equal() {
            assertThat(
                            ScoredChunk.compareByScoreDesc(
                                    new ScoredChunk(chunk(), 0.5f), new ScoredChunk(chunk(), 0.5f)))
                    .isZero();
        }

        @Test
        @DisplayName("低分排在后面（高分在前时比较器返回负数）")
        void lowerLast() {
            assertThat(
                            ScoredChunk.compareByScoreDesc(
                                    new ScoredChunk(chunk(), 0.9f), new ScoredChunk(chunk(), 0.2f)))
                    .isLessThan(0);
        }
    }
}
