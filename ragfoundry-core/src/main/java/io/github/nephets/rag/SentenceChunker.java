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
 * 按「句子」切片：遇到句末标点（{@code .!?。！？!}) 断句，再把若干句子贪心拼进一个不超过 {@code maxChars} 的切片。
 *
 * <p>句子是检索的最小语义单元，因此一个切片绝不会从句子中间切断 —— 单句超过 {@code maxChars} 时，该句独占一个切片
 * （切片可能略大于上限，这是保语义的代价，调用方应据此选合适的上限）。空白「句子」（标点连排）不产生切片。
 */
public final class SentenceChunker implements ChunkingStrategy {

    private static final Logger log = LoggerFactory.getLogger(SentenceChunker.class);

    private static final String TERMINATORS = ".!?。！？!";

    private final int maxChars;

    public SentenceChunker(int maxChars) {
        if (maxChars <= 0) {
            throw new IllegalArgumentException("切片上限必须为正数，实际 " + maxChars);
        }
        this.maxChars = maxChars;
        log.debug("SentenceChunker 初始化完成：maxChars={}", maxChars);
    }

    @Override
    public List<TextChunk> chunk(Document document) {
        String text = document.content();
        if (text == null || text.isBlank()) {
            log.debug("chunk 跳过：文档为空或空白，documentId={}", document.id());
            return List.of();
        }
        List<int[]> sentences = splitSentences(text);
        List<TextChunk> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int currentStart = -1;
        int index = 0;
        for (int[] span : sentences) {
            String piece = text.substring(span[0], span[1]);
            if (current.length() > 0 && current.length() + piece.length() > maxChars) {
                chunks.add(
                        ChunkBuilders.build(
                                document,
                                index++,
                                currentStart,
                                currentStart + current.length(),
                                "sentence"));
                current.setLength(0);
                currentStart = -1;
            }
            if (current.length() == 0) {
                currentStart = span[0];
            }
            current.append(piece);
        }
        if (current.length() > 0) {
            chunks.add(
                    ChunkBuilders.build(
                            document,
                            index,
                            currentStart,
                            currentStart + current.length(),
                            "sentence"));
        }
        log.info("chunk 完成：documentId={}, 切片数={}", document.id(), chunks.size());
        return chunks;
    }

    /** 返回每个句子的 [start, end) 区间（含句末标点）。连续标点之间的空白段不产生区间。 */
    private List<int[]> splitSentences(String text) {
        List<int[]> spans = new ArrayList<>();
        int start = 0;
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (TERMINATORS.indexOf(c) >= 0) {
                int end = i + 1;
                // 跳过紧跟的空白，把空白并入本句，避免下一句以空格开头
                int j = end;
                while (j < text.length() && Character.isWhitespace(text.charAt(j))) {
                    j++;
                }
                if (j > end) {
                    end = j;
                }
                if (end > start) {
                    spans.add(new int[] {start, end});
                }
                start = end;
                i = end;
            } else {
                i++;
            }
        }
        if (start < text.length()) {
            spans.add(new int[] {start, text.length()});
        }
        return spans;
    }
}
