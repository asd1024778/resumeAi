import { fileURLToPath, URL } from 'node:url'
import { defineConfig, type Plugin } from 'vite'
import vue from '@vitejs/plugin-vue'
import tailwindcss from '@tailwindcss/vite'

// 后端地址：显式用 127.0.0.1，避免 Windows 上 localhost 先解析成 IPv6(::1) 导致代理连不上
const BACKEND = process.env.VITE_BACKEND_ORIGIN || 'http://127.0.0.1:8080'

/**
 * 去掉客户端的 `Expect: 100-continue` 请求头。
 *
 * 原因：Vite 的代理会把该头转给后端（Spring WebFlux / Netty），Netty 会先回复
 * `HTTP/1.1 100 Continue` 再回 200，Node 的 HTTP 解析器此时直接抛
 * "Parse Error: Data after `Connection: close`"，前端收到 502。
 * （浏览器本身不发这个头，但 curl、.NET HttpClient、Postman 等会发，
 *   表现为"同一个接口浏览器能通、脚本却 502"。）
 *
 * 注意：Vite 的 `proxy.configure` / `proxyReq` 钩子对带 expect 的请求根本不会触发，
 * 所以必须在预处理中间件阶段把请求头删掉。
 */
function stripExpect(): Plugin {
  return {
    name: 'resume-io:strip-expect-continue',
    configureServer(server) {
      server.middlewares.use((req, _res, next) => {
        if (req.headers.expect) delete req.headers.expect
        next()
      })
    },
  }
}

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue(), tailwindcss(), stripExpect()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    proxy: {
      '/api': {
        target: BACKEND,
        changeOrigin: true,
      },
      '/ws': {
        target: BACKEND,
        changeOrigin: true,
        ws: true,
      },
    },
  },
})
