# 宝塔面板 + 云服务器 部署手册（傻瓜式 · IP + HTTPS 自签）

> **目标**：在宝塔 Linux 云服务器上把项目跑起来，用 `https://<你的IP>:8443` 访问。
> **前提**：无备案、无域名、无正式证书。全部操作按顺序做，每步都有「看到什么算成功」。
> **耗时**：约 40–60 分钟（其中传镜像占大头）。

---

## ⚠️ 先看清楚：HTTPS 长什么样

| 项目 | 结果 |
|---|---|
| 地址 | `https://1.2.3.4:8443`（1.2.3.4 换成你的公网 IP） |
| 首次打开 | 浏览器显示「**您的连接不是私密连接**」/ `NET::ERR_CERT_AUTHORITY_INVALID` |
| 怎么进去 | Chrome/Edge：点「高级」→「继续前往 1.2.3.4（不安全）」；Firefox：点「高级」→「接受风险并继续」 |
| 进去之后 | 地址栏显示「不安全」/ 红色斜杠锁，但**功能完全正常**，且流量是加密的 |
| 微信/QQ 打开 | ❌ **打不开**（微信内置浏览器不接受自签证书，没有「继续」按钮） |
| 解决警告的办法 | 只有两个：买域名 + 免费证书（需备案）；或用 Cloudflare Tunnel（免备案，自动真证书） |

**为什么能用自签证书**：证书的信任链里没有公共 CA，浏览器不认识签发者，所以报警。但加密本身是有效的（别人抓包看不到内容）。演示给自己/同事看完全够用；发给微信群里的人看会失败。

如果这个前提你能接受，继续往下。

---

## 第 0 步（本机）：先把镜像打出来

在**你自己的 Windows 电脑**上做，不在服务器上做。

```powershell
cd D:\Project\Java_projeck

docker build --platform linux/amd64 -t resume-io-backend:1.0.0  .\resume-io
docker build --platform linux/amd64 -t resume-io-frontend:1.0.0 .\resume-web

docker save -o backend-1.0.0.tar  resume-io-backend:1.0.0
docker save -o frontend-1.0.0.tar resume-io-frontend:1.0.0

# ⚠️ 两种压缩方式，务必分清：
#  A) tar -czf  → 两层包（.gz 里只有一个 .tar），服务器上必须先 gunzip 再 load
#  B) gzip   → 单层包（.gz 里就是镜像本体），服务器上可直接 docker load -i
# 注意 A 方式产生的文件，“直觉上”像能直接 load，但会报
#   open /var/lib/docker/tmp/.../repositories: no such file or directory
tar -czf backend-1.0.0.tar.gz  backend-1.0.0.tar
tar -czf frontend-1.0.0.tar.gz frontend-1.0.0.tar
```

**验收**：`dir *.tar.gz` 能看到两个文件，后端约 250–300MB、前端约 35MB。

想省掉服务器上那一步解压（更省事），在 **Git Bash** 里用 `gzip` 生成单层包：

```bash
gzip -k backend-1.0.0.tar      # 产出 backend-1.0.0.tar.gz，内容 = 镜像 tar 本体
gzip -k frontend-1.0.0.tar
```

> 只传这两个。`docker-compose.yml`、`.env`、`init-db.sql` 全部在服务器上现场生成（内容短、且必须按宝塔环境定制，传旧的反而会出错）。

---

## 第 1 步（服务器）：装宝塔

如果面板已经装好了，跳过这步，记下**面板地址、端口、账号密码**。

```bash
# 用 root 登录服务器后执行（命令以 bt.cn 官网当前版本为准）
url=https://download.bt.cn/install/install_lts.sh; curl -sSO $url && bash install_lts.sh ed8484bec
```

**验收**：命令结束会打印面板地址（形如 `http://1.2.3.4:8888/xxxxxx`）、用户名、密码。浏览器打开能登录。

> ⚠️ 记住宝塔面板用的是 **8888** 端口，我们项目不能用这个端口。

---

## 第 2 步（宝塔面板）：装 Docker

**面板 → 软件商店 → 搜索「Docker」→ 安装**（宝塔 9.x 叫「Docker」，7.x/8.x 叫「Docker管理器」）。

**验收**：软件商店里显示「已安装」；点进去能看到「容器」列表（此时是空的）。

> 装不上（多半是国内网络）就在 SSH 里手动装：
> ```bash
> curl -fsSL https://get.docker.com | sh
> systemctl enable --now docker
> ```
> 然后配镜像加速（拉 mysql/redis 要用）：
> ```bash
> mkdir -p /etc/docker
> cat > /etc/docker/daemon.json <<'EOF'
> {
>   "registry-mirrors": [
>     "https://docker.1ms.run",
>     "https://docker-0.unsee.tech",
>     "https://docker.m.daocloud.io"
>   ]
> }
> EOF
> systemctl restart docker
> ```

---

## 第 3 步（宝塔面板）：省内存，停掉用不上的软件

2G 内存的机器，宝塔默认装的 MySQL + PHP + phpMyAdmin 会吃掉几百 MB，跟我们的容器抢内存。

**面板 → 软件商店**：

| 软件 | 处理 | 原因 |
|---|---|---|
| Nginx | **保留** | 我们的 HTTPS 站点靠它 |
| MySQL 5.7/8.0 | 停掉（若已装） | 我们用容器里的 MySQL |
| PHP / phpMyAdmin | 停掉 | 项目是纯静态 + 容器后端，用不到 |

**验收**：`free -h` 显示可用内存在 1GB 以上。

---

## 第 4 步（宝塔面板）：上传镜像

**面板 → 文件 → 进入 `/root` → 上传**，把 `backend-1.0.0.tar.gz`、`frontend-1.0.0.tar.gz` 拖进去。

宝塔的文件上传支持进度显示和续传，比命令行直观。

**验收**：`/root` 下能看到两个 `.tar.gz`，大小与第 0 步一致。

> 上传慢（服务器带宽小）的加速办法：先把文件传到阿里云 OSS/腾讯云 COS，再在宝塔文件里用「远程下载」填 OSS 的临时链接。云服务器**入网**带宽通常比出网宽松，快很多。

---

## 第 5 步（服务器终端）：生成配置文件

**面板 → 终端**（或 SSH 登录），整段复制粘贴执行。

```bash
mkdir -p /opt/resume-io/resume-io/scripts
cd /opt/resume-io

# ① 建库脚本（只有首次初始化数据卷时会执行）
cat > resume-io/scripts/init-db.sql <<'EOF'
CREATE DATABASE IF NOT EXISTS resume_db
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
USE resume_db;
EOF

# ② compose 编排（宝塔专用：只有前端映射到本机 18080，其余不占宿主端口）
cat > docker-compose.yml <<'EOF'
name: resume-io

services:
  mysql:
    image: mysql:8.0
    container_name: resume-io-mysql
    restart: unless-stopped
    environment:
      MYSQL_ROOT_PASSWORD: "${MYSQL_PASSWORD}"
      MYSQL_DATABASE: resume_db
      TZ: Asia/Shanghai
    command:
      - --character-set-server=utf8mb4
      - --collation-server=utf8mb4_unicode_ci
      - --default-time-zone=+08:00
    volumes:
      - mysql_data:/var/lib/mysql
      - ./resume-io/scripts/init-db.sql:/docker-entrypoint-initdb.d/init-db.sql:ro
    healthcheck:
      test: ["CMD", "mysqladmin", "ping", "-h", "127.0.0.1", "-uroot", "-p${MYSQL_PASSWORD}"]
      interval: 10s
      timeout: 5s
      retries: 15
      start_period: 40s

  redis:
    image: redis:7-alpine
    container_name: resume-io-redis
    restart: unless-stopped
    volumes:
      - redis_data:/data
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 10s
      timeout: 3s
      retries: 5

  backend:
    image: resume-io-backend:1.0.0
    container_name: resume-io-backend
    restart: unless-stopped
    environment:
      MYSQL_HOST: mysql
      MYSQL_PORT: "3306"
      MYSQL_DATABASE: resume_db
      MYSQL_USERNAME: root
      MYSQL_PASSWORD: "${MYSQL_PASSWORD}"
      REDIS_HOST: redis
      REDIS_PORT: "6379"
      SENSITIVE_STORAGE: redis
      DEEPSEEK_API_KEY: "${DEEPSEEK_API_KEY}"
      DEEPSEEK_MODEL: "${DEEPSEEK_MODEL:-deepseek-flash}"
      SMS_DEBUG_RETURN_CODE: "true"
      JWT_SECRET: "${JWT_SECRET}"
      APP_DEMO_DATA: "true"
      JAVA_OPTS: "-Xms256m -Xmx512m"
      TZ: Asia/Shanghai
    depends_on:
      mysql:
        condition: service_healthy
      redis:
        condition: service_healthy

  frontend:
    image: resume-io-frontend:1.0.0
    container_name: resume-io-frontend
    restart: unless-stopped
    ports:
      - "127.0.0.1:18080:80"
    depends_on:
      backend:
        condition: service_started

volumes:
  mysql_data:
  redis_data:
EOF
```

**验收**：`cat docker-compose.yml | head -5` 能看到 `name: resume-io`。

---

## 第 6 步（服务器终端）：写 `.env`

```bash
cd /opt/resume-io
cat > .env <<'EOF'
DEEPSEEK_API_KEY=sk-你的真实Key
DEEPSEEK_MODEL=deepseek-flash
JWT_SECRET=这里填一串随机字符串至少32位
MYSQL_PASSWORD=这里填一个强密码
EOF
chmod 600 .env
```

**怎么生成 JWT_SECRET**：`openssl rand -base64 48`，把输出粘进去。

**验收**：

```bash
cat -A .env | head -2
```

行尾应该只有 `$`，**不能出现 `^M$`**（有就说明是 Windows 换行，Key 会多一个隐藏字符，AI 会报 401）。上面用 heredoc 写不会有这个问题。

```bash
docker compose config --quiet && echo "compose 语法 OK"
```

---

## 第 7 步（服务器终端）：载入镜像并启动

```bash
cd /opt/resume-io

# 先解外层 gz（-k 保留原包），再 load 里面的 .tar
# 如果跳过解压、直接 docker load -i xxx.tar.gz，会报：
#   open /var/lib/docker/tmp/.../repositories: no such file or directory
gunzip -k /root/backend-1.0.0.tar.gz
gunzip -k /root/frontend-1.0.0.tar.gz

docker load -i /root/backend-1.0.0.tar
docker load -i /root/frontend-1.0.0.tar
docker images | grep resume-io      # 应看到两个 1.0.0

docker compose up -d
docker compose ps
```

**验收**（三个都要对）：

```bash
docker compose ps                 # 4 个容器，状态都是 Up (healthy)
docker compose logs backend | grep "AI 配置自检"
#   → 应看到：api-key=sk-xxxx****xxxx  model=deepseek-flash
curl -I http://127.0.0.1:18080/   # → HTTP/1.1 200 OK
```

看到 `200 OK` 说明「容器 + 前端 + 反代」这条链已经通了，接下来只是给它套一层 HTTPS。

> 首次启动 MySQL 要建库建表，等 1–2 分钟；`docker compose logs -f backend` 能看进度。

---

## 第 8 步（服务器终端）：生成 IP 自签证书

```bash
mkdir -p /www/server/panel/vhost/cert/ip-demo
cd /www/server/panel/vhost/cert/ip-demo

# 把下面两处 1.2.3.4 换成你的服务器公网 IP（共 2 处，必须一致）
cat > san.cnf <<'EOF'
[req]
distinguished_name = dn
x509_extensions = v3
prompt = no

[dn]
C  = CN
ST = Beijing
L  = Beijing
O  = ResumeIO
CN = 1.2.3.4

[v3]
subjectAltName = IP:1.2.3.4
keyUsage = digitalSignature, keyEncipherment
extendedKeyUsage = serverAuth
EOF

openssl req -x509 -nodes -newkey rsa:2048 -days 3650 \
  -keyout ip.key -out ip.crt -config san.cnf

ls -l ip.crt ip.key
```

**为什么必须写 `subjectAltName = IP:...`**：现代浏览器只认 SAN，看 CN 的时代早过了。只写 CN 会得到 `ERR_CERT_COMMON_NAME_INVALID`，连「继续前往」都不给。

**验收**：

```bash
openssl x509 -in ip.crt -noout -text | grep -A1 "Subject Alternative Name"
# → 必须看到 DNS:... 或 IP Address:1.2.3.4
```

---

## 第 9 步（宝塔面板）：建站点

1. **面板 → 网站 → 添加站点**
   - 域名：`1.2.3.4:8443`（把你的 IP 和你想用的端口填进去；端口别用 8888，那是面板）
   - PHP 版本：**纯静态**
   - 数据库：不创建
   - 提交

   > 如果面板不接受「IP:端口」这种写法，就先只填 IP 建站，下一步再改端口。

2. **进入该站点 → 设置 → SSL**
   - 选择「**其他证书**」
   - 证书（PEM）：粘贴 `cat /www/server/panel/vhost/cert/ip-demo/ip.crt` 的全部内容（含 `-----BEGIN CERTIFICATE-----`）
   - 私钥（KEY）：粘贴 `cat /www/server/panel/vhost/cert/ip-demo/ip.key` 的全部内容
   - 保存

3. **改监听端口**（如果第 1 步没能指定 8443）
   - 站点 → 设置 → **配置文件**
   - 找到 `listen 80;` 改成 `listen 8443 ssl;`
   - 保存

**验收**：配置文件里能看到 `listen 8443 ssl;` 和 `ssl_certificate /www/server/panel/vhost/cert/ip-demo/ip.crt;`。

---

## 第 10 步（宝塔面板）：配反向代理到容器

**站点 → 设置 → 反向代理 → 添加反向代理**

| 字段 | 填什么 |
|---|---|
| 代理名称 | `resume` |
| 目标 URL | `http://127.0.0.1:18080` |
| 发送域名 | `$host` |

保存后，**必须再改一处**，否则 AI 对话/面试点评的流式输出会「卡住到最后一口气吐出来」：

**站点 → 设置 → 配置文件**，在 `server { ... }` 里（任意 `location` 外面）加三行：

```nginx
    proxy_buffering   off;
    proxy_read_timeout 600s;
    proxy_send_timeout 600s;
```

保存并让宝塔重载配置（有的版本会自动 reload）。

**验收**：配置文件里能搜到 `proxy_buffering off;`。

---

## 第 11 步：放行端口（两处都要！）

### 11.1 宝塔防火墙

**面板 → 安全 → 添加端口规则**
- 端口：`8443`　协议：TCP　策略：放行

### 11.2 云厂商安全组

登录云厂商控制台 → 你的实例 → **安全组 / 防火墙** → 添加入方向规则：

| 端口 | 协议 | 来源 | 用途 |
|---|---|---|---|
| 8443 | TCP | `0.0.0.0/0` | 站点 |
| 8888 | TCP | **你的IP/32** | 宝塔面板（强烈建议只对自己的 IP 开） |
| 22 | TCP | 你的IP/32 | SSH |

**注意**：`3306`、`6379`、`8080`、`18080` **一律不要开**。我们的 compose 里也没有把这些映射到公网（前端只绑 `127.0.0.1`），所以外部根本访问不到，这是对的。

**验收**：在你自己电脑上 `telnet 1.2.3.4 8443` 或浏览器直接打开（能连上、有证书警告，就说明端口通了）。

---

## 第 12 步：浏览器验证

1. 打开 `https://1.2.3.4:8443`
2. 出现「您的连接不是私密连接」→ 点「**高级**」→「**继续前往 1.2.3.4（不安全）**」
3. 应该看到项目的首页（AI 简历助手）

**功能自检清单**：

| 检查项 | 操作 | 期望 |
|---|---|---|
| 首页 | 打开 `/` | 显示上传区 |
| 注册 | 打开 `/register`，填手机号 | 点「获取验证码」后，页面上直接显示 6 位验证码（演示模式），填入注册成功并自动登录 |
| 登录 | `/login` 用 `demo / 123456` | 能进（`APP_DEMO_DATA=true` 时存在） |
| AI 分析 | 上传一份 PDF/Word 简历 | 10–60 秒返回评分与建议 |
| 模拟面试 | 开始面试、提交答案 | 有 AI 出题与点评 |
| 刷新不 404 | 在 `/register` 页按 F5 | 页面正常，不报 404 |
| 流式输出 | 触发 AI 对话 | 文字是**一点点出来**的，不是卡住半天一次性出现 |

---

## 第 13 步（可选）：日常维护

```bash
cd /opt/resume-io

# 看状态
docker compose ps

# 看日志
docker compose logs -f backend

# 重启后端（改了 .env 之后）
docker compose up -d backend

# 备份数据库
docker compose exec -T mysql mysqldump -uroot -p"$(grep MYSQL_PASSWORD .env | cut -d= -f2)" resume_db > /root/backup-$(date +%F).sql

# 清空演示数据（演示站会被人传垃圾简历，可挂 crontab 每天跑）
docker compose exec -T mysql mysql -uroot -p"$(grep MYSQL_PASSWORD .env | cut -d= -f2)" -e "
  DELETE FROM resume_db.interview_questions;
  DELETE FROM resume_db.interview_sessions;
  DELETE FROM resume_db.analysis_reports;
  DELETE FROM resume_db.resumes;
  DELETE FROM resume_db.users WHERE id > 1;"

# 停止（保留数据）
docker compose down
```

---

## 附录 A：出错时按这个表查

| 现象 | 在哪看 | 原因 | 怎么办 |
|---|---|---|---|
| `docker load` 报 `open /var/lib/docker/tmp/.../repositories: no such file or directory` | 服务器终端 | 把「两层包」直接喂给了 `docker load`（`.tar.gz` 里只有一个 `.tar`） | `gunzip -k x.tar.gz` 解出 `.tar`，再 `docker load -i x.tar`；验证方法：`tar -tzf x.tar.gz \| head` 只输出一行 `.tar` |
| 浏览器 `无法访问此网站` | 都看 | 端口没放行 | 宝塔防火墙 + 云安全组**两处**都要加 8443 |
| 浏览器显示宝塔默认页 | 宝塔 | 站点没绑到 8443 或反代没生效 | 检查 `listen 8443 ssl;` 和反代目标 |
| 502 Bad Gateway | 宝塔 | 反代目标 `127.0.0.1:18080` 没通 | `curl -I http://127.0.0.1:18080/` 先确认容器在跑 |
| 页面能开但接口全 404 | 浏览器 F12 | 反代只配了 `/`，没转发 `/api` | 反代是**整站**代理，别只代理某个路径 |
| 接口 502 | 宝塔 | 前端容器内的 nginx 找不到 `backend` | `docker compose ps` 看 backend 是否 healthy |
| AI 报 401 | `docker compose logs backend` | Key 错/失效，或 `.env` 带 `^M` | `cat -A .env` 检查行尾；`curl https://api.deepseek.com/models -H "Authorization: Bearer 你的Key"` 验证 |
| `Unable to determine Dialect` | backend 日志 | 根本没连上 MySQL | 等 mysql healthy；确认 `MYSQL_HOST=mysql`（服务名，不是 localhost） |
| 页面空白 | F12 → Network | 静态资源 404 | 确认载入的是 `resume-io-frontend:1.0.0` 而不是旧镜像 |
| AI 输出卡住半天才一次性出现 | 宝塔配置文件 | 没关 Nginx 缓冲 | 加 `proxy_buffering off;` |
| 证书警告点不进去 | 浏览器 | 证书没写 SAN | 重做第 8 步，确认 `subjectAltName = IP:...` |
| 微信里打不开 | — | 微信不接受自签证书 | 无解，只能换域名+真证书或 Cloudflare Tunnel |
| `no space left on device` | `df -h` | 磁盘满 | 删掉 `/root/*.tar`，只留 `.tar.gz` |
| 内存不足 / 卡死 | `free -h`、`dmesg` | 2G 机器上还跑着宝塔 MySQL/PHP | 按第 3 步停掉 |

---

## 附录 B：这套部署长什么样

```
浏览器
  │  https://1.2.3.4:8443   （自签证书，浏览器警告后进入）
  ▼
宝塔 Nginx（宿主）  ── 443/8443 终止 TLS
  │  proxy_pass http://127.0.0.1:18080   （proxy_buffering off）
  ▼
resume-io-frontend 容器（Nginx:80，只绑本机回环）
  │  /api、/ws 反代 → backend:8080（容器网络内，服务名解析）
  ▼
resume-io-backend 容器（Spring Boot:8080，不占宿主端口）
  ├── resume-io-mysql 容器（3306，不占宿主端口）
  └── resume-io-redis 容器（6379，不占宿主端口）
```

**端口只用了一个：8443**。数据库、后端、容器内网全部不外露——这也是为什么不需要在云安全组里开一堆端口。

---

## 附录 C：想彻底去掉证书警告的三条路

| 方案 | 需要 | 成本 | 备注 |
|---|---|---|---|
| 买域名 + 免费证书 | 域名 + **备案**（国内节点） | 域名 ¥30–80/年，备案 1–2 周 | 最正规 |
| 换香港/海外节点 + 域名 | 域名（**免备案**） | 服务器 ¥50–80/月 | 最省心，推荐长期演示 |
| Cloudflare Tunnel | 只需一个域名（或用临时域名） | ¥0 | 从本机/服务器出隧道，自动真证书；临时域名重启会变 |

其中 Cloudflare Tunnel 在服务器上跑也可以，不需要备案：

```bash
# 服务器上
curl -L https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-amd64 -o /usr/local/bin/cloudflared
chmod +x /usr/local/bin/cloudflared
cloudflared tunnel --url http://127.0.0.1:18080
# 输出一个 https://xxxx.trycloudflare.com，真证书、无警告
```

代价是域名是临时的、且依赖这个进程常驻。

---

**版本记录**

| 日期 | 内容 |
|---|---|
| 2026-10-21 | 首版：宝塔面板 + Docker 容器 + IP 自签证书 HTTPS 全流程 |
