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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("FixedSizeChunker 定长切片")
class FixedSizeChunkerTest {

    private final FixedSizeChunker chunker = new FixedSizeChunker(5, 0);

    @Nested
    @DisplayName("边界与构造")
    class Construction {
        @Test
        @DisplayName("chunkSize<=0 抛异常")
        void badSize() {
            assertThatThrownBy(() -> new FixedSizeChunker(0, 0))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("overlap<0 抛异常")
        void badOverlap() {
            assertThatThrownBy(() -> new FixedSizeChunker(5, -1))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("overlap>=chunkSize 抛异常（会死循环）")
        void overlapTooLarge() {
            assertThatThrownBy(() -> new FixedSizeChunker(5, 5))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("切片行为")
    class Behaviour {
        @Test
        @DisplayName("恰好整除时分出整数片")
        void exactMultiple() {
            List<TextChunk> chunks = chunker.chunk(Document.of("0123456789"));
            assertThat(chunks).hasSize(2);
            assertThat(chunks.get(0).text()).isEqualTo("01234");
            assertThat(chunks.get(1).text()).isEqualTo("56789");
            assertThat(chunks.get(0).startIndex()).isZero();
            assertThat(chunks.get(1).endIndex()).isEqualTo(10);
        }

        @Test
        @DisplayName("短于上限的文本整段成一片")
        void shorterThanSize() {
            List<TextChunk> chunks = chunker.chunk(Document.of("abc"));
            assertThat(chunks).hasSize(1);
            assertThat(chunks.get(0).text()).isEqualTo("abc");
        }

        @Test
        @DisplayName("恰好等于上限时单片")
        void equalToSize() {
            assertThat(chunker.chunk(Document.of("12345"))).hasSize(1);
        }

        @Test
        @DisplayName("重叠使相邻片内容重叠")
        void overlapProducesOverlap() {
            FixedSizeChunker c = new FixedSizeChunker(5, 2);
            List<TextChunk> chunks = c.chunk(Document.of("0123456789"));
            assertThat(chunks).hasSize(3);
            assertThat(chunks.get(0).text()).isEqualTo("01234");
            assertThat(chunks.get(1).text()).isEqualTo("34567");
            assertThat(chunks.get(2).text()).isEqualTo("6789");
        }

        @Test
        @DisplayName("空文档与纯空白文档返回空")
        void blankDocument() {
            assertThat(chunker.chunk(Document.of(""))).isEmpty();
            assertThat(chunker.chunk(Document.of("   "))).isEmpty();
            assertThat(chunker.chunk(Document.of((String) null))).isEmpty();
        }
    }

    @Nested
    @DisplayName("偏移与元数据")
    class Offsets {
        @Test
        @DisplayName("startIndex/endIndex 与原文对齐")
        void offsetsAligned() {
            List<TextChunk> chunks = chunker.chunk(Document.of("0123456789"));
            assertThat(chunks.get(0).startIndex()).isZero();
            assertThat(chunks.get(0).endIndex()).isEqualTo(5);
            assertThat(chunks.get(1).startIndex()).isEqualTo(5);
        }

        @Test
        @DisplayName("切片顺序下标递增")
        void indexIncreasing() {
            List<TextChunk> chunks = chunker.chunk(Document.of("0123456789"));
            assertThat(chunks.stream().map(TextChunk::index).toList()).containsExactly(0, 1);
        }

        @Test
        @DisplayName("切片 id 形如 doc#index，且携带策略元数据")
        void idsAndMeta() {
            Document doc = Document.of("docX", "0123456789", java.util.Map.of("src", "web"));
            List<TextChunk> chunks = chunker.chunk(doc);
            assertThat(chunks.get(0).id()).isEqualTo("docX#0");
            assertThat(chunks.get(0).metadata())
                    .containsEntry("src", "web")
                    .containsEntry("chunk_strategy", "fixed");
        }
    }

    @Nested
    @DisplayName("扩展边界")
    class Extended {
        @Test
        @DisplayName("overlap=chunkSize-1 仍终止，每段长度<=上限且覆盖全部字符")
        void maxOverlap() {
            FixedSizeChunker c = new FixedSizeChunker(4, 3);
            List<TextChunk> chunks = c.chunk(Document.of("abcdefgh"));
            assertThat(chunks).isNotEmpty();
            assertThat(chunks).allMatch(ch -> ch.text().length() <= 4);
            List<String> texts = chunks.stream().map(TextChunk::text).toList();
            for (char ch : "abcdefgh".toCharArray()) {
                assertThat(texts).anyMatch(t -> t.indexOf(ch) >= 0);
            }
        }

        @Test
        @DisplayName("单字符文本成一片")
        void singleChar() {
            assertThat(chunker.chunk(Document.of("a"))).hasSize(1);
        }

        @Test
        @DisplayName("中文按字符数切片（非字节数）")
        void cjkByChar() {
            FixedSizeChunker c = new FixedSizeChunker(2, 0);
            List<TextChunk> chunks = c.chunk(Document.of("苹果香蕉"));
            assertThat(chunks).hasSize(2);
            assertThat(chunks.get(0).text()).isEqualTo("苹果");
        }

        @Test
        @DisplayName("非整倍数的尾部成更短的一片")
        void trailingTail() {
            List<TextChunk> chunks = chunker.chunk(Document.of("012345678"));
            assertThat(chunks.get(chunks.size() - 1).text()).isEqualTo("5678");
        }

        @Test
        @DisplayName("切片携带 documentId")
        void carriesDocumentId() {
            Document d = Document.of("docY", "0123456789", java.util.Map.of());
            assertThat(chunker.chunk(d))
                    .allSatisfy(ch -> assertThat(ch.documentId()).isEqualTo("docY"));
        }
    }
}
