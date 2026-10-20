import {
  fetchEventSource,
  type EventSourceMessage,
} from '@microsoft/fetch-event-source'
import router from '@/router'
import { getToken, redirectToLogin } from '@/utils/auth'

/** SSE 流式请求配置 */
export interface FetchStreamOptions<TBody = Record<string, unknown>> {
  /** 请求路径，如 /api/interview/stream */
  url: string
  /** POST 请求体 */
  body?: TBody
  /** 额外请求头 */
  headers?: Record<string, string>
  /** 取消信号 */
  signal?: AbortSignal
  /** 收到 SSE 消息回调 */
  onMessage: (event: EventSourceMessage) => void
  /** 错误回调 */
  onError?: (error: unknown) => void
  /** 连接关闭回调 */
  onClose?: () => void
}

/** Token 过期跳转登录 */
function handleUnauthorized(): void {
  redirectToLogin(router.push, router.currentRoute.value.fullPath)
}

/**
 * POST 方式发起 SSE 流式请求，对接 Spring AI text/event-stream 接口
 */
export async function fetchStream<TBody = Record<string, unknown>>(
  options: FetchStreamOptions<TBody>,
): Promise<void> {
  const { url, body, headers, signal, onMessage, onError, onClose } = options
  const token = getToken()

  try {
    await fetchEventSource(url, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Accept: 'text/event-stream',
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        ...headers,
      },
      body: body !== undefined ? JSON.stringify(body) : undefined,
      signal,
      async onopen(response) {
        if (response.ok) {
          return
        }
        if (response.status === 401) {
          handleUnauthorized()
          throw new Error('登录已过期，请重新登录')
        }
        throw new Error(`SSE 连接失败: ${response.status} ${response.statusText}`)
      },
      onmessage: onMessage,
      onerror(err) {
        onError?.(err)
        throw err
      },
      onclose() {
        onClose?.()
      },
    })
  } catch (err) {
    onError?.(err)
    throw err
  }
}
