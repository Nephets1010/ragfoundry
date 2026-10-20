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

@DisplayName("Document 文档模型")
class DocumentTest {

    @Test
    @DisplayName("无参构造自动生成非空 id")
    void autoId() {
        Document d = Document.of("hello");
        assertThat(d.id()).isNotBlank();
        assertThat(d.content()).isEqualTo("hello");
        assertThat(d.metadata()).isEmpty();
    }

    @Test
    @DisplayName("不同文档 id 不同")
    void uniqueIds() {
        assertThat(Document.of("a").id()).isNotEqualTo(Document.of("a").id());
    }

    @Test
    @DisplayName("显式 id 与元数据被保留")
    void explicit() {
        Document d = Document.of("doc-1", "内容", Map.of("lang", "zh"));
        assertThat(d.id()).isEqualTo("doc-1");
        assertThat(d.metadata()).containsEntry("lang", "zh").hasSize(1);
    }

    @Test
    @DisplayName("元数据不可被外部修改（返回不可变视图）")
    void metadataImmutable() {
        Document d = Document.of("doc-1", "内容", Map.of("lang", "zh"));
        assertThatThrownBy(() -> d.metadata().put("x", "y"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("空 id 抛异常")
    void blankIdRejected() {
        assertThatThrownBy(() -> Document.of("", "内容", Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("null 内容抛异常")
    void nullContentRejected() {
        assertThatThrownBy(() -> Document.of("id", null, Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("传入的元数据被复制，后续修改源 map 不影响文档")
    void metadataCopied() {
        java.util.Map<String, String> src = new java.util.HashMap<>();
        src.put("a", "1");
        Document d = Document.of("d", "c", src);
        src.put("a", "changed");
        assertThat(d.metadata()).containsEntry("a", "1");
    }

    @Test
    @DisplayName("of(content) 生成的 id 非空且元数据初始为空")
    void autoIdNonBlank() {
        Document d = Document.of("内容");
        assertThat(d.id()).isNotBlank();
        assertThat(d.metadata()).isEmpty();
    }
}
