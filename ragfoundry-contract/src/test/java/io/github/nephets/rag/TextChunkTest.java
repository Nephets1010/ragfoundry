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
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("TextChunk 切片模型")
class TextChunkTest {

    @Test
    @DisplayName("基本字段被正确保留")
    void basics() {
        TextChunk c = TextChunk.of("d#0", "d", "你好世界", 0, 4, 0, Map.of("k", "v"));
        assertThat(c.id()).isEqualTo("d#0");
        assertThat(c.documentId()).isEqualTo("d");
        assertThat(c.text()).isEqualTo("你好世界");
        assertThat(c.startIndex()).isZero();
        assertThat(c.endIndex()).isEqualTo(4);
        assertThat(c.index()).isZero();
        assertThat(c.metadata()).containsEntry("k", "v");
    }

    @Test
    @DisplayName("元数据对外不可变")
    void metadataImmutable() {
        TextChunk c = TextChunk.of("d#0", "d", "x", 0, 1, 0, Map.of("k", "v"));
        assertThatThrownBy(() -> c.metadata().put("z", "1"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("空 id 抛异常")
    void blankIdRejected() {
        assertThatThrownBy(() -> TextChunk.of(" ", "d", "x", 0, 1, 0, Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("null 文本抛异常")
    void nullTextRejected() {
        assertThatThrownBy(() -> TextChunk.of("d#0", "d", null, 0, 1, 0, Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("start>end 的非法偏移抛异常")
    void badOffsets() {
        assertThatThrownBy(() -> TextChunk.of("d#0", "d", "x", 5, 1, 0, Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("负偏移抛异常")
    void negativeOffset() {
        assertThatThrownBy(() -> TextChunk.of("d#0", "d", "x", -1, 1, 0, Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("start==end 的零长度切片被允许（文本为空）")
    void zeroLengthAllowed() {
        TextChunk c = TextChunk.of("d#0", "d", "", 0, 0, 0, Map.of());
        assertThat(c.text()).isEmpty();
        assertThat(c.startIndex()).isZero();
        assertThat(c.endIndex()).isZero();
    }

    @Test
    @DisplayName("传入元数据被复制，外部修改不影响切片")
    void metadataCopied() {
        java.util.Map<String, String> src = new java.util.HashMap<>();
        src.put("k", "1");
        TextChunk c = TextChunk.of("d#0", "d", "x", 0, 1, 0, src);
        src.put("k", "2");
        assertThat(c.metadata()).containsEntry("k", "1");
    }
}
