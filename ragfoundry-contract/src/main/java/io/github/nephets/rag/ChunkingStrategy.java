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

import java.util.List;

/** 把文档切成若干 {@link TextChunk} 的策略。实现必须稳定：相同输入总是产出相同切片。 */
public interface ChunkingStrategy {

    /** 切片。空文档或纯空白文档返回空列表（没有可检索的内容）。 */
    List<TextChunk> chunk(Document document);
}
