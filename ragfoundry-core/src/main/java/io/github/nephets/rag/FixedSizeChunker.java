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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * 按固定字符数切片，可带重叠（overlap）。
 *
 * <p>最朴素的策略，适合无结构文本。重叠用于在切片边界处被切断的语义提供一点冗余召回。 重叠量必须小于切片大小，否则会陷入死循环（每个窗口的起点都前进 0）。
 */
public final class FixedSizeChunker implements ChunkingStrategy {

    private static final Logger log = LoggerFactory.getLogger(FixedSizeChunker.class);

    private final int chunkSize;
    private final int overlap;

    public FixedSizeChunker(int chunkSize, int overlap) {
        if (chunkSize <= 0) {
            throw new IllegalArgumentException("切片大小必须为正数，实际 " + chunkSize);
        }
        if (overlap < 0) {
            throw new IllegalArgumentException("重叠量不能为负，实际 " + overlap);
        }
        if (overlap >= chunkSize) {
            throw new IllegalArgumentException("重叠量必须小于切片大小（" + chunkSize + "），实际 " + overlap);
        }
        this.chunkSize = chunkSize;
        this.overlap = overlap;
        log.debug("FixedSizeChunker 初始化完成：chunkSize={}, overlap={}", chunkSize, overlap);
    }

    @Override
    public List<TextChunk> chunk(Document document) {
        String text = document.content();
        if (text == null || text.isBlank()) {
            log.debug("chunk 跳过：文档为空或空白，documentId={}", document.id());
            return List.of();
        }
        int step = chunkSize - overlap;
        List<TextChunk> chunks = new ArrayList<>();
        int index = 0;
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + chunkSize, text.length());
            chunks.add(ChunkBuilders.build(document, index, start, end, "fixed"));
            index++;
            if (end == text.length()) {
                break;
            }
            start += step;
        }
        log.info("chunk 完成：documentId={}, 切片数={}", document.id(), chunks.size());
        return chunks;
    }
}
