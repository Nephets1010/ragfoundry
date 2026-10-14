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

@DisplayName("SentenceChunker 按句切片")
class SentenceChunkerTest {

    @Nested
    @DisplayName("英文标点")
    class Latin {
        @Test
        @DisplayName("上限足够大时整段合为一片")
        void joinsAll() {
            List<TextChunk> chunks = new SentenceChunker(100).chunk(Document.of("A. B. C."));
            assertThat(chunks).hasSize(1);
            assertThat(chunks.get(0).text()).isEqualTo("A. B. C.");
        }

        @Test
        @DisplayName("上限很小时每句一片")
        void onePerSentence() {
            List<TextChunk> chunks = new SentenceChunker(4).chunk(Document.of("A. B. C."));
            assertThat(chunks).hasSize(3);
            assertThat(chunks.stream().map(TextChunk::text).toList())
                    .containsExactly("A. ", "B. ", "C.");
        }

        @Test
        @DisplayName("无句末标点的整段成一片")
        void noTerminator() {
            List<TextChunk> chunks = new SentenceChunker(100).chunk(Document.of("hello world"));
            assertThat(chunks).hasSize(1);
            assertThat(chunks.get(0).text()).isEqualTo("hello world");
        }

        @Test
        @DisplayName("单句超上限仍保持完整（保语义，允许略超上限）")
        void longSentenceKeptWhole() {
            List<TextChunk> chunks = new SentenceChunker(5).chunk(Document.of("abcdefghij."));
            assertThat(chunks).hasSize(1);
            assertThat(chunks.get(0).text()).isEqualTo("abcdefghij.");
            assertThat(chunks.get(0).text().length()).isGreaterThan(5);
        }
    }

    @Nested
    @DisplayName("中文标点")
    class Chinese {
        @Test
        @DisplayName("中文句号断句")
        void chinesePeriod() {
            List<TextChunk> chunks = new SentenceChunker(100).chunk(Document.of("你好。世界。你好世界。"));
            assertThat(chunks).hasSize(1);
            assertThat(chunks.get(0).text()).isEqualTo("你好。世界。你好世界。");
        }

        @Test
        @DisplayName("中文短句在上限较小时各成一片")
        void chineseSplit() {
            List<TextChunk> chunks = new SentenceChunker(4).chunk(Document.of("苹果。香蕉。猫。"));
            assertThat(chunks.stream().map(TextChunk::text).toList())
                    .containsExactly("苹果。", "香蕉。", "猫。");
        }
    }

    @Nested
    @DisplayName("边界与元数据")
    class Edge {
        @Test
        @DisplayName("空白文档返回空")
        void blank() {
            assertThat(new SentenceChunker(10).chunk(Document.of(""))).isEmpty();
            assertThat(new SentenceChunker(10).chunk(Document.of("   "))).isEmpty();
        }

        @Test
        @DisplayName("偏移覆盖整段原文")
        void offsetsCoverAll() {
            Document doc = Document.of("A. B. C.");
            List<TextChunk> chunks = new SentenceChunker(100).chunk(doc);
            assertThat(chunks.get(0).startIndex()).isZero();
            assertThat(chunks.get(chunks.size() - 1).endIndex()).isEqualTo(doc.content().length());
        }

        @Test
        @DisplayName("元数据标注策略为 sentence")
        void strategyMeta() {
            List<TextChunk> chunks = new SentenceChunker(100).chunk(Document.of("A. B."));
            assertThat(chunks.get(0).metadata()).containsEntry("chunk_strategy", "sentence");
        }

        @Test
        @DisplayName("chunkSize<=0 构造即抛异常")
        void badMax() {
            assertThatThrownBy(() -> new SentenceChunker(0))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
