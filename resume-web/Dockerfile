# 前端镜像：Node 构建 -> Nginx 静态托管 + /api、/ws 反向代理
# 国内网络可在 compose / .env 通过 ARG 覆盖镜像前缀，例如：
#   NODE_IMAGE=docker.m.daocloud.io/library/node:20-alpine
ARG NODE_IMAGE=node:20-alpine
ARG NGINX_IMAGE=nginx:1.27-alpine

# ========== 阶段 1：前端构建 ==========
FROM ${NODE_IMAGE} AS build
WORKDIR /app

# 可选的 npm 镜像源（默认官方源；国内可传 https://registry.npmmirror.com）
ARG NPM_REGISTRY=
RUN if [ -n "$NPM_REGISTRY" ]; then npm config set registry "$NPM_REGISTRY"; fi

COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund

COPY . .
RUN npm run build

# ========== 阶段 2：Nginx 运行 ==========
FROM ${NGINX_IMAGE}
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
HEALTHCHECK --interval=15s --timeout=5s --start-period=10s --retries=5 \
  CMD wget -q -O /dev/null http://127.0.0.1/ || exit 1
