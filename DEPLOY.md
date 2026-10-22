# Resume IO 部署操作手册

> 本文按「**在什么地方 → 干什么 → 为什么**」组织，每一步都给出可复制命令和验收标准。
> 部署方式：**本机构建镜像 → 传到云服务器 → 服务器只跑容器**（不在服务器上编译）。
>
> 之所以这么选：本机 Docker 已配好镜像加速、Maven/npm 依赖也已缓存；云服务器直接构建大概率卡在拉 `mysql/maven/node/nginx` 基础镜像和 Maven 中央仓库上。

---

## 0. 全景：谁在什么地方做什么

| 阶段 | 在哪里 | 核心动作 | 为什么 | 产出 |
|---|---|---|---|---|
| ① 前置检查 | 本机 + 服务器 | 确认 Docker / 端口 / 资源 | 提前暴露环境差异，别在路上才发现 | 环境清单 |
| ② 代码入库 | 本机 → GitHub | `git push` | 让部署的版本可追溯、可回滚 | 一个 commit SHA |
| ③ 构建镜像 | **本机** | `docker build` 打固定 tag | 复用本机网络与依赖缓存，产出跨机器可运行的成品 | 2 个 `.tar` |
| ④ 传输 | 本机 → 服务器 | `docker save` + `scp` | 绕开服务器拉镜像/拉依赖的网络问题 | 镜像文件落在服务器 |
| ⑤ 配置 | **服务器** | 建 `.env` 填密钥 | 密钥与镜像/代码分离，不落仓库不落镜像 | `/opt/resume-io/.env` |
| ⑥ 启动 | 服务器 | `docker load` + `up --no-build` | 用成品镜像起服务，不触发重新构建 | 4 个 healthy 容器 |
| ⑦ 收敛 | 服务器 | 关端口映射 + 安全组 | 数据库/Redis 绝不能暴露公网 | 只对外开 80/443 |
| ⑧ 验证 | 服务器 + 浏览器 | 分层验收 | 证明「起来了」且「真的能用」 | 验收记录 |
| ⑨ 运维 | 服务器 | 日志/备份/升级/回滚 | 上线只是开始 | 可回滚的运行态 |

架构一句话：浏览器 → `frontend`(Nginx:80) → 反代 `/api`、`/ws` → `backend`(:8080) → `mysql`(:3306) + `redis`(:6379)。四个服务在同一个 compose 网络里，靠**服务名**互通。

---

## 1. 阶段① — 前置检查

### 1.1 本机（Windows）

**目的**：确认构建能跑通，别把问题带到服务器。

```powershell
docker version                 # 需要 Engine 24+ / Compose v2
docker compose version         # 需要 v2.3.3+（compose 文件用了顶层 name:）
cd D:\Project\Java_projeck; git status -sb
```

| 验收项 | 期望 |
|---|---|
| `docker compose version` | `v2.x` 及以上 |
| `git status -sb` | 工作区干净，`main` 与 `origin/main` 一致 |

### 1.2 服务器（Linux）

**目的**：服务器只需要「容器运行时」，不需要 JDK / Maven / Node（全在镜像里），环境更一致。

```bash
# 装 Docker Engine + compose 插件
curl -fsSL https://get.docker.com | sh

# 国内服务器：配镜像加速（否则拉 mysql/redis 会超时）
sudo mkdir -p /etc/docker
sudo tee /etc/docker/daemon.json <<'EOF'
{
  "registry-mirrors": [
    "https://docker.1ms.run",
    "https://docker-0.unsee.tech",
    "https://docker.m.daocloud.io"
  ]
}
EOF
sudo systemctl restart docker
```

| 资源 | 最低 | 说明 |
|---|---|---|
| 内存 | **2GB**（推荐 4GB） | JVM `-Xmx512m` + MySQL 8 常驻 400MB+ + Redis + Nginx |
| 磁盘 | 20GB+ | 镜像约 2.5GB + 数据卷 |
| 架构 | x86_64 | 若为 ARM 实例，构建时必须加 `--platform linux/amd64`（见阶段③） |

> 服务器**不装** Java/Node 是刻意的：装了就多一套版本漂移的可能，镜像里已经是 JDK17 + Node20。

---

## 2. 阶段② — 代码入库（本机）

**目的**：让「部署的是哪个版本」有据可查，也为回滚留锚点。

```powershell
cd D:\Project\Java_projeck
git add -A
git commit -m "部署前快照"
git push origin main
git rev-parse --short HEAD      # 记下这个 SHA，写进发布记录
```

| 注意 | 原因 |
|---|---|
| 仓库里**没有** `.env`，只有 `.env.example` | 密钥不进版本库（历史里的旧 Key 已清洗） |
| 提交前确认没有把 `.env` 加进来 | `git status` 里不应出现 `.env` |

---

## 3. 阶段③ — 构建镜像（**本机**做，不在服务器做）

**目的**：在「网络通、依赖全」的机器上产出可移植成品，服务器只负责跑。

```powershell
cd D:\Project\Java_projeck

# 固定 tag（不要 latest，回滚要用）；ARM 服务器必须在两条命令里都加 --platform linux/amd64
docker build --platform linux/amd64 -t resume-io-backend:1.0.0  .\resume-io
docker build --platform linux/amd64 -t resume-io-frontend:1.0.0 .\resume-web
```

| 镜像 | 体积参考 | 内容 |
|---|---|---|
| `resume-io-backend` | ~626MB | Maven 构建 + JRE17 运行，内含 `app.jar` |
| `resume-io-frontend` | ~77MB | Nginx + 静态产物 + `nginx.conf`（`/api`、`/ws` 反代到 `backend:8080`） |

**为什么只打这两个**：`mysql:8.0`、`redis:7-alpine` 让服务器自己拉（配了加速就很快），一起打包会让传输量从 ~700MB 变成 ~1.9GB。

**冒烟验证（本机，构建完立刻做）**：

```powershell
docker images | Select-String "resume-io"
# 后端单独起一下，确认能读到配置、能启动（会连不上库，属正常，看日志有 Netty 启动即可）
docker run --rm -e MYSQL_HOST=127.0.0.1 resume-io-backend:1.0.0
```

| 验收 | 期望 |
|---|---|
| `docker images` | 两个镜像，tag 是 `1.0.0` |
| 镜像内无密钥 | `docker run --rm --entrypoint env resume-io-backend:1.0.0 \| findstr DEEPSEEK` 无输出 |

---

## 4. 阶段④ — 传输（本机 → 服务器）

**目的**：把成品跨公网搬过去，同时能校验没传坏。

```powershell
cd D:\Project\Java_projeck

# 导出（必须用 -o 写文件，PowerShell 管道会破坏二进制）
docker save -o backend-1.0.0.tar  resume-io-backend:1.0.0
docker save -o frontend-1.0.0.tar resume-io-frontend:1.0.0

# 压缩（Win10+ 自带 tar）
# ⚠️ 注意：tar -czf 产出的是「两层」文件（.tar.gz 里面只有 .tar 本身），
#         服务器上必须先解压再 load，不能直接 docker load -i xxx.tar.gz
tar -czf backend-1.0.0.tar.gz  backend-1.0.0.tar
tar -czf frontend-1.0.0.tar.gz frontend-1.0.0.tar

# 记下校验值
Get-FileHash backend-1.0.0.tar.gz, frontend-1.0.0.tar.gz -Algorithm SHA256 | Format-Table

# 上传
scp backend-1.0.0.tar.gz frontend-1.0.0.tar.gz root@<服务器IP>:/root/
```

服务器端校验（防止传输截断导致 `docker load` 报错）：

```bash
sha256sum /root/*.tar.gz          # 与上面 Get-FileHash 的值逐位比对
```

**想省这一步解压**：用 Git for Windows 自带的 `gzip` 生成「单层」gz，服务器就能直接 `docker load -i`：

```powershell
gzip -k backend-1.0.0.tar      # 产出 backend-1.0.0.tar.gz（内容就是镜像 tar 本体）
gzip -k frontend-1.0.0.tar
```

| 注意 | 原因 |
|---|---|
| 别把 `.env` 一起打包 | 密钥只放服务器，不随制品流转 |
| 公网慢就传对象存储再 `wget` | scp 断线要重传，OSS/COS 支持断点续传 |

---

## 5. 阶段⑤ — 配置（**服务器**）

**目的**：把「运行参数」和「密钥」放在镜像之外，做到**同一份镜像可在不同环境跑**，且密钥不落仓库、不落镜像。

> 服务器上**不需要整个仓库**，只需要 3 个文件。

### 5.1 建目录、放文件

```bash
mkdir -p /opt/resume-io/resume-io/scripts && cd /opt/resume-io

# 方式一：从 GitHub 只取需要的文件（推荐，仓库里有 node_modules，全量 clone 很慢）
git init -q && git remote add origin https://github.com/asd1024778/resumeAi.git
git fetch --depth 1 origin main && git checkout FETCH_HEAD -- docker-compose.yml
git checkout FETCH_HEAD -- resume-io/scripts/init-db.sql
# .env.example 也取出来做模板
git checkout FETCH_HEAD -- .env.example

# 方式二：本机上传（服务器连不上 GitHub 时）
#   scp docker-compose.yml root@<IP>:/opt/resume-io/
#   scp resume-io/scripts/init-db.sql root@<IP>:/opt/resume-io/resume-io/scripts/
#   scp .env.example root@<IP>:/opt/resume-io/

chmod 600 /opt/resume-io/.env 2>/dev/null || true
ls -R /opt/resume-io
```

目录结构应为：

```
/opt/resume-io/
├── docker-compose.yml
├── .env.example          → 复制成 .env
├── .env                  （手写，chmod 600）
└── resume-io/scripts/init-db.sql
```

### 5.2 写 `.env`（**这一步决定能不能用**）

```bash
cp /opt/resume-io/.env.example /opt/resume-io/.env
vim /opt/resume-io/.env
chmod 600 /opt/resume-io/.env
```

必须改的：

| 变量 | 改成 | 为什么 |
|---|---|---|
| `DEEPSEEK_API_KEY` | 你的真实 Key | 为空则简历分析/面试/对话全部 500 |
| `JWT_SECRET` | `openssl rand -base64 48` | 默认值写死在公共仓库里，等于没有签名保护 |
| `MYSQL_PASSWORD` | 强密码 | 默认 `123456` |
| `FRONTEND_PORT` | `80` | 否则要访问 `:5173` |
| `APP_DEMO_DATA` | `false` | 否则线上存在 `demo/123456` 后门账号 |
| `SMS_DEBUG_RETURN_CODE` | 见下方决策 | `true` = 注册页直接显示验证码，**生产禁止** |

**关于短信验证码（必须做决策）**：
关掉 `SMS_DEBUG_RETURN_CODE` 后注册会卡死——代码里只有 `LoggingSmsSender`（只写日志不发短信）。三个选项：

1. 接真实短信网关（新增一个 `SmsSender` 实现类，业务代码不用动）；
2. 暂时保持 `true` 且不对外开放注册；
3. 保持 `false`，让用户去日志取码：`docker compose logs backend | grep 模拟短信`。

| 验收 | 期望 |
|---|---|
| `grep -c DEEPSEEK_API_KEY /opt/resume-io/.env` | `1` |
| `ls -l /opt/resume-io/.env` | `-rw-------` |

---

## 6. 阶段⑥ — 启动（服务器）

**目的**：用成品镜像起服务，并保证启动顺序正确（MySQL 健康后才起后端）。

```bash
cd /opt/resume-io

# 载入镜像（先解压外层 gz，再 load 里面的 tar）
# 若报 "open /var/lib/docker/tmp/.../repositories: no such file or directory"，
# 几乎都是漏了解压、把两层包直接喂给了 docker load
gunzip -k /root/backend-1.0.0.tar.gz      # -k 保留原 .gz
gunzip -k /root/frontend-1.0.0.tar.gz
docker load -i /root/backend-1.0.0.tar
docker load -i /root/frontend-1.0.0.tar
docker images | grep resume-io

# 先确认 compose 引用的 tag 与载入的一致（改 image: 行）
grep -n "image: resume-io" docker-compose.yml

# 启动（--no-build 必须加，否则会去重新构建）
docker compose up -d --no-build
docker compose ps
```

**为什么必须 `--no-build`**：compose 文件里有 `build:` 段，不加这个参数会触发构建 → 服务器上重新拉 Maven 依赖 → 大概率超时失败。

| 验收 | 期望 |
|---|---|
| `docker compose ps` | 4 个容器 `Up (healthy)` |
| 启动顺序 | 日志里 `mysql` 先 healthy，`backend` 才 Starting |
| `docker compose logs backend \| grep "AI 配置自检"` | `api-key=sk-xxxx****xxxx`（脱敏）、`model=deepseek-flash` |

首启耗时：MySQL 初始化 30s 左右，后端 `start_period` 90s，一般 1–2 分钟内全部 healthy。

---

## 7. 阶段⑦ — 收敛暴露面（服务器）

**目的**：MySQL / Redis / 后端接口一律不暴露公网，只留前端入口。

### 7.1 安全组（云厂商控制台）

只放行 **80 / 443**（以及你的 SSH 端口）。关掉 3307、6380、8080、5173。

### 7.2 改 compose 的端口映射

```bash
cd /opt/resume-io
cp docker-compose.yml docker-compose.yml.bak
vim docker-compose.yml
```

把 `mysql`、`redis`、`backend` 三个服务的 `ports:` 整段注释掉（它们只在 compose 内部网络互通，不需要宿主端口）；`frontend` 保留 `"${FRONTEND_PORT:-5173}:80"`。

```bash
docker compose up -d --no-build
```

| 验收 | 期望 |
|---|---|
| 公网访问 `http://<IP>:3307` | 连接被拒 |
| 公网访问 `http://<IP>:6380` | 连接被拒 |
| 公网访问 `http://<IP>:8080` | 连接被拒 |
| 公网访问 `http://<IP>/` | 200 |

---

## 8. 阶段⑧ — 验证（分层，缺一层都不算成功）

| 层 | 在哪 | 命令 / 操作 | 通过标准 |
|---|---|---|---|
| 容器 | 服务器 | `docker compose ps` | 4 个 healthy |
| 日志 | 服务器 | `docker compose logs --tail 50 backend` | 无 ERROR；有「AI 配置自检」 |
| 静态页 | 浏览器 / curl | `curl -I http://<IP>/` | 200，`text/html` |
| SPA 路由 | 浏览器 | 打开 `/register` 直接刷新 | 不 404（history 回退生效） |
| 反代 | 服务器 | `curl -s http://127.0.0.1/api/auth/login -H 'Content-Type: application/json' -d '{"username":"demo","password":"123456"}'` | 返回 `{"code":200,...}`（若已关 demo 数据，返回 401 也说明反代通） |
| 数据库 | 服务器 | `docker compose exec mysql mysql -uroot -p -e "show tables from resume_db;"` | 有 `users`、`resumes` 等表 |
| 注册 | 浏览器 | `/register` 走完整流程 | 收码/显码 → 注册成功 → 自动登录 |
| AI | 浏览器 | 上传一份简历 | 10~60s 出评分与建议（首调较慢） |
| 数据落库 | 服务器 | `docker compose exec mysql mysql -uroot -p -e "select id,username,phone from resume_db.users;"` | 新用户有记录 |

---

## 9. 阶段⑨ — 日常运维（服务器）

| 场景 | 在哪 | 命令 | 目的 |
|---|---|---|---|
| 看日志 | 服务器 | `docker compose logs -f backend` | 排查运行问题 |
| 重启单个服务 | 服务器 | `docker compose restart backend` | 改完配置生效 |
| 停止（保数据） | 服务器 | `docker compose down` | 常规停机 |
| ⚠ 清库 | 服务器 | `docker compose down -v` | **连数据卷一起删，慎用** |
| 备份数据库 | 服务器 | `docker compose exec -T mysql mysqldump -uroot -p"$MYSQL_PASSWORD" resume_db > /root/backup-$(date +%F).sql` | 出事能恢复 |
| 升级版本 | 本机 → 服务器 | 本机打 `1.0.1` → 传 → `load` → 改 compose 的 tag → `up -d --no-build` | 滚动更新 |
| 回滚 | 服务器 | 改回旧 tag → `docker compose up -d --no-build` | 5 分钟内退回 |

**升级/回滚要点**：镜像用固定 tag（`1.0.0` / `1.0.1`），`down` 之前旧镜像别删；数据库结构由 `ddl-auto=update` 自动加列，回滚代码不会自动回滚表结构——所以**涉及删列/改语义的版本要单独评估**。

---

## 10. 附录 A：故障对照表

| 现象 | 去哪个位置看 | 根因 | 处置 |
|---|---|---|---|
| `docker compose up` 直接报无法连接 registry | 服务器 / 阶段① | 没配镜像加速 | 配 `/etc/docker/daemon.json` 后重启 docker |
| `bind source path does not exist: .../init-db.sql` | 服务器 / 阶段⑤ | 只传了 compose，漏了 `init-db.sql` | 补齐 `/opt/resume-io/resume-io/scripts/init-db.sql`，或注释掉该 volume 行 |
| 容器起了但 AI 接口 500，日志有 401 | 服务器 backend 日志 | Key 没填 / 无效 | 改 `.env` → `docker compose up -d backend` |
| `Unable to determine Dialect without JDBC metadata` | backend 日志 | 根本没连上 MySQL（不是 JPA 配置问题） | 看 mysql 是否 healthy；`MYSQL_HOST` 应为 `mysql`（服务名，不是 localhost） |
| 前端 502 | 浏览器 → `docker compose logs frontend` | nginx 反代不到 `backend` | 确认 4 个容器在同一 compose 网络；单独 `docker run` 前端必然失败 |
| 页面空白、`/register` 刷新 404 | 浏览器 | 静态资源 404 或 SPA 回退未生效 | 确认 `nginx.conf` 已打进镜像（`docker run --rm --entrypoint sh resume-io-frontend:1.0.0 -c "cat /etc/nginx/conf.d/default.conf"`） |
| `exec format error` | `docker compose logs` | 镜像架构与服务器不一致 | 本机重新 `build --platform linux/amd64` |
| 服务器内存被打满 / OOM | `dmesg`、`free -m` | 机器太小（1GB） | 升配到 2GB+ |
| 注册页拿不到验证码 | 浏览器 / backend 日志 | `SMS_DEBUG_RETURN_CODE=false` 且没接网关 | 见阶段⑤的短信三选项 |
| 语音面试连不上 | 浏览器控制台 | `voiceSocket.ts` 里硬编码 `ws://localhost:8080` | 代码问题，需改成 `location.host` 相对地址（见附录 C） |

---

## 11. 附录 B：速查表

**服务与端口**

| 服务 | 容器名 | 容器内 | 宿主机（默认） | 对外 |
|---|---|---|---|---|
| frontend | `resume-io-frontend` | 80 | 5173 → 改 80 | ✅ 唯一入口 |
| backend | `resume-io-backend` | 8080 | 8080 | ❌ 关掉 |
| mysql | `resume-io-mysql` | 3306 | 3307 | ❌ 关掉 |
| redis | `resume-io-redis` | 6379 | 6380 | ❌ 关掉 |

**文件**

| 路径 | 作用 |
|---|---|
| `docker-compose.yml` | 四服务编排、健康检查、依赖顺序 |
| `.env`（服务器手写） | 密钥与运行参数，`chmod 600` |
| `resume-io/scripts/init-db.sql` | 首次初始化数据卷时建库（卷已存在则不再执行） |
| 卷 `mysql_data` / `redis_data` | 数据持久化，`down -v` 会删 |

**关键环境变量**

`DEEPSEEK_API_KEY` `DEEPSEEK_MODEL` `JWT_SECRET` `MYSQL_PASSWORD` `FRONTEND_PORT` `BACKEND_PORT` `APP_DEMO_DATA` `SMS_DEBUG_RETURN_CODE` `SENSITIVE_STORAGE` `NPM_REGISTRY`

---

## 12. 附录 C：本手册**不覆盖**、需要单独做的事

| 事项 | 现状 | 为什么重要 |
|---|---|---|
| **HTTPS** | Nginx 只监听 80，无证书 | 生产必须上 TLS（云 LB / Caddy / certbot） |
| **真实短信网关** | 只有 `LoggingSmsSender`（只写日志） | 关掉调试回传后注册不可用 |
| **语音面试 WS** | 前端硬编码 `ws://localhost:8080/ws/voice` | 上线后该功能失效；nginx 的 `/ws/` 反代已配好，改前端一行即可 |
| **反向代理真实 IP** | 未配 `X-Forwarded-For` 透传链路 | 后续做限流/审计需要 |
| **日志轮转与监控** | Docker 默认 json-file，无上限 | 长期运行会吃满磁盘 |
| **数据库备份策略** | 无自动备份 | 单机部署，卷一丢全没 |
| **CI/CD** | 手工构建上传 | 迭代频繁时考虑 Actions + 私有仓库 |

---

**版本记录**

| 日期 | 内容 |
|---|---|
| 2026-10-21 | 首版：本机构建 → 传输 → 服务器载入启动的完整流程 |
