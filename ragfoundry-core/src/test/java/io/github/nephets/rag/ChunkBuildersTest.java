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

@DisplayName("ChunkBuilders 切片构造共享逻辑（包级私有，三策略共用）")
class ChunkBuildersTest {

    @Test
    @DisplayName("切片 id 形如 documentId#index")
    void idFormat() {
        Document d = Document.of("doc9", "abcdef", Map.of());
        TextChunk c = ChunkBuilders.build(d, 3, 0, 3, "fixed");
        assertThat(c.id()).isEqualTo("doc9#3");
    }

    @Test
    @DisplayName("documentId 取自源文档")
    void documentId() {
        Document d = Document.of("doc9", "abcdef", Map.of());
        assertThat(ChunkBuilders.build(d, 0, 0, 3, "fixed").documentId()).isEqualTo("doc9");
    }

    @Test
    @DisplayName("text 为原文 [start,end) 子串，偏移被正确记录")
    void textSubstring() {
        Document d = Document.of("d", "abcdef", Map.of());
        TextChunk c = ChunkBuilders.build(d, 0, 1, 4, "recursive");
        assertThat(c.text()).isEqualTo("bcd");
        assertThat(c.startIndex()).isEqualTo(1);
        assertThat(c.endIndex()).isEqualTo(4);
    }

    @Test
    @DisplayName("追加 chunk_index 与 chunk_strategy 元数据，并保留源文档元数据")
    void metadataAugmented() {
        Document d = Document.of("d", "abc", Map.of("src", "web"));
        TextChunk c = ChunkBuilders.build(d, 2, 0, 3, "sentence");
        assertThat(c.metadata())
                .containsEntry("src", "web")
                .containsEntry("chunk_index", "2")
                .containsEntry("chunk_strategy", "sentence");
    }

    @Test
    @DisplayName("源文档元数据被复制，外部修改不影响已构造切片")
    void metadataCopied() {
        Map<String, String> src = new java.util.HashMap<>();
        src.put("k", "1");
        Document d = Document.of("d", "abc", src);
        TextChunk c = ChunkBuilders.build(d, 0, 0, 3, "fixed");
        src.put("k", "9");
        assertThat(c.metadata()).containsEntry("k", "1");
    }

    @Test
    @DisplayName("index 字段取自调用方传入的顺序下标")
    void indexField() {
        Document d = Document.of("d", "abc", Map.of());
        assertThat(ChunkBuilders.build(d, 7, 0, 3, "fixed").index()).isEqualTo(7);
    }
}
