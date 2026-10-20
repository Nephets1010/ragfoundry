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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** 文档切片：原文的一段子串 + 在原文档中的偏移量 + 顺序下标 + 元数据。不可变。 */
public final class TextChunk {

    private final String id;
    private final String documentId;
    private final String text;
    private final int startIndex;
    private final int endIndex;
    private final int index;
    private final Map<String, String> metadata;

    private TextChunk(
            String id,
            String documentId,
            String text,
            int startIndex,
            int endIndex,
            int index,
            Map<String, String> metadata) {
        this.id = id;
        this.documentId = documentId;
        this.text = text;
        this.startIndex = startIndex;
        this.endIndex = endIndex;
        this.index = index;
        this.metadata = metadata;
    }

    public static TextChunk of(
            String id,
            String documentId,
            String text,
            int startIndex,
            int endIndex,
            int index,
            Map<String, String> metadata) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("切片 id 不能为空");
        }
        if (text == null) {
            throw new IllegalArgumentException("切片文本不能为 null");
        }
        if (startIndex < 0 || endIndex < startIndex) {
            throw new IllegalArgumentException("切片偏移量非法：start=" + startIndex + ", end=" + endIndex);
        }
        Map<String, String> copy = new LinkedHashMap<>(metadata);
        return new TextChunk(
                id,
                documentId,
                text,
                startIndex,
                endIndex,
                index,
                Collections.unmodifiableMap(copy));
    }

    public String id() {
        return id;
    }

    public String documentId() {
        return documentId;
    }

    public String text() {
        return text;
    }

    public int startIndex() {
        return startIndex;
    }

    public int endIndex() {
        return endIndex;
    }

    public int index() {
        return index;
    }

    public Map<String, String> metadata() {
        return metadata;
    }
}
