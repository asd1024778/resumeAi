/** 登录请求 */
export interface LoginRequest {
  username: string
  password: string
}

/** 注册请求 */
export interface RegisterRequest {
  username: string
  password: string
  email?: string
}

/** 登录响应 */
export interface LoginResponse {
  token: string
  user: import('./entities').User
}
