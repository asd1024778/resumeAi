# 并发保护与数据实体化 — 操作说明文档

> 更新时间：2026-07-06  
> 适用范围：`resume-io` 后端

---

## 一、本次改造目标

1. **并发保护**：防止面试会话、报告读取等环节在并发请求下出现数据错乱或对象被外部篡改。
2. **测试代码实体化**：将原先内存 Map、空壳类、预留占位符落地为 JPA 持久化实体与可用接口。
3. **可验证**：补充集成测试，确保改造后行为可回归。

---

## 二、做了什么（摘要）

| 类别 | 改造前 | 改造后 |
|------|--------|--------|
| 分析报告 | `ReportStoreService` 内存 `ConcurrentHashMap` | MySQL/H2 表 `analysis_reports` + `resumes` |
| 面试会话 | `InterviewService` 内存 Map + 可变 Context | 表 `interview_sessions` + `interview_questions` |
| 简历实体 | 空类 `Resume.java` | `ResumeEntity` + `ResumeRepository` |
| 测试控制器 | 空类 `TestController` | `DevTestController`（仅 `dev` profile） |
| 演示数据 | 无条件启动写入 | `app.demo-data.enabled` 可控 + `synchronized` 初始化 |
| 并发 | 仅 Map 线程安全，会话内写非原子 | 会话 `ReentrantLock` + JPA `@Version` 乐观锁 |
| 报告返回 | 直接返回内部对象引用 | `DtoCopies` 防御性拷贝 |

---

## 三、新增数据库表（JPA 自动建表）

启动后 Hibernate 会自动创建（`spring.jpa.hibernate.ddl-auto=update`）：

### 1. `resumes` — 上传的简历原文

| 字段 | 说明 |
|------|------|
| id | 主键，自增 |
| user_id | 所属用户 |
| filename | 原始文件名 |
| parsed_text | Tika 解析后的纯文本 |
| created_at | 上传时间 |

### 2. `analysis_reports` — AI 分析报告

| 字段 | 说明 |
|------|------|
| id | 报告 ID（与 resume.id 一致，兼容前端 `reportId`） |
| user_id | 所属用户 |
| resume_id | 关联 resumes.id |
| report_data | TEXT 列，JSON 序列化的 `AnalysisReportDto` |
| created_at | 创建时间 |

### 3. `interview_sessions` — 面试会话

| 字段 | 说明 |
|------|------|
| id | 会话 ID |
| user_id | 用户 ID |
| report_id | 关联报告（前端称 resumeId） |
| status | in_progress / completed |
| current_index | 当前题目索引 |
| version | **乐观锁版本号** |
| created_at / updated_at | 时间戳 |

### 4. `interview_questions` — 面试题目

| 字段 | 说明 |
|------|------|
| id | 题目 ID |
| session_id | 所属会话 |
| content / category / difficulty | 题目内容 |
| question_order | 顺序 |

---

## 四、并发保护机制说明

### 4.1 面试提交答案 `POST /api/interview/answer`

```
请求进入
  → SessionLockManager 按 sessionId 获取 ReentrantLock
  → lock.lock()
  → 读取 InterviewSessionEntity（带 @Version）
  → currentIndex + 1 并 save
  → 若 version 冲突 → OptimisticLockException → ConcurrentOperationException
  → lock.unlock()
```

**系统响应：**

- 成功：`{ "code": 200, "data": { "message": ..., "nextQuestion": ... } }`
- 会话不存在：`404`
- 无权访问：`403`
- **并发冲突：`409`，message 为「面试进度已被其他请求更新，请刷新后重试」**

### 4.2 分析报告读取 `GET /api/report/{id}`

- 从数据库读取后通过 `DtoCopies.copyReport()` 返回**副本**。
- 前端修改返回值不会影响数据库中的数据。

### 4.3 敏感资料内存存储

- `InMemorySensitiveProfileStore` 保留 `ConcurrentHashMap`，并增加 `merge` 能力供后续扩展。

### 4.4 演示数据初始化

- `DataInitializer.initDemoData()` 使用 `static synchronized`，避免并行 Runner 重复插入 demo 用户。

---

## 五、修改 / 新增文件清单

### 新增

```
entity/ResumeEntity.java
entity/AnalysisReportEntity.java
entity/InterviewSessionEntity.java
entity/InterviewQuestionEntity.java
repository/ResumeRepository.java
repository/AnalysisReportRepository.java
repository/InterviewSessionRepository.java
repository/InterviewQuestionRepository.java
persistence/AnalysisReportConverter.java
concurrency/SessionLockManager.java
concurrency/DtoCopies.java
exception/ConcurrentOperationException.java
controller/DevTestController.java
test/.../ReportPersistenceIntegrationTest.java
test/.../InterviewConcurrencyIntegrationTest.java
OPERATIONS.md（本文档）
```

### 重写 / 大幅修改

```
service/ReportStoreService.java      — 内存 → JPA
service/InterviewService.java        — 内存 → JPA + 锁
service/ReportMapper.java            — 移除内存 ID 生成器
controller/ResumeController.java     — 调用 saveWithResume
controller/InterviewController.java  — 409 并发冲突处理
exception/GlobalExceptionHandler.java — 409 处理
config/DataInitializer.java          — 条件化 + synchronized
config/SecurityConfig.java           — 放行 /api/dev/**
dto/InterviewSessionDto.java         — 更新注释
```

### 删除

```
entity/Resume.java           — 空占位，由 ResumeEntity 替代
controller/TestController.java — 空占位，由 DevTestController 替代
```

### 配置变更

```
application.properties         — 新增 app.demo-data.enabled
application-dev.properties     — 同上
src/test/resources/application.properties — 同上
```

---

## 六、API 行为变化（对前端的影响）

### 6.1 上传简历 `POST /api/resume/upload`

- **变化**：报告与简历落库；`reportId` 现为数据库自增 ID（与 `resumeId` 相同）。
- **响应格式不变**：仍为 `AnalysisReportDto`。
- **重要**：**重启服务后报告仍可访问**（不再丢失）。

### 6.2 查询报告 `GET /api/report/{id}`

- 从数据库按 `id + userId` 查询，无变化的路径与响应结构。

### 6.3 开始面试 `POST /api/interview/start`

- 会话与题目写入数据库，返回的 `session.id`、`question.id` 为 DB 主键（仍为 number）。

### 6.4 提交答案 `POST /api/interview/answer`

- 新增 **409** 业务码（并发冲突），前端可选提示用户刷新。

### 6.5 开发调试（仅 dev profile）

```
GET /api/dev/ping                 → { "code":200, "data":"dev-ok" }
GET /api/dev/concurrency-info     → 并发策略说明
```

启动 dev：

```powershell
cd resume-io
mvn spring-boot:run "-Dspring-boot.run.profiles=dev"
```

---

## 七、如何验证

### 7.1 运行自动化测试

```powershell
cd d:\Project\Java_projeck\resume-io
mvn test
```

包含：

- `AuthIntegrationTest` — 登录、脱敏、鉴权
- `SensitiveDataMaskTest` — 脱敏规则
- `ReportPersistenceIntegrationTest` — 报告持久化与拷贝隔离
- `InterviewConcurrencyIntegrationTest` — 面试会话索引推进

### 7.2 手动验证持久化

1. 启动后端（MySQL 或 dev/H2）
2. 登录 `demo / 123456`
3. 上传简历 → 记下 `reportId`
4. **重启后端**
5. 访问 `/api/report/{reportId}` → 应仍能返回报告

### 7.3 IDEA 中查看数据

- **MySQL/H2 `resumes`、`analysis_reports`**：查看上传记录与 JSON 报告
- **interview_sessions / interview_questions**：查看面试进度与 `version` 字段
- **Redis `user:sensitive:*`**：敏感资料（若 `app.sensitive.storage=redis`）

---

## 八、配置项

| 配置 | 默认值 | 说明 |
|------|--------|------|
| `app.demo-data.enabled` | `true` | 是否创建 demo 用户及敏感资料 |
| `APP_DEMO_DATA` | 环境变量覆盖 | 生产建议设为 `false` |
| `app.sensitive.storage` | `redis` | 敏感资料 Redis / memory |
| `spring.jpa.hibernate.ddl-auto` | `update` | 自动建表/更新表结构 |

---

## 九、生产部署建议

1. 设置 `APP_DEMO_DATA=false`，避免生产环境自动创建 demo 账号。
2. 不要使用 `dev` profile 启动生产实例（避免暴露 `/api/dev/*`）。
3. MySQL 定期备份；报告 JSON 含用户简历分析结果。
4. 多实例部署时，面试会话锁为**进程内锁**；跨节点并发仍依赖 JPA `@Version` 乐观锁，冲突时返回 409。

---

## 十、后续可扩展方向（未在本次实现）

- 知识库模块（`KnowledgeController` 等注释代码）实体化
- 报告 / 会话历史列表 API
- 分布式锁（Redis Redisson）替代进程内 `ReentrantLock`
- 前端对 409 的统一 Toast 提示
