import axios, {
  type AxiosError,
  type AxiosInstance,
  type AxiosRequestConfig,
  type InternalAxiosRequestConfig,
} from 'axios'
import router from '@/router'
import type { ApiResponse } from '@/types'
import { getToken, redirectToLogin } from '@/utils/auth'

/** 业务成功状态码 */
const SUCCESS_CODES = [200, 0]

/** Token 过期或未授权状态码 */
const UNAUTHORIZED_CODE = 401

/** 解包 ApiResponse.data 后的 HTTP 客户端类型 */
interface HttpClient extends AxiosInstance {
  get<T = unknown>(url: string, config?: AxiosRequestConfig): Promise<T>
  post<T = unknown>(
    url: string,
    data?: unknown,
    config?: AxiosRequestConfig,
  ): Promise<T>
  put<T = unknown>(
    url: string,
    data?: unknown,
    config?: AxiosRequestConfig,
  ): Promise<T>
  delete<T = unknown>(url: string, config?: AxiosRequestConfig): Promise<T>
  patch<T = unknown>(
    url: string,
    data?: unknown,
    config?: AxiosRequestConfig,
  ): Promise<T>
}

const instance = axios.create({
  baseURL: '/api',
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json',
  },
})

/** 处理 Token 过期，跳转登录页 */
function handleUnauthorized(): void {
  redirectToLogin(router.push, router.currentRoute.value.fullPath)
}

/** 判断业务响应是否成功 */
function isSuccessCode(code: number): boolean {
  return SUCCESS_CODES.includes(code)
}

/** 请求拦截：自动注入 JWT Token，FormData 上传时移除 Content-Type */
instance.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const token = getToken()
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    if (config.data instanceof FormData) {
      delete config.headers['Content-Type']
    }
    return config
  },
  (error: AxiosError) => Promise.reject(error),
)

/** 响应拦截：统一处理 { code, message, data } 及 Token 过期 */
instance.interceptors.response.use(
  (response) => {
    const res = response.data as ApiResponse

    if (res.code === UNAUTHORIZED_CODE) {
      handleUnauthorized()
      return Promise.reject(new Error(res.message || '登录已过期，请重新登录'))
    }

    if (!isSuccessCode(res.code)) {
      return Promise.reject(new Error(res.message || '请求失败'))
    }

    // 拦截器解包 data 字段，对外直接返回业务数据
    return res.data as typeof response
  },
  (error: AxiosError<ApiResponse>) => {
    if (error.response?.status === UNAUTHORIZED_CODE) {
      handleUnauthorized()
    }

    const message =
      error.response?.data?.message || error.message || '网络异常，请稍后重试'
    return Promise.reject(new Error(message))
  },
)

const request = instance as HttpClient

export default request
