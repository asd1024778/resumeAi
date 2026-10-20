/** 通用 API 响应结构 */
export interface ApiResponse<T = unknown> {
  code: number
  message: string
  data: T
}
