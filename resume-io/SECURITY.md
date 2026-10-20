# 安全配置说明（阶段五）

## 生产环境必做

1. **JWT 密钥**：通过环境变量 `JWT_SECRET` 注入，长度至少 32 字节，禁止写入 Git。
2. **HTTPS**：生产环境必须启用 HTTPS，防止 Token 在传输中被截获。
3. **CORS**：在 `SecurityConfig.corsConfigurationSource()` 中仅允许正式前端域名，不要使用 `*`。
4. **数据库**：使用 MySQL，通过环境变量配置 `MYSQL_HOST`、`MYSQL_PORT`、`MYSQL_DATABASE`、`MYSQL_USERNAME`、`MYSQL_PASSWORD`。
5. **日志**：禁止打印完整 Token、密码或 Authorization 请求头。

## 开发环境

- 默认连接本地 MySQL 数据库 `resume_db`（需先安装并启动 MySQL）。
- 演示账号 `demo` / `123456` 由 `DataInitializer` 在首次启动时自动创建。
- 开发用 JWT 密钥默认值仅用于本地，不可用于生产。

## MySQL 初始化

```sql
CREATE DATABASE IF NOT EXISTS resume_db
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;
```

或在连接 URL 中已启用 `createDatabaseIfNotExist=true`，首次启动会自动建库。

## Token 存储

- 前端使用 `localStorage` 存储 `access_token`，需防范 XSS。
- 高安全场景可改为 HttpOnly Cookie + CSRF 防护（需前后端同步调整）。
