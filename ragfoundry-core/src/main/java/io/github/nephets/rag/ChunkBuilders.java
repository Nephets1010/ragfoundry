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

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 切片构造的共享逻辑：三个切片策略（fixed / sentence / recursive）都需要在「复制来源文档元数据 + 附加 chunk_index / chunk_strategy +
 * 包成不可变 {@link TextChunk}」这件事上保持一致。
 *
 * <p>集中到这里，避免每个策略各自重写一遍、将来改元数据约定时要改三处。包级私有，不进入公开 API。
 */
final class ChunkBuilders {

    private ChunkBuilders() {}

    /**
     * 构建单个切片。
     *
     * @param document 来源文档，提供 id、原文与基础元数据
     * @param index 切片在文档内的顺序下标
     * @param start 切片在原文中的起始偏移（含）
     * @param end 切片在原文中的结束偏移（不含）
     * @param strategy 切片策略名（fixed / sentence / recursive），写入 chunk_strategy 元数据
     */
    static TextChunk build(Document document, int index, int start, int end, String strategy) {
        Map<String, String> meta = new LinkedHashMap<>(document.metadata());
        meta.put("chunk_index", String.valueOf(index));
        meta.put("chunk_strategy", strategy);
        return TextChunk.of(
                document.id() + "#" + index,
                document.id(),
                document.content().substring(start, end),
                start,
                end,
                index,
                meta);
    }
}
