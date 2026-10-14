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
import java.util.UUID;

/** 被检索的文档：一段原文 + 任意元数据。不可变。 */
public final class Document {

    private final String id;
    private final String content;
    private final Map<String, String> metadata;

    private Document(String id, String content, Map<String, String> metadata) {
        this.id = id;
        this.content = content;
        this.metadata = metadata;
    }

    /** 用随机 id 构造（适合大多数场景，调用方无需关心 id 生成）。 */
    public static Document of(String content) {
        return new Document(UUID.randomUUID().toString(), content, Map.of());
    }

    /** 显式指定 id 与元数据。 */
    public static Document of(String id, String content, Map<String, String> metadata) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("文档 id 不能为空");
        }
        if (content == null) {
            throw new IllegalArgumentException("文档内容不能为 null");
        }
        Map<String, String> copy = new LinkedHashMap<>(metadata);
        return new Document(id, content, Collections.unmodifiableMap(copy));
    }

    public String id() {
        return id;
    }

    public String content() {
        return content;
    }

    /** 不可变元数据视图。 */
    public Map<String, String> metadata() {
        return metadata;
    }
}
