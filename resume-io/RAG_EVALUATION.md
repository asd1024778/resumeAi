# RAG 检索增强改造 — 评估文档

> 更新时间：2026-07-07  
> 项目：`resume-io` 后端

---

## 一、改造目标与结论

### 目标

将原先「整段文本直接 Prompt」的 AI 调用，重构为 **RAG（Retrieval-Augmented Generation）** 流程：

1. 文档分块 → 向量化 → 入库  
2. 按问题检索相关片段  
3. 将检索结果作为「参考资料」注入 Prompt  
4. 再调用 DeepSeek 大模型生成

### 结论

| 维度 | 评估 |
|------|------|
| **是否完成 RAG 重构** | 是 — 简历分析、面试、对话均已接入 `RagKnowledgeService` |
| **是否可运行** | 是 — `mvn test` 14 项全部通过 |
| **生产就绪度** | 中 — 本地 Embedding 适合学习/演示，生产建议换专业 Embedding API + 持久化向量库 |
| **前端改动** | 无 — API 路径与响应结构保持不变 |

---

## 二、架构对比

### 改造前

```
用户输入 / 简历全文
        ↓
   ChatClient（DeepSeek）
        ↓
      直接回答
```

问题：长简历易超出上下文；回答缺少统一的领域知识（面试技巧、评分标准）；对话与简历内容无关。

### 改造后

```
用户输入 / 简历 / 知识文档
        ↓
 DocumentChunkService（Tika + TokenTextSplitter）
        ↓
 LocalEmbeddingModel → SimpleVectorStore（内存向量库）
        ↓
 similaritySearch（按 userId / resumeId / sourceType 过滤）
        ↓
 formatContext → 增强 Prompt
        ↓
   ChatClient（DeepSeek）
        ↓
   基于资料的生成结果
```

---

## 三、各模块 RAG 接入说明

### 3.1 简历分析 `ResumeAnalysisService`

| 项目 | 说明 |
|------|------|
| **入口** | `POST /api/resume/upload` |
| **RAG 流程** | ① `createResume` 获得 resumeId → ② `indexResume` 切块入库 → ③ 多路检索（技术/项目/教育/软技能 + 评分规范知识）→ ④ 增强 Prompt → ⑤ `entity(ResumeScore.class)` |
| **相对旧版** | 旧版把**全文**塞进 Prompt；新版以**检索片段 + 知识库**为主，降低 token 压力 |
| **元数据** | `sourceType=resume`, `userId`, `resumeId` |

### 3.2 模拟面试 `InterviewService`

| 项目 | 说明 |
|------|------|
| **开始面试** | 从 DB 读取简历文本 → 重新 `indexResume` → 用 RAG 检索项目经历生成**个性化第二题** |
| **流式反馈** | `streamChatWithResumeContext`：以「题目+回答」为 query，检索简历片段 + 知识库，再生成点评 |
| **相对旧版** | 旧版固定三题 + 无简历上下文的反馈；新版题目与反馈均参考候选人简历 |
| **元数据** | 检索时带 `resumeId`（即前端 reportId） |

### 3.3 AI 对话 `ChatService` / `ChatController`

| 项目 | 说明 |
|------|------|
| **入口** | `GET /api/chat/stream`, `POST /api/chat/sync` |
| **RAG 流程** | `searchHybrid`：全局知识库 + 可选简历上下文 → 增强 Prompt |
| **鉴权** | 需登录（JWT），按 `userId` 隔离用户级知识 |
| **相对旧版** | 旧版直接问答；新版优先依据种子知识（STAR 法则、面试技巧、评分维度） |

### 3.4 知识库 `KnowledgeController`

| 项目 | 说明 |
|------|------|
| **入口** | `POST /api/knowledge/upload`（multipart `file` + 可选 `title`） |
| **作用** | 用户上传 PDF/Word 等 → 解析 → 切块 → `indexKnowledge` |
| **元数据** | `sourceType=knowledge`, `userId` |

### 3.5 启动种子知识 `RagSeedInitializer`

自动写入 3 份全局知识（无需用户操作）：

- `kb-resume-rules` — 简历撰写规范  
- `kb-interview-tips` — 技术面试技巧  
- `kb-scoring-rubric` — 简历评分维度说明  

---

## 四、技术组件清单

| 组件 | 类/配置 | 作用 |
|------|---------|------|
| 向量库 | `VectorStoreConfig` → `SimpleVectorStore` | 内存存储，重启后向量丢失 |
| Embedding | `LocalEmbeddingModel` | 本地哈希稀疏向量，无需额外 API |
| 分块 | `DocumentChunkService` | 500 token/块，中文标点优先切分 |
| 检索服务 | `RagKnowledgeService` | 入库、检索、上下文格式化 |
| 元数据常量 | `RagConstants` | userId / resumeId / sourceType |

### Maven 新增依赖

```xml
<artifactId>spring-ai-vector-store</artifactId>
```

---

## 五、检索策略与元数据过滤

```
简历片段：userId == '{uid}' && resumeId == '{rid}' && sourceType == 'resume'
用户知识：userId == '{uid}' && sourceType == 'knowledge'
全局知识：sourceType == 'knowledge'（种子数据 userId=0）
```

**混合检索** `searchHybrid`：知识库 Top-K/2 + 简历 Top-K/2，合并后截断。

---

## 六、效果评估

### 6.1 预期收益

| 收益 | 说明 |
|------|------|
| 上下文可控 | 长简历只检索相关块，避免整篇塞入 |
| 领域知识一致 | 种子知识保证评分/面试建议有统一基准 |
| 个性化面试 | 第二题可引用简历中的项目关键词 |
| 可扩展 | 用户可通过 `/api/knowledge/upload` 扩充私有知识库 |

### 6.2 当前局限

| 局限 | 影响 | 改进方向 |
|------|------|----------|
| **SimpleVectorStore 内存存储** | 重启后向量丢失，需重新上传/面试时 re-index | Redis Vector / PgVector 持久化 |
| **LocalEmbeddingModel** | 语义理解弱于专业 Embedding，中文检索精度有限 | 接入 DashScope text-embedding / OpenAI ada |
| **无重排序（Rerank）** | Top-K 可能含噪声片段 | 增加 Reranker 模型 |
| **简历重复入库** | 每次分析/开始面试会 `add` 新向量，可能重复 | 按 docId 先删后增 |
| **对话未绑 resumeId** | 通用对话只用全局知识，不自动带当前简历 | 前端传可选 `reportId` 参数 |

### 6.3 测试覆盖

| 测试类 | 验证点 |
|--------|--------|
| `RagKnowledgeServiceTest` | 简历入库检索、种子知识检索、上下文格式化 |
| `InterviewConcurrencyIntegrationTest` | RAG 入库后面试流程正常 |
| `AuthIntegrationTest` | 鉴权与脱敏未受 RAG 影响 |
| `ReportPersistenceIntegrationTest` | 报告持久化与 RAG 流程兼容 |

---

## 七、修改文件一览

### 新增

```
rag/RagConstants.java
rag/LocalEmbeddingModel.java
service/RagKnowledgeService.java
config/VectorStoreConfig.java（由注释改为启用）
config/RagSeedInitializer.java
controller/KnowledgeController.java（由注释改为 WebFlux 实现）
test/.../RagKnowledgeServiceTest.java
RAG_EVALUATION.md
```

### 重构

```
service/ResumeAnalysisService.java   — RAG 多路检索 + 增强 Prompt
service/InterviewService.java        — 个性化出题 + RAG 反馈
service/ChatService.java             — RAG 混合检索对话
service/DocumentChunkService.java    — 新增 chunkText()
service/ReportStoreService.java      — createResume / saveReport 拆分
controller/ResumeController.java     — 先建简历再 RAG 分析
controller/ChatController.java       — 需登录 + userId 传入
```

### 删除

```
service/KnowledgeService.java（注释旧版，由 RagKnowledgeService 替代）
```

### 配置

```
application.properties — app.rag.top-k=5, app.rag.embedding=local
pom.xml — spring-ai-vector-store
```

---

## 八、运行与验证

### 启动

```powershell
cd d:\Project\Java_projeck\resume-io
mvn spring-boot:run
# 或 dev：mvn spring-boot:run "-Dspring-boot.run.profiles=dev"
```

需配置 `DASHSCOPE_API_KEY`（DeepSeek）方可真实调用大模型；向量检索与入库不依赖 API Key。

### 验证步骤

1. **登录** `demo / 123456`  
2. **上传简历** → 观察日志：`文本分块完成`、`Calling EmbeddingModel`  
3. **查看报告** → 评分应结合知识库维度  
4. **开始面试** → 第二题可能包含简历项目摘要  
5. **对话** `POST /api/chat/sync` Body: `{"question":"如何写好项目经历？"}` → 应引用 STAR 法则等种子知识  
6. **知识库** `POST /api/knowledge/upload` 上传 PDF  

### 自动化测试

```powershell
mvn test
```

---

## 九、生产升级路线图（建议）

1. **Embedding**：替换 `LocalEmbeddingModel` 为 DashScope `text-embedding-v3`  
2. **向量库**：`SimpleVectorStore` → PgVector / Redis Stack  
3. **去重**：`indexResume` 前按 `docId` 删除旧向量  
4. **可观测性**：记录每次检索的 query、命中 docId、相似度分数  
5. **Rerank**：对 Top-10 结果用交叉编码器重排取 Top-3  

---

## 十、总结

本次改造将 `resume-io` 从「纯 Prompt 工程」升级为「**分块 + 向量检索 + 知识增强**」的 RAG 架构，三大 AI 场景（分析、面试、对话）均已接入统一检索层 `RagKnowledgeService`。  

在学习与演示场景下，本地 Embedding + 内存向量库即可完整跑通；若面向生产，应优先升级 **Embedding 质量** 与 **向量持久化**，并补充去重与 Rerank 以提升检索精度。
