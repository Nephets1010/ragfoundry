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
package io.github.nephets.rag.sample;

import io.github.nephets.rag.Document;
import io.github.nephets.rag.Retriever;
import io.github.nephets.rag.ScoredChunk;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.List;
import java.util.Map;

/** ragfoundry 最小可运行示例：把几篇中文文档建库并用语义检索找出最相关切片。 */
@SpringBootApplication
public class RagfoundrySampleApplication {

    private static final Logger log = LoggerFactory.getLogger(RagfoundrySampleApplication.class);

    public static void main(String[] args) {
        log.info("ragfoundry 示例启动");
        ConfigurableApplicationContext ctx =
                SpringApplication.run(RagfoundrySampleApplication.class, args);
        demo(ctx.getBean(Retriever.class));
        ctx.close();
        log.info("ragfoundry 示例结束");
    }

    private static void demo(Retriever retriever) {
        retriever.index(Document.of("rag", "检索增强生成把大模型和企业知识库连起来，先检索再生成。", Map.of()));
        retriever.index(Document.of("vec", "向量数据库用余弦相似度做最近邻搜索，pgvector 是 Postgres 的扩展。", Map.of()));
        retriever.index(Document.of("chunk", "长文档要先切片，递归切片按段落、句子逐级切分。", Map.of()));

        List<ScoredChunk> hits = retriever.retrieve("怎么做向量检索", 2);
        log.info("检索命中 {} 个切片", hits.size());
        for (ScoredChunk hit : hits) {
            System.out.printf("%.4f  %s%n", hit.score(), hit.chunk().text());
        }
    }
}
