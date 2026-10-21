import request from './request'
import type {
  LoginRequest,
  LoginResponse,
  RegisterRequest,
  SendSmsCodeRequest,
  SendSmsCodeResponse,
} from '@/types/auth'
import type { UpdateUserProfileRequest } from '@/types/profile'
import type { User } from '@/types'

/** 用户登录 */
export async function login(params: LoginRequest): Promise<LoginResponse> {
  return request.post('/auth/login', params)
}

/** 发送注册短信验证码（后端生成 6 位纯数字） */
export async function sendSmsCode(params: SendSmsCodeRequest): Promise<SendSmsCodeResponse> {
  return request.post('/auth/sms-code', params)
}

/** 手机号注册（验证码校验通过后落库，成功即返回 JWT） */
export async function register(params: RegisterRequest): Promise<LoginResponse> {
  return request.post('/auth/register', params)
}

/** 获取当前登录用户（脱敏） */
export async function getCurrentUser(): Promise<User> {
  return request.get('/auth/me')
}

/** 获取当前用户完整敏感资料（仅本人） */
export async function getFullProfile(): Promise<User> {
  return request.get('/auth/me/profile/full')
}

/** 更新敏感资料，响应仍为脱敏数据 */
export async function updateProfile(params: UpdateUserProfileRequest): Promise<User> {
  return request.put('/auth/me/profile', params)
}
