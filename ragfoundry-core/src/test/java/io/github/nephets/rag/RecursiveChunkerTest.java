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
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("RecursiveChunker 递归字符切片")
class RecursiveChunkerTest {

    @Nested
    @DisplayName("分隔符优先级")
    class Priority {
        @Test
        @DisplayName("段落（双换行）优先断开")
        void paragraphs() {
            // maxChars=11 确保整段(20 字符)必须切分；分隔符保留在前一片，拼接可还原原文
            List<TextChunk> chunks =
                    new RecursiveChunker(11).chunk(Document.of("Para one.\n\nPara two."));
            assertThat(chunks.stream().map(TextChunk::text).toList())
                    .containsExactly("Para one.\n\n", "Para two.");
        }

        @Test
        @DisplayName("单段落过长时退到单换行")
        void lines() {
            List<TextChunk> chunks =
                    new RecursiveChunker(20).chunk(Document.of("line one text\nline two text"));
            assertThat(chunks).hasSize(2);
            assertThat(chunks.get(0).text()).isEqualTo("line one text\n");
            assertThat(chunks.get(1).text()).isEqualTo("line two text");
        }

        @Test
        @DisplayName("既无换行也无句号的超长文本退化为定长硬切")
        void hardSplitFallback() {
            List<TextChunk> chunks = new RecursiveChunker(4).chunk(Document.of("abcdefghij"));
            assertThat(chunks.stream().map(TextChunk::text).toList())
                    .containsExactly("abcd", "efgh", "ij");
        }

        @Test
        @DisplayName("含空格的长串在空格处断开（分隔符保留在前一片）")
        void spaces() {
            List<TextChunk> chunks = new RecursiveChunker(6).chunk(Document.of("aa bb cc dd"));
            assertThat(chunks.get(0).text()).isEqualTo("aa bb ");
            assertThat(chunks.get(1).text()).isEqualTo("cc dd");
        }
    }

    @Nested
    @DisplayName("不变量")
    class Invariants {
        @Test
        @DisplayName("没有切片超过上限")
        void neverExceedsMax() {
            List<TextChunk> chunks = new RecursiveChunker(5).chunk(Document.of("abcdefghijklmnop"));
            assertThat(chunks).allMatch(c -> c.text().length() <= 5);
        }

        @Test
        @DisplayName("切片拼接后还原原文（连续无遗漏）")
        void contiguous() {
            String text = "Para one.\n\nPara two.\nline a\nline b";
            List<TextChunk> chunks = new RecursiveChunker(8).chunk(Document.of(text));
            String joined = chunks.stream().map(TextChunk::text).collect(Collectors.joining());
            assertThat(joined).isEqualTo(text);
        }

        @Test
        @DisplayName("空白文档返回空")
        void blank() {
            assertThat(new RecursiveChunker(5).chunk(Document.of(""))).isEmpty();
            assertThat(new RecursiveChunker(5).chunk(Document.of("   "))).isEmpty();
        }

        @Test
        @DisplayName("元数据标注策略为 recursive")
        void strategyMeta() {
            List<TextChunk> chunks =
                    new RecursiveChunker(20).chunk(Document.of("Para one.\n\nPara two."));
            assertThat(chunks.get(0).metadata()).containsEntry("chunk_strategy", "recursive");
        }

        @Test
        @DisplayName("首片从 0 开始，末片覆盖到文末")
        void offsets() {
            String text = "abcdefghij";
            List<TextChunk> chunks = new RecursiveChunker(4).chunk(Document.of(text));
            assertThat(chunks.get(0).startIndex()).isZero();
            assertThat(chunks.get(chunks.size() - 1).endIndex()).isEqualTo(text.length());
        }
    }

    @Nested
    @DisplayName("构造")
    class Construction {
        @Test
        @DisplayName("maxChars<=0 抛异常")
        void badMax() {
            assertThatThrownBy(() -> new RecursiveChunker(0))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("扩展边界")
    class Extended {
        @Test
        @DisplayName("maxChars=1 退化为逐字硬切")
        void charLevel() {
            List<TextChunk> chunks = new RecursiveChunker(1).chunk(Document.of("abc"));
            assertThat(chunks.stream().map(TextChunk::text).toList())
                    .containsExactly("a", "b", "c");
        }

        @Test
        @DisplayName("中文句号处断开")
        void chinesePeriod() {
            String text = "你好。世界。你好世界。";
            List<TextChunk> chunks = new RecursiveChunker(4).chunk(Document.of(text));
            assertThat(chunks).isNotEmpty();
            assertThat(chunks).allMatch(c -> c.text().length() <= 4);
            String joined = chunks.stream().map(TextChunk::text).collect(Collectors.joining());
            assertThat(joined).isEqualTo(text);
        }

        @Test
        @DisplayName("切片携带 documentId 且 index 连续")
        void idsAndIndex() {
            Document d = Document.of("d1", "aaaa bbbb cccc dddd", java.util.Map.of());
            List<TextChunk> chunks = new RecursiveChunker(5).chunk(d);
            assertThat(chunks).hasSize(4);
            assertThat(chunks).allSatisfy(ch -> assertThat(ch.documentId()).isEqualTo("d1"));
            assertThat(chunks.stream().map(TextChunk::index).toList()).containsExactly(0, 1, 2, 3);
        }

        @Test
        @DisplayName("所有切片总长度等于原文长度（无空洞）")
        void totalLengthEqualsText() {
            String text = "a bb ccc dddd eeeee";
            List<TextChunk> chunks = new RecursiveChunker(3).chunk(Document.of(text));
            int total = chunks.stream().mapToInt(c -> c.text().length()).sum();
            assertThat(total).isEqualTo(text.length());
        }
    }
}
