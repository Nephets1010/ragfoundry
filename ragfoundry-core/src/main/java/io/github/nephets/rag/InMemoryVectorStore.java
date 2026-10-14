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
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * 内存向量索引：暴力计算余弦相似度。零依赖、线程安全（用 {@link Collections#synchronizedMap} 包裹的 {@link LinkedHashMap}
 * 存向量，{@code accessOrder=true} 实现按访问排序）。
 *
 * <p>为避免内存无限增长，索引设有容量上限：当条目数超过容量时，自动淘汰「最近最少使用（LRU）」的条目。 默认容量为 {@value
 * #DEFAULT_CAPACITY}（足够大，常规场景不会触发淘汰），也可通过带参构造器自定义。
 */
public final class InMemoryVectorStore implements VectorStore {

    private static final Logger log = LoggerFactory.getLogger(InMemoryVectorStore.class);

    /** 默认容量：一百万条向量，常规场景不会触发 LRU 淘汰。 */
    private static final long DEFAULT_CAPACITY = 1_000_000L;

    private final long capacity;
    private final Map<String, Embedding> vectors;

    /** 创建默认容量的内存向量索引（{@value #DEFAULT_CAPACITY} 条）。 */
    public InMemoryVectorStore() {
        this(DEFAULT_CAPACITY);
    }

    /**
     * 创建指定容量上限的内存向量索引。
     *
     * @param capacity 容量上限（必须为正数），超出后按 LRU 淘汰最久未访问的条目
     * @throws IllegalArgumentException 当 capacity 不为正数时
     */
    public InMemoryVectorStore(long capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("容量必须为正数，实际 " + capacity);
        }
        this.capacity = capacity;
        LinkedHashMap<String, Embedding> backing =
                new LinkedHashMap<>(16, 0.75f, true) {
                    @Override
                    protected boolean removeEldestEntry(Map.Entry<String, Embedding> eldest) {
                        return size() > capacity;
                    }
                };
        this.vectors = Collections.synchronizedMap(backing);
        log.debug("InMemoryVectorStore 初始化完成：capacity={}", capacity);
    }

    @Override
    public void add(String id, Embedding embedding) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("向量 id 不能为空");
        }
        if (embedding == null) {
            throw new IllegalArgumentException("向量不能为 null");
        }
        vectors.put(id, embedding);
        log.debug("add 完成：id={}, 索引条目数={}", id, vectors.size());
    }

    @Override
    public List<ScoredId> search(Embedding query, int topK) {
        if (topK <= 0) {
            throw new IllegalArgumentException("topK 必须为正数，实际 " + topK);
        }
        log.info("search 开始：topK={}, 索引条目数={}", topK, vectors.size());
        // 有界最小堆：始终保留当前最大的 topK 个，复杂度 O(n·log K)，
        // 避免对全量 n 条做 O(n log n) 排序并为每条都分配 ScoredId 后又丢弃。
        PriorityQueue<ScoredId> heap = new PriorityQueue<>(Comparator.comparing(ScoredId::score));
        // 迭代需在 synchronizedMap 的监视器上同步，避免并发修改异常。
        synchronized (vectors) {
            if (vectors.isEmpty()) {
                return List.of();
            }
            for (Map.Entry<String, Embedding> entry : vectors.entrySet()) {
                heap.offer(new ScoredId(entry.getKey(), query.cosineSimilarity(entry.getValue())));
                if (heap.size() > topK) {
                    heap.poll();
                }
            }
        }
        List<ScoredId> result = new ArrayList<>(heap);
        result.sort(Comparator.comparing(ScoredId::score).reversed());
        log.debug("search 结束：返回 {} 个结果", result.size());
        return result;
    }

    @Override
    public int size() {
        return vectors.size();
    }

    @Override
    public void clear() {
        vectors.clear();
        log.debug("clear 完成：索引已清空");
    }
}
