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

@DisplayName("值对象构造边界与不变量")
class ValueObjectEdgeTest {

    @Test
    @DisplayName("Document.of(content) 自动生成非空 id")
    void autoIdNonBlank() {
        Document d = Document.of("随便一段内容");
        assertThat(d.id()).isNotBlank();
        assertThat(d.content()).isEqualTo("随便一段内容");
    }

    @Test
    @DisplayName("Document.of 拒绝 null id")
    void documentRejectsNullId() {
        assertThatThrownBy(() -> Document.of(null, "内容", Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Document.of 拒绝空 id")
    void documentRejectsBlankId() {
        assertThatThrownBy(() -> Document.of("  ", "内容", Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Document.of 拒绝 null 内容")
    void documentRejectsNullContent() {
        assertThatThrownBy(() -> Document.of("d1", null, Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("TextChunk.of 拒绝 null id")
    void chunkRejectsNullId() {
        assertThatThrownBy(() -> TextChunk.of(null, "d", "文本", 0, 2, 0, Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("TextChunk.of 拒绝 null 文本")
    void chunkRejectsNullText() {
        assertThatThrownBy(() -> TextChunk.of("d#0", "d", null, 0, 2, 0, Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("TextChunk.of 拒绝非法偏移（end < start）")
    void chunkRejectsBadOffsets() {
        assertThatThrownBy(() -> TextChunk.of("d#0", "d", "文本", 3, 1, 0, Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Embedding.of 拒绝 null 向量")
    void embeddingRejectsNullVector() {
        assertThatThrownBy(() -> Embedding.of(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Embedding 维度与输入向量长度一致")
    void embeddingDimensionsMatch() {
        assertThat(Embedding.of(new float[16]).dimensions()).isEqualTo(16);
    }

    @Test
    @DisplayName("ScoredId 记录访问器与 toString 包含 id 与 score")
    void scoredIdAccessors() {
        ScoredId s = new ScoredId("v1", 0.42f);
        assertThat(s.id()).isEqualTo("v1");
        assertThat(s.score()).isCloseTo(0.42f, org.assertj.core.api.Assertions.within(1e-6f));
        assertThat(s.toString()).contains("v1").contains("0.42");
    }

    @Test
    @DisplayName("ScoredChunk 记录访问器返回切片与得分")
    void scoredChunkAccessors() {
        TextChunk chunk = TextChunk.of("d#0", "d", "苹果", 0, 2, 0, Map.of());
        ScoredChunk s = new ScoredChunk(chunk, 0.3f);
        assertThat(s.chunk()).isEqualTo(chunk);
        assertThat(s.score()).isCloseTo(0.3f, org.assertj.core.api.Assertions.within(1e-6f));
    }
}
