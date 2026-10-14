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
 * 递归字符切片：按「分隔符优先级」逐级切分，优先在段落/换行/句末处断开，都断不开才退化为定长硬切。
 *
 * <p>分隔符优先级（从高到低）：{@code \n\n} → {@code \n} → 中文句号 {@code 。} → 空格 → 字符级硬切。
 * 这样结构越清晰的文本切得越自然；只有长段落才会落到更小的分隔符上，避免把一句话从中间劈开。
 */
public final class RecursiveChunker implements ChunkingStrategy {

    private static final Logger log = LoggerFactory.getLogger(RecursiveChunker.class);

    private static final String[] SEPARATORS = {"\n\n", "\n", "。", " ", ""};

    private final int maxChars;

    public RecursiveChunker(int maxChars) {
        if (maxChars <= 0) {
            throw new IllegalArgumentException("切片上限必须为正数，实际 " + maxChars);
        }
        this.maxChars = maxChars;
        log.debug("RecursiveChunker 初始化完成：maxChars={}", maxChars);
    }

    @Override
    public List<TextChunk> chunk(Document document) {
        String text = document.content();
        if (text == null || text.isBlank()) {
            log.debug("chunk 跳过：文档为空或空白，documentId={}", document.id());
            return List.of();
        }
        List<int[]> spans = recurse(text, List.of(new int[] {0, text.length()}), 0);
        List<TextChunk> chunks = new ArrayList<>();
        int index = 0;
        for (int[] span : spans) {
            chunks.add(ChunkBuilders.build(document, index++, span[0], span[1], "recursive"));
        }
        log.info("chunk 完成：documentId={}, 切片数={}", document.id(), chunks.size());
        return chunks;
    }

    private List<int[]> recurse(String text, List<int[]> spans, int sepIndex) {
        if (sepIndex >= SEPARATORS.length) {
            List<int[]> out = new ArrayList<>();
            for (int[] span : spans) {
                out.addAll(hardSplit(text, span));
            }
            return out;
        }
        String sep = SEPARATORS[sepIndex];
        List<int[]> out = new ArrayList<>();
        for (int[] span : spans) {
            int length = span[1] - span[0];
            if (length <= maxChars) {
                out.add(span);
                continue;
            }
            List<int[]> merged = merge(text, splitBySep(text, span, sep));
            for (int[] m : merged) {
                if (m[1] - m[0] <= maxChars) {
                    out.add(m);
                } else {
                    out.addAll(recurse(text, List.of(m), sepIndex + 1));
                }
            }
        }
        return out;
    }

    private List<int[]> splitBySep(String text, int[] span, String sep) {
        if (sep.isEmpty()) {
            return List.of(span);
        }
        List<int[]> sub = new ArrayList<>();
        int pos = span[0];
        while (pos < span[1]) {
            int idx = text.indexOf(sep, pos);
            if (idx < 0 || idx + sep.length() > span[1]) {
                sub.add(new int[] {pos, span[1]});
                break;
            }
            int nextStart = idx + sep.length();
            if (nextStart > span[1]) {
                nextStart = span[1];
            }
            sub.add(new int[] {pos, nextStart});
            pos = nextStart;
        }
        return sub.isEmpty() ? List.of(span) : sub;
    }

    private List<int[]> merge(String text, List<int[]> sub) {
        List<int[]> merged = new ArrayList<>();
        if (sub.isEmpty()) {
            return merged;
        }
        int curStart = sub.get(0)[0];
        int curEnd = sub.get(0)[1];
        int curLen = curEnd - curStart;
        for (int k = 1; k < sub.size(); k++) {
            int len = sub.get(k)[1] - sub.get(k)[0];
            if (curLen + len <= maxChars) {
                curEnd = sub.get(k)[1];
                curLen += len;
            } else {
                merged.add(new int[] {curStart, curEnd});
                curStart = sub.get(k)[0];
                curEnd = sub.get(k)[1];
                curLen = curEnd - curStart;
            }
        }
        merged.add(new int[] {curStart, curEnd});
        return merged;
    }

    private List<int[]> hardSplit(String text, int[] span) {
        List<int[]> out = new ArrayList<>();
        for (int s = span[0]; s < span[1]; s += maxChars) {
            out.add(new int[] {s, Math.min(s + maxChars, span[1])});
        }
        return out;
    }
}
