/** 登录请求 */
export interface LoginRequest {
  username: string
  password: string
}

/** 注册请求（手机号 + 短信验证码） */
export interface RegisterRequest {
  /** 手机号（必填，11 位大陆手机号） */
  phone: string
  /** 6 位数字验证码 */
  code: string
  /** 密码（不少于 6 位） */
  password: string
  /** 用户名，留空则后端默认使用手机号 */
  username?: string
  /** 邮箱，可选 */
  email?: string
}

/** 发送注册验证码请求 */
export interface SendSmsCodeRequest {
  phone: string
}

/** 发送注册验证码响应 */
export interface SendSmsCodeResponse {
  /** 实际接收验证码的手机号（已规范化） */
  phone: string
  /** 验证码有效期（秒） */
  expiresInSeconds: number
  /** 重新发送冷却时间（秒），用于前端倒计时 */
  resendAfterSeconds: number
  /** 开发期由后端回传的验证码；接入真实短信网关后为 null */
  debugCode: string | null
}

/** 登录响应（注册成功后同样返回该结构，可直接登录） */
export interface LoginResponse {
  token: string
  user: import('./entities').User
}
