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

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ChunkingStrategy 接口契约")
class ChunkingStrategyContractTest {

    /**
     * 一个遵守契约的最小实现：按空白切词。仅用于验证接口契约（确定性、空文档返回空、输出是合法 {@link TextChunk}）， 真正的策略实现位于 ragfoundry-core。
     */
    static final class WhitespaceChunker implements ChunkingStrategy {
        @Override
        public List<TextChunk> chunk(Document document) {
            String text = document.content();
            if (text == null || text.isBlank()) {
                return List.of();
            }
            String[] words = text.trim().split("\\s+");
            List<TextChunk> out = new java.util.ArrayList<>();
            int index = 0;
            for (String w : words) {
                int start = text.indexOf(w);
                out.add(
                        TextChunk.of(
                                document.id() + "#" + index,
                                document.id(),
                                w,
                                start,
                                start + w.length(),
                                index,
                                Map.of()));
                index++;
            }
            return out;
        }
    }

    private final ChunkingStrategy strategy = new WhitespaceChunker();

    @Nested
    @DisplayName("契约：空文档")
    class EmptyDoc {
        @Test
        @DisplayName("空内容返回空列表（无内容可检索）")
        void emptyContent() {
            assertThat(strategy.chunk(Document.of("x", "", Map.of()))).isEmpty();
        }

        @Test
        @DisplayName("纯空白内容返回空列表")
        void blankContent() {
            assertThat(strategy.chunk(Document.of("x", "   ", Map.of()))).isEmpty();
        }
    }

    @Nested
    @DisplayName("契约：稳定性与输出合法性")
    class Determinism {
        @Test
        @DisplayName("相同输入总是产出相同切片文本（实现必须稳定）")
        void stableForSameInput() {
            Document d = Document.of("d1", "苹果 香蕉 樱桃", Map.of());
            List<TextChunk> first = strategy.chunk(d);
            List<TextChunk> second = strategy.chunk(d);
            assertThat(first)
                    .extracting(TextChunk::text)
                    .containsExactlyElementsOf(second.stream().map(TextChunk::text).toList());
        }

        @Test
        @DisplayName("每个切片都是合法 TextChunk（id 非空、文本等于原词）")
        void producesValidChunks() {
            List<TextChunk> chunks = strategy.chunk(Document.of("d1", "苹果 香蕉 樱桃", Map.of()));
            assertThat(chunks).hasSize(3);
            assertThat(chunks).allSatisfy(c -> assertThat(c.id()).isNotBlank());
            assertThat(chunks.get(0).text()).isEqualTo("苹果");
            assertThat(chunks.get(1).text()).isEqualTo("香蕉");
            assertThat(chunks.get(2).text()).isEqualTo("樱桃");
        }
    }
}
