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
package io.github.nephets.rag.spring;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** ragfoundry 的配置项。 */
@ConfigurationProperties(prefix = "ragfoundry")
public class RagfoundryProperties {

    /** 总开关，默认开启。 */
    private boolean enabled = true;

    /** 确定性嵌入维度（仅 {@code DeterministicEmbedding} 生效）。 */
    private int embeddingDimensions = 32;

    /** 切片相关配置。 */
    private final Chunking chunking = new Chunking();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getEmbeddingDimensions() {
        return embeddingDimensions;
    }

    public void setEmbeddingDimensions(int embeddingDimensions) {
        this.embeddingDimensions = embeddingDimensions;
    }

    public Chunking getChunking() {
        return chunking;
    }

    /** 切片策略配置。 */
    public static class Chunking {
        /** fixed / sentence / recursive，默认 recursive。 */
        private String strategy = "recursive";

        /** 切片上限字符数（recursive / sentence 用）。 */
        private int maxChars = 400;

        /** 定长切片的重叠量。 */
        private int overlap = 0;

        public String getStrategy() {
            return strategy;
        }

        public void setStrategy(String strategy) {
            this.strategy = strategy;
        }

        public int getMaxChars() {
            return maxChars;
        }

        public void setMaxChars(int maxChars) {
            this.maxChars = maxChars;
        }

        public int getOverlap() {
            return overlap;
        }

        public void setOverlap(int overlap) {
            this.overlap = overlap;
        }
    }
}
