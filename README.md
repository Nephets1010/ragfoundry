# ragfoundry

![CI](https://github.com/Nephets1010/ragfoundry/actions/workflows/ci.yml/badge.svg?branch=main)
![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)
![Java](https://img.shields.io/badge/Java-21-007396?logo=openjdk)

把「文档切片 → 向量化 → 入库 → 余弦检索 → 回填切片原文」这条 RAG 检索链路收成一组可替换的纯 Java 类：核心链路只依赖 `slf4j-api`，Spring 只有 starter 与 sample 需要。

坐标 `io.github.nephets`，当前版本 `0.1.0-SNAPSHOT`；运行条件：Java 21 / Spring Boot 3.5.16。

## 真实输出样例

以下输出由 `ragfoundry-core` / `ragfoundry-store` 实际打印，不需要数据库、不需要 API Key。

```text
[1] rag#0 [0,22) {chunk_index=0, chunk_strategy=recursive} 检索增强生成把大模型和企业知识库连起来。\n\n
[1] rag#1 [22,42) {chunk_index=1, chunk_strategy=recursive} 先检索，再生成，答案要能溯源到切片。\n\n
[1] rag#2 [42,53) {chunk_index=2, chunk_strategy=recursive} 切片策略决定召回质量。
[2] dimensions=64 前 8 维=[0.0, 0.26726124, 0.0, 0.26726124, 0.0, 0.0, 0.0, 0.26726124]
[3] 已索引切片数=3，向量库条目数=3
[3] 0.4643  rag  检索增强生成把大模型和企业知识库连起来，先检索再生成。
[3] 0.3687  vec  向量数据库用余弦相似度做最近邻搜索，pgvector 是 Postgres 的扩展。
[4] fixed     [第一段讲检索。第, 二段讲向量库。第, 三段讲切片。]
[4] sentence  [第一段讲检索。, 第二段讲向量库。, 第三段讲切片。]
[4] recursive [第一段讲检索。, 第二段讲向量库。, 第三段讲切片。]
[5] 零向量与自身余弦=0.0
[5] 零向量与查询向量余弦=0.0
```

诚实提示：`DeterministicEmbedding` 是字符 bigram 的哈希投影再 L2 归一化，不是语义模型。上面第 `[3]` 组的排序只说明管线跑通，不代表相关性判断正确；换成真实 embedding 之后排序会和这里不一样。

## 能力边界

能做：

- `FixedSizeChunker` / `SentenceChunker` / `RecursiveChunker` 按定长、句末标点、递归分隔符三种方式切片，产出形如 `文档id#序号` 的 id 与 `chunk_index` / `chunk_strategy` 元数据。
- `Retriever` 把切片、向量化、入库、检索、回填原文串成一条管线，重复索引同一篇文档是幂等的。
- `InMemoryVectorStore` 做暴力余弦检索，用有界最小堆取前 K 条，超出容量按 LRU 淘汰最久未访问的条目。
- `EmbeddingMath` 提供点积、L2 范数、余弦与归一化，并定下零向量余弦为 0 的约定。
- `RagfoundryAutoConfiguration` 在 Spring Boot 里按 `ragfoundry.*` 配置装配四个 bean，全部可被用户自己的 bean 顶替。

不能做：

- 不生成语义 embedding：`DeterministicEmbedding` 是哈希投影，不是模型，真实项目要换成自己的 `EmbeddingModel` 实现。
- 不接真实向量数据库：`VectorStore` 只有 `InMemoryVectorStore` 一个实现，数据在进程内存里，进程退出即失。
- 不做 BM25 / RRF 融合 / 重排序：这三个词在全仓源码里 grep 零命中，只在根 `pom.xml` 的 `<description>` 里出现过。
- 不调用任何 LLM：五个模块都没有 HTTP 客户端依赖，也没有 API Key 之类的配置。
- 不做持久化与跨进程一致性保证：内存索引不写盘、没有事务，也不保证多线程写入后检索结果的时序。
- 不做业务输入校验：只保证最基本的参数合法性——`topK <= 0`、空 id、null 向量、向量维度不符会抛异常，空查询直接返回空列表而不是报错；文本长度上限、文档 id 字符集、切片是否为空这些留给调用方。
- 不校验 `ragfoundry.chunking.strategy` 的取值合法性：未知值静默回退到 `RecursiveChunker`，不报错也不告警。
- 不在零向量上抛异常：`DimensionMismatchException` 只在维度不符时抛，零向量余弦按约定返回 `0f`，不会是 `NaN`。

完整限制见[「不做什么」](#不做什么)。

## 5 分钟快速开始

### 1. 引入依赖

Spring Boot 项目引 starter：

```xml
<dependency>
  <groupId>io.github.nephets</groupId>
  <artifactId>ragfoundry-spring-boot-starter</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

只用核心链路、不引 Spring 时需要三个坐标：`ragfoundry-contract`（值对象与接口）+ `ragfoundry-core`（`Retriever` 与切片器）+ `ragfoundry-store`（`VectorStore` 实现）。前两个不够跑通链路，因为 `Retriever` 需要一个真正的 `VectorStore` 实例，而它在 store 里：

```xml
<dependency>
  <groupId>io.github.nephets</groupId>
  <artifactId>ragfoundry-contract</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
<dependency>
  <groupId>io.github.nephets</groupId>
  <artifactId>ragfoundry-core</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
<dependency>
  <groupId>io.github.nephets</groupId>
  <artifactId>ragfoundry-store</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

`0.1.0-SNAPSHOT` 是开发版本，尚未发布到公共仓库。想直接跑起来看输出，按下面三步从源码编译并运行。

### 2. 拉取代码并编译核心模块

这一步不需要数据库，也不需要 Docker：

```bash
git clone https://github.com/Nephets1010/ragfoundry.git
cd ragfoundry
./mvnw -B -ntp -DskipTests compile
```

### 3. 保存单文件示例

把下面代码保存为仓库根目录下的 `QuickStart.java`：

```java
import io.github.nephets.rag.ChunkingStrategy;
import io.github.nephets.rag.DeterministicEmbedding;
import io.github.nephets.rag.Document;
import io.github.nephets.rag.Embedding;
import io.github.nephets.rag.EmbeddingModel;
import io.github.nephets.rag.FixedSizeChunker;
import io.github.nephets.rag.InMemoryVectorStore;
import io.github.nephets.rag.RecursiveChunker;
import io.github.nephets.rag.Retriever;
import io.github.nephets.rag.ScoredChunk;
import io.github.nephets.rag.SentenceChunker;
import io.github.nephets.rag.TextChunk;
import io.github.nephets.rag.VectorStore;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class QuickStart {

    public static void main(String[] args) {
        // 1) 切片：递归策略按 \n\n -> \n -> 。 -> 空格 -> 硬切 逐级下钻，切片 id 形如 文档id#序号
        String article = "检索增强生成把大模型和企业知识库连起来。\n\n先检索，再生成，答案要能溯源到切片。\n\n切片策略决定召回质量。";
        for (TextChunk c : new RecursiveChunker(30).chunk(Document.of("rag", article, Map.of()))) {
            System.out.println(
                    "[1] "
                            + c.id()
                            + " ["
                            + c.startIndex()
                            + ","
                            + c.endIndex()
                            + ") "
                            + c.metadata()
                            + " "
                            + c.text().replace("\n", "\\n"));
        }

        // 2) 嵌入：确定性哈希向量，同样输入永远同样输出（不是语义模型，见「不做什么」）
        EmbeddingModel model = new DeterministicEmbedding(64);
        Embedding e = model.embed("向量检索");
        System.out.println(
                "[2] dimensions="
                        + e.dimensions()
                        + " 前 8 维="
                        + Arrays.toString(Arrays.copyOf(e.toArray(), 8)));

        // 3) 建库 + 检索：切片 -> 向量化 -> 入库 -> 检索 -> 按序回填切片原文
        VectorStore store = new InMemoryVectorStore();
        Retriever retriever = new Retriever(new RecursiveChunker(400), model, store);
        retriever.index(Document.of("rag", "检索增强生成把大模型和企业知识库连起来，先检索再生成。", Map.of()));
        retriever.index(Document.of("vec", "向量数据库用余弦相似度做最近邻搜索，pgvector 是 Postgres 的扩展。", Map.of()));
        retriever.index(Document.of("chunk", "长文档要先切片，递归切片按段落、句子逐级切分。", Map.of()));
        System.out.println("[3] 已索引切片数=" + retriever.indexedChunkCount() + "，向量库条目数=" + store.size());
        for (ScoredChunk hit : retriever.retrieve("怎么做向量检索", 2)) {
            System.out.println(
                    "[3] " + String.format("%.4f", hit.score()) + "  " + hit.chunk().documentId() + "  " + hit.chunk().text());
        }

        // 4) 同一段文本在三种切法下的边界
        String text = "第一段讲检索。第二段讲向量库。第三段讲切片。";
        System.out.println("[4] fixed     " + texts(new FixedSizeChunker(8, 0), text));
        System.out.println("[4] sentence  " + texts(new SentenceChunker(8), text));
        System.out.println("[4] recursive " + texts(new RecursiveChunker(8), text));

        // 5) 边界：空文本产出零向量，零向量与任何向量的余弦约定为 0（不抛异常、不产生 NaN）
        Embedding zero = model.embed("");
        System.out.println("[5] 零向量与自身余弦=" + zero.cosineSimilarity(zero));
        System.out.println("[5] 零向量与查询向量余弦=" + zero.cosineSimilarity(model.embed("怎么做向量检索")));
    }

    private static List<String> texts(ChunkingStrategy strategy, String text) {
        return strategy.chunk(Document.of("t", text, Map.of())).stream().map(TextChunk::text).toList();
    }
}
```

第 2 步显式用了 64 维，starter 的默认值是 32 维；两者都不影响链路，真实项目请换成自己 embedding 模型的输出维度。

### 4. 运行示例

Linux / macOS：

```bash
java -Dstdout.encoding=UTF-8 -cp "ragfoundry-contract/target/classes:ragfoundry-store/target/classes:ragfoundry-core/target/classes:$HOME/.m2/repository/org/slf4j/slf4j-api/2.0.18/slf4j-api-2.0.18.jar" QuickStart.java
```

Windows 命令提示符：

```bat
java -Dstdout.encoding=UTF-8 -cp "ragfoundry-contract\target\classes;ragfoundry-store\target\classes;ragfoundry-core\target\classes;%USERPROFILE%\.m2\repository\org\slf4j\slf4j-api\2.0.18\slf4j-api-2.0.18.jar" QuickStart.java
```

classpath 需要三个模块的 `target/classes`（contract 放值对象与接口、store 放 `InMemoryVectorStore`、core 放 `Retriever` 与切片器），再加一个 `slf4j-api`。`slf4j-api` 的版本由 Spring Boot 3.5.16 的依赖管理决定；如果本地不是 2.0.18，请把路径换成 `~/.m2` 或 `%USERPROFILE%\.m2` 中的实际版本。`-Dstdout.encoding=UTF-8` 在 Windows 上是必需的一项：控制台默认是 GBK，不加的话 stdout 不是合法 UTF-8，中文在 UTF-8 终端或重定向后是乱码。示例不接日志实现，因此 stderr 会显示三行 SLF4J NOP 提示，这是正常现象；下面只列 stdout。

完整输出：

```text
[1] rag#0 [0,22) {chunk_index=0, chunk_strategy=recursive} 检索增强生成把大模型和企业知识库连起来。\n\n
[1] rag#1 [22,42) {chunk_index=1, chunk_strategy=recursive} 先检索，再生成，答案要能溯源到切片。\n\n
[1] rag#2 [42,53) {chunk_index=2, chunk_strategy=recursive} 切片策略决定召回质量。
[2] dimensions=64 前 8 维=[0.0, 0.26726124, 0.0, 0.26726124, 0.0, 0.0, 0.0, 0.26726124]
[3] 已索引切片数=3，向量库条目数=3
[3] 0.4643  rag  检索增强生成把大模型和企业知识库连起来，先检索再生成。
[3] 0.3687  vec  向量数据库用余弦相似度做最近邻搜索，pgvector 是 Postgres 的扩展。
[4] fixed     [第一段讲检索。第, 二段讲向量库。第, 三段讲切片。]
[4] sentence  [第一段讲检索。, 第二段讲向量库。, 第三段讲切片。]
[4] recursive [第一段讲检索。, 第二段讲向量库。, 第三段讲切片。]
[5] 零向量与自身余弦=0.0
[5] 零向量与查询向量余弦=0.0
```

## 模块表

根 POM 的 `<modules>` 只有五个，没有别的：

| 模块目录 | artifactId | 职责 | 依赖谁 | 边界与约束 |
| --- | --- | --- | --- | --- |
| `ragfoundry-contract` | `ragfoundry-contract` | 值对象、接口与异常：`Document` / `TextChunk` / `Embedding` / `EmbeddingMath` / `ScoredId` / `ScoredChunk` / `ChunkingStrategy` / `EmbeddingModel` / `VectorStore` / `DimensionMismatchException` | 真零运行时依赖；`junit-jupiter` 与 `assertj-core` 均为 test scope | 只放词汇表，不放实现。改这里的接口等于改所有模块，因此只增不改 |
| `ragfoundry-store` | `ragfoundry-store` | `VectorStore` 的唯一实现 `InMemoryVectorStore`：暴力余弦 + 有界最小堆 + LRU 容量上限 | `contract`、`slf4j-api`（compile） | 只管向量怎么存怎么查。进程内内存，重启即失；默认容量一百万条 |
| `ragfoundry-core` | `ragfoundry-core` | 切片与检索管线：`Retriever`、`FixedSizeChunker`、`RecursiveChunker`、`SentenceChunker`、`DeterministicEmbedding`（`ChunkBuilders` 是包级私有，不对外） | `contract`、`slf4j-api`（compile）、`store`（test） | `src/main` 不许出现 `org.springframework`，由 CI 的 `architecture-boundaries` job grep 校验；不做 IO、不管连接 |
| `ragfoundry-spring-boot-starter` | `ragfoundry-spring-boot-starter` | `RagfoundryAutoConfiguration`（自动装配）与 `RagfoundryProperties`（`ragfoundry.*` 配置） | `contract`、`core`、`store`、`slf4j-api`、`spring-boot-autoconfigure`、`spring-boot-configuration-processor`（optional） | 只做装配：四个 bean 全部 `@ConditionalOnMissingBean`，不建库、不预热、不在启动时读任何外部资源 |
| `ragfoundry-sample` | `ragfoundry-sample` | 可运行 demo：`RagfoundrySampleApplication` 演示整条链路被 Spring 容器装配并检索 | `contract`、`core`、`starter`、`spring-boot-starter`、`slf4j-api`（store 经 starter 传递，不直接声明） | 只做演示，不发布为公共 API。覆盖率门禁刻意关闭（见下） |

依赖方向：`sample` → `starter` → `core` → `contract`，`store` → `contract`。`core` 在编译期只依赖 `contract` 与 `slf4j-api`，`store` 只在测试期进 `core`（见设计决策 3）。

## 它解决什么问题

搭一条能用的 RAG 检索链路，要自己写的东西比看上去多：长文档要切成能召回的片段、每个片段要变成向量、向量要有地方存、查询时要按相似度取回前 K 条、取回的 id 还要换回原文才能交给下游。
这几段代码分开都不难，但各有各的坑：切片从句子中间切断会让召回的片段读不通；切片 id 与原文的对应关系散落在调用方手里，取回 id 之后常常找不到原文；空文本产出的零向量会让余弦算出 `NaN`，一个 `NaN` 就污染整个排序——本仓用「零向量余弦返回 0」的约定先把这一种挡掉。
本仓把它们收进几个类：`FixedSizeChunker` / `SentenceChunker` / `RecursiveChunker` 负责切片并统一生成 `文档id#序号` 形态的 id 与元数据，`EmbeddingModel` 负责向量化，`VectorStore` 负责存取与余弦检索，`Retriever` 负责把这几步串起来并在返回前按 id 回填切片原文。
`EmbeddingMath` 定下零向量余弦为 0 的约定，`InMemoryVectorStore` 给出一个不用装任何服务的默认 `VectorStore`。
这些环节都是接口，换实现时 `Retriever` 一行都不用改。

## Spring Boot 配置

配置前缀是 `ragfoundry`，一共五个键：

| 配置键 | 类型 | 默认值 |
| --- | --- | --- |
| `ragfoundry.enabled` | boolean | `true` |
| `ragfoundry.embedding-dimensions` | int | `32` |
| `ragfoundry.chunking.strategy` | String | `recursive` |
| `ragfoundry.chunking.max-chars` | int | `400` |
| `ragfoundry.chunking.overlap` | int | `0` |

```yaml
ragfoundry:
  enabled: true
  embedding-dimensions: 64
  chunking:
    strategy: recursive   # fixed | sentence | recursive
    max-chars: 400
    overlap: 0
```

两条实测行为值得先知道：`ragfoundry.chunking.overlap` 只有 `fixed` 策略生效，`SentenceChunker(int maxChars)` 与 `RecursiveChunker(int maxChars)` 的构造器不接 overlap 参数，给它们配了也不报错、也不起作用。`strategy` 拼错或写成其它值时不会报错，`RagfoundryAutoConfiguration#chunkingStrategy` 的 `switch` 走 `default` 分支静默回退到 `RecursiveChunker`，只有日志里那行 `装配 ChunkingStrategy` 能看出实际用的是哪个类。

自动装配提供四个 bean：`EmbeddingModel`、`VectorStore`、`ChunkingStrategy`、`Retriever`，全部 `@ConditionalOnMissingBean`，在自己的 `@Configuration` 里声明同名 bean 即可顶替。其中 `VectorStore` 固定用无参构造（`new InMemoryVectorStore()`），也就是默认一百万条容量，这个值改不了——要指定容量就自己声明 bean。

## 职责边界

### 全仓只有一个 core 模块

就叫 `ragfoundry-core`，目录名与 artifactId 同名，它是家族 `<repo>-core` 约定的落点。不存在第二个 core，也不存在别的「公共工具模块」。任何跨模块复用的东西，落点只有这一个。

### `ragfoundry-core` 的硬边界

- `src/main` 编译期只依赖 `contract` 与 `slf4j-api`；`grep -r "org.springframework" ragfoundry-core/src/main/java` 的结果是 0，这条由 CI 的 `architecture-boundaries` job 机器化守住。`store` 只在 test 作用域进 `core`，为的是 `RetrieverTest` 能做端到端集成测试。
- 父 POM 刻意不在 `<dependencies>` 里声明通用框架依赖（哪怕只是 `spring-boot-starter-test`）：一旦声明，每个子模块都会背上它的传递依赖，「core 零 Spring 依赖」就无法再用机器判定。
- `contract` 更严一层：它连 `slf4j-api` 都不依赖，只有 `junit-jupiter` 与 `assertj-core` 两个 test 依赖。值对象、接口与异常不许依赖任何东西。
- 后果是：切片、向量化、检索管线必须住在 `contract` / `core`，不许往 starter 或 sample 挪。

### 依赖方向单向

```
ragfoundry-sample  -> ragfoundry-spring-boot-starter -> ragfoundry-core -> ragfoundry-contract
                                                    -> ragfoundry-store -> ragfoundry-contract
```

`contract` 不依赖任何东西，`core` 与 `store` 不反向依赖上层模块。sample 不直接声明 `store`：它的 `VectorStore` 由 starter 传递进来，这条依赖是隐式的，所以快速开始里手动拼 classpath 时 store 要自己加上。

## 不做什么

不做的清单也是设计的一部分，这几条都有对应的源码依据：

- **不生成语义 embedding。** `DeterministicEmbedding#embed` 把 UTF-8 字节按两字节滑动窗口投进 `dimensions` 个桶再 L2 归一化，是哈希投影，不是模型。它存在的理由只是让示例与单测零依赖、零 API Key、可复现。真实项目请实现 `EmbeddingModel` 替换掉它。
- **不接真实向量数据库。** `VectorStore` 接口只有一个实现 `InMemoryVectorStore`，数据放在 `Collections.synchronizedMap` 包的 `LinkedHashMap` 里，进程退出即失。没有 pgvector / Milvus / Faiss 的适配层，也没有方言抽象。
- **不做 BM25 / RRF 融合 / 重排序。** 全仓 `.java` 源码里 grep `BM25` / `RRF` / `rerank` / 重排 / 融合 全部零命中，这些词只出现在根 `pom.xml` 的 `<description>` 里，那是愿景不是现状。检索只有余弦一条路。
- **不调用任何 LLM。** 五个模块的 `pom.xml` 里没有 HTTP 客户端依赖，没有 API Key 配置，也没有 prompt 管理。本仓止步于「取回相关切片」，取回之后怎么用由调用方决定。
- **不做持久化与跨进程一致性保证。** `InMemoryVectorStore` 不写盘、没有事务、没有快照。`capacity` 只是 LRU 上限，不是持久化水位；超容量时淘汰的是最久未访问的条目。被淘汰的条目会先从 store 里消失、不再被检索返回，但它的 id 仍留在 `Retriever` 的 `chunkRegistry` 里，`indexedChunkCount()` 因此会偏大。`Retriever#retrieve` 里的 `if (chunk != null)` 保护的是另一种情形：store 中存在 `chunkRegistry` 之外的 id（比如绕过 `Retriever` 直接调 `store.add`，或多个 `Retriever` 共享同一个 store）。
- **不做业务输入校验。** 只保证最基本的参数合法性：`EmbeddingMath.requireSameDimensions` 先拒 null 数组、再比长度，`InMemoryVectorStore#add` 只拒空 id 与 null 向量，`Retriever#retrieve` 对空查询返回空列表、对 `topK <= 0` 抛异常。没有 `topK` 上限：`search` 用小顶堆保留分数最高的 `topK` 条，多出来的随取随丢，`Retriever` 与 `InMemoryVectorStore` 都只拒非正数。文本长度上限、文档 id 字符集、切片是否为空这些业务校验留给调用方。
- **不校验 `ragfoundry.chunking.strategy` 的取值合法性。** `RagfoundryAutoConfiguration#chunkingStrategy` 的 `switch` 只认 `fixed` 与 `sentence`，其余全部走 `default -> new RecursiveChunker(maxChars)`。写错不抛异常、不打 WARN，只能从装配日志里看出来。
- **不在零向量上抛异常或返回 NaN。** `EmbeddingMath.cosineSimilarity` 在 `normA == 0f || normB == 0f` 时 `return 0f`，`Embedding#cosineSimilarity` 同样先判缓存范数。维度不符才抛 `DimensionMismatchException`，零向量不算错误。
- **不依赖 `llm-switchyard` / `agentswitchyard`。** 根 POM 的 `maven-enforcer-plugin` 里 `bannedDependencies` 明确排除 `io.github.nephets:llm-switchyard*` 与 `io.github.nephets:agentswitchyard*`。三个仓库互相独立，任何方向的依赖都会被执行器拦下。

## 构建与测试

```bash
./mvnw -B -ntp clean verify
```

需要 JDK 21 与 Maven 3.9.9 以上，两者都由 `maven-enforcer-plugin` 在构建时校验。

覆盖率门禁（jacoco `check`，各模块 `pom.xml` 里的 `jacoco.line.minimum`）：

| 模块 | 行覆盖率下限 |
| --- | --- |
| `ragfoundry`（父 POM 默认值） | 60% |
| `ragfoundry-contract` | 70% |
| `ragfoundry-store` | 70% |
| `ragfoundry-core` | 80% |
| `ragfoundry-spring-boot-starter` | 70% |
| `ragfoundry-sample` | 关闭（`jacoco.skip=true`） |

sample 关闭门禁是有意的：它是 demo，不在发布的 API 面上，主要逻辑是端到端演示。给它设一个百分比阈值，等于让演示代码的改动去卡构建。

测试规模：28 个测试类，surefire 实测 258 次执行，全部通过——`contract` 92 / `store` 31 / `core` 95 / `starter` 24 / `sample` 16。

采用 **JUnit 5 + AssertJ**，包含单元测试、契约测试、跨特性集成测试、边界测试与并发测试。统一 **Spotless**（Google Java Format AOSP 风格 + Apache-2.0 许可证头），对 `src/main/java` 与 `src/test/java` 生效；根目录 `*.md` 也受管，规则是去掉行尾空格、文件以换行结尾。

CI 一共三个 job：`verify`（`./mvnw -B -ntp verify`，编译 + 测试 + 覆盖率门禁）、`spotless`（`./mvnw -B -ntp spotless:check`）、`architecture-boundaries`（检查 `ragfoundry-core/src/main/java` 里是否出现 `org.springframework`，命中就回显命中行并让 job 失败）。没有 smoke job，也没有真库 job：本仓不需要数据库或 Docker。

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
**名词位置**：`EmbeddingMath` 放在 `contract` 而不是 `core`。它是 `Embedding` 值对象自己就要用的纯 `float[]` 运算，跟着值对象走比跟着管线走更合理，也让 `contract` 保持「零运行时依赖」。

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
**豁免**：「5 分钟快速开始」片段里的 `System.out.println` 是给读者直接粘贴运行的文档示例，不在门禁范围内——守卫只扫 `src/main/java`，README 不在其中。

## 许可证

[Apache License 2.0](LICENSE)
