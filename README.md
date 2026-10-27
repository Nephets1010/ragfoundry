# ragfoundry

轻量级 RAG（检索增强生成）基础库：把「文档切片 → 向量化 → 入库 → 余弦检索 → 回填切片原文」这条链路做成一个零强依赖、可单测、可替换的纯 Java 组件。

- 运行仅需 **Java 21** 与 **Spring Boot 3.5**（仅 starter/sample 需要 Spring）。
- 核心检索逻辑 **零第三方依赖**，可在任何 JVM 项目里直接使用。
- 提供确定性嵌入（`DeterministicEmbedding`）用于演示与单测；生产请替换为真实 embedding 供应商。

---

## 模块一览

本项目从最初的 3 个 Maven 模块重构为 **5 个模块**，每个模块职责单一、依赖方向清晰（只向下依赖契约，不向上耦合）：

| 模块 | 职责 | 关键产物 | 运行时依赖 |
| --- | --- | --- | --- |
| `ragfoundry-contract` | 公共契约：值对象、接口、异常。**不依赖任何其它模块、零运行时依赖** | `Document`、`TextChunk`、`Embedding`、`ScoredId`、`ScoredChunk`、`ChunkingStrategy`、`EmbeddingModel`、`VectorStore`、`DimensionMismatchException` | 无 |
| `ragfoundry-store` | `VectorStore` 的唯一参考实现：`InMemoryVectorStore`（内存向量索引 + LRU 淘汰） | `InMemoryVectorStore` | contract |
| `ragfoundry-core` | 检索管线与切片算法：`Retriever` 端到端管线、三种切片策略、`ChunkBuilders`、确定性嵌入 | `Retriever`、`FixedSizeChunker`、`RecursiveChunker`、`SentenceChunker`、`DeterministicEmbedding` | contract（+ store 仅 test 作用域） |
| `ragfoundry-spring-boot-starter` | Spring Boot 自动装配：按配置提供四个 bean，全部 `@ConditionalOnMissingBean` | `RagfoundryAutoConfiguration`、`RagfoundryProperties` | contract、core、store |
| `ragfoundry-sample` | 端到端示例应用，验证整条链路可被 Spring 容器自动装配并检索 | `RagfoundrySampleApplication` | 以上全部 |

依赖方向：`starter` / `sample` → `core` / `store` → `contract`。`core` 在**编译期只依赖 `contract`**，仅在测试时才引入 `store`（见设计决策 3）。

---

## 快速开始

```java
// 1) 选择切片策略、嵌入模型、向量库（都可换成你自己的实现）
ChunkingStrategy chunker = new RecursiveChunker(400);
EmbeddingModel model    = new DeterministicEmbedding(32);
VectorStore store       = new InMemoryVectorStore();

// 2) 组装检索管线
Retriever retriever = new Retriever(chunker, model, store);

// 3) 建库
retriever.index(Document.of("fruit", "苹果 香蕉 樱桃 都是水果", Map.of()));
retriever.index(Document.of("space", "火箭 发动机 推力 轨道", Map.of()));

// 4) 检索（按相似度降序返回切片）
List<ScoredChunk> hits = retriever.retrieve("哪些水果含维生素", 3);
hits.forEach(h -> System.out.println(h.chunk().text() + " -> " + h.score()));
```

在 Spring Boot 工程中只需引入 starter，并通过配置切换策略与维度：

```yaml
ragfoundry:
  enabled: true
  embedding-dimensions: 64
  chunking:
    strategy: recursive   # fixed | sentence | recursive
    max-chars: 400
    overlap: 0
```

---

## 设计决策

下面记录本项目演进到 5 模块结构时的关键「为什么」，供后续维护者参考。

### 1. 为什么抽出独立的 `contract` 模块（接口 + 值对象 + 异常）

`Document`、`Embedding`、`ChunkingStrategy`、`EmbeddingModel`、`VectorStore` 等是整条链路的「词汇表」。把它们集中到一个**零运行时依赖**的 `contract` 模块，使 `store` / `core` / `starter` / `sample` 都只依赖这份契约，而不是依赖某个具体实现。
**收益**：① 替换真实向量库（pgvector）或真实 embedding 供应商时，只动实现，不动调用方；② 契约测试（`*ContractTest`）可以用一份参考实现验证「接口约定」，所有实现者都要满足；③ 避免任何模块意外引入另一个模块的内部类，依赖图保持单向。

### 2. 为什么 `InMemoryVectorStore` 单独成 `store` 模块，而不是塞进 `core`

`VectorStore` 是一个接口，`InMemoryVectorStore` 是它的**唯一参考实现**（暴力余弦 + LRU）。把它从 `core` 拆出，是因为「检索管线」与「向量索引实现」是两个不同关注点：`core` 负责怎么切、怎么算、怎么回填；`store` 负责向量怎么存、怎么查。
**收益**：将来要接入 pgvector / Milvus，只需新增一个 `store` 风格的实现模块或在 `store` 内加类，`core` 一行都不用改——这也正是面向接口编程最直接的回报。

### 3. 为什么 `core` 对 `store` 只用 `test` 作用域依赖

`Retriever` 在编译期只 import `contract` 的 `VectorStore` 接口（运行时不耦合具体实现），这本没问题。但 `RetrieverTest` 要做**端到端集成测试**，需要一个真实可用的 `VectorStore`，而 `InMemoryVectorStore` 恰好在 `store` 模块。
**决策**：在 `core` 的 `pom.xml` 里把 `store` 声明为 `test` 作用域（并加注释说明「RetrieverTest 端到端集成测试需要真实的 VectorStore 实现」）。
**收益**：既保住了编译期的单向依赖（core 不向 store 反向耦合，不会出现循环依赖），又让集成测试能直接调用真实实现，不必为测试再写一套假实现。

### 4. 为什么内置 `DeterministicEmbedding` 而不是依赖某个 embedding 库

`DeterministicEmbedding` 把文本字符散布进定长向量再做 L2 归一化，具备「可复现、零依赖、相似文本得分更高」三个性质，但**不是真语义嵌入**。
**决策**：把它作为默认实现放在 `core`，生产环境通过 `@ConditionalOnMissingBean` 或手动装配替换成真实供应商。
**收益**：单测/示例/离线演示不需要任何外部服务或网络，构建与测试完全离线可跑（这也是本仓库能在离线 `mvn -o` 下通过的根本原因）。

### 5. 为什么余弦相似度遇到零向量约定返回 `0` 而非抛异常或 `NaN`

`EmbeddingMath.cosineSimilarity` 与 `Embedding.cosineSimilarity` 都规定：任一向量 L2 范数为 0 时返回 `0f`。
**理由**：检索是高频批量操作，偶发的空文本（空片、空查询）不应让整条查询崩溃或产生 `NaN` 污染排序。返回 `0` 表示「不相似」，语义安全且可预测；同时单测里 `EmbeddingMathEdge2Test` 固化了这一约定。

### 6. 为什么 `ChunkBuilders` 设计为包级私有、被三种切片策略共用

`FixedSizeChunker` / `RecursiveChunker` / `SentenceChunker` 都需要在「复制来源文档元数据 + 附加 `chunk_index` / `chunk_strategy` + 包成不可变 `TextChunk`」上保持一致。
**决策**：把这段逻辑收进包级私有的 `ChunkBuilders.build(...)`，三策略只负责算出 `[start, end)` 区间。
**收益**：元数据约定只有一处定义（`ChunkBuildersTest` 专门守护），避免将来改字段要在三个类里同步改；也顺带保证了「切片 id 形如 `documentId#index`」这类不变量的统一性。

### 7. 为什么提供 `fixed` / `recursive` / `sentence` 三种切片策略

不同结构的文本适合不同切法：定长最朴素、句子保语义、递归优先在段落/换行/句末处断开。
**决策**：`RecursiveChunker` 以分隔符优先级 `\n\n → \n → 。 → 空格 → 字符级硬切` 逐级下钻，结构越清晰的文本切得越自然；`SentenceChunker` 保证一个切片绝不从句子中间切断（单句超上限时该句独占一片，允许略超上限以保语义）。
**收益**：检索质量直接受切片质量影响，多策略让调用方按内容特征自选，而不是被一个「万能但不称手」的切法将就。

### 8. 为什么 starter 的四个 bean 全部用 `@ConditionalOnMissingBean`

`RagfoundryAutoConfiguration` 提供的 `EmbeddingModel` / `VectorStore` / `ChunkingStrategy` / `Retriever` 都带 `@ConditionalOnMissingBean`。
**收益**：用户只要在自己的 `@Configuration` 里声明同名 bean，自动装配就自动退让，零侵入地换成真实实现；同时 starter 不强制绑定任何具体类，依赖图始终保持「用户代码 → 接口 ← 自动装配」。

### 9. 为什么 `InMemoryVectorStore` 用 `synchronizedMap` + `accessOrder` LRU

为支持并发检索与「内存上限」两件事：用 `Collections.synchronizedMap` 包裹 `LinkedHashMap(accessOrder=true)` 保证线程安全，并重写 `removeEldestEntry` 在超过容量时淘汰最久未访问条目。
**收益**：既能在检索/建库并发场景下不抛 `ConcurrentModificationException`，又能防止长期运行内存无限增长（默认容量一百万条，常规场景不触发；测试里用容量为 1/2 的实例精确验证 LRU 语义）。

### 10. 为什么 `Retriever` 只负责「管线与回填」，不持有业务状态

`Retriever.index`/`retrieve` 只做：切片 → 批量向量化 → 写库 →（查询时）向量化 → 查库 → 按序回填原文。它不缓存任何业务语义，向量库与嵌入模型都可外部注入。
**收益**：换实现（或换测试替身）时 `Retriever` 一行不改；单元测试可纯函数式地验证「索引后检索能命中正确来源文档」，无需启动 Spring。

### 11. 为什么主源码统一走 SLF4J，而不是直接写 `System.out`

示例应用原来用 `System.out.printf` 打印检索命中。这类直写 stdout 的输出绕过日志框架：不受级别控制、关不掉，也没有时间戳与线程名，混入日志流后还会打散多行输出的列对齐。
**决策**：主源码里的叙述性输出一律交给 SLF4J；示例应用那处保留原有版式（分数四位小数、与文本之间两个空格）只换输出口，异常也不再用 `printStackTrace()` 兜底——要么抛出，要么记日志。这条由 `ragfoundry-spring-boot-starter` 的 `StdoutHygieneTest` 在构建期强制：它扫描五个模块的 `src/main/java`，`System.out` / `System.err` / `System.console()` / `setOut` / `setErr` / `printStackTrace()` / `FileDescriptor.out|err` 命中即失败，并对扫描到的文件数设下限、防止路径改坏后「扫不到就算通过」。
**收益**：日志可按级别过滤、可被采集、来源可定位；「改过一次」因此变成构建期持续判据，不会被人遗忘后悄悄退回。
**豁免**：「快速开始」片段里的 `System.out.println` 是给读者直接粘贴运行的文档示例，不在门禁范围内——守卫只扫 `src/main/java`，README 不在其中。

---

## 构建与测试

```bash
# 离线、批处理、干净构建并跑全量测试 + 覆盖率门禁
mvn -o -B clean verify
```

- 采用 **JUnit 5 + AssertJ**，包含单元测试、契约测试、跨特性集成测试、边界测试与并发测试。
- 接入 **JaCoCo** 覆盖率门禁：整体 `BUNDLE LINE ≥ 0.50`，`contract` / `store` / `core` / `starter` 各有更高阈值，`sample` 为端到端演示默认跳过。
- 统一 **Spotless**（Google Java Format）格式化，CI 中 `spotless:check` 失败即构建失败。

### 测试分布（示意）

| 模块 | 关注点 |
| --- | --- |
| `ragfoundry-contract` | 值对象不变性、记录语义、`*ContractTest` 契约验证 |
| `ragfoundry-store` | `InMemoryVectorStore` 写入/检索/LRU/并发 |
| `ragfoundry-core` | 三种切片策略、确定性嵌入、Retriever 管线与跨特性集成 |
| `ragfoundry-spring-boot-starter` | 自动装配 bean 注册、配置绑定、`@ConditionalOnMissingBean` 退让 |
| `ragfoundry-sample` | Spring 容器端到端装配与检索 |

---

## 许可证

Apache License 2.0。
