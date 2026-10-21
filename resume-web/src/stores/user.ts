import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { getCurrentUser, login as loginApi, register as registerApi } from '@/api/auth'
import type { LoginRequest, RegisterRequest } from '@/types/auth'
import type { User } from '@/types'
import { clearToken, getToken, setToken } from '@/utils/auth'

export const useUserStore = defineStore('user', () => {
  const user = ref<User | null>(null)
  /** 与 localStorage 同步，保证登录后界面能响应式更新 */
  const token = ref<string | null>(getToken())
  const initialized = ref(false)

  const isLoggedIn = computed(() => !!token.value && !!user.value)

  const displayName = computed(() => user.value?.username ?? '')

  async function fetchUser(): Promise<void> {
    token.value = getToken()
    if (!token.value) {
      user.value = null
      initialized.value = true
      return
    }
    try {
      user.value = await getCurrentUser()
    } catch {
      clearToken()
      token.value = null
      user.value = null
    } finally {
      initialized.value = true
    }
  }

  async function login(params: LoginRequest): Promise<void> {
    const result = await loginApi(params)
    setToken(result.token)
    token.value = result.token
    user.value = result.user
  }

  /**
   * 手机号注册：后端校验验证码通过后返回 JWT，这里直接落 token 完成自动登录。
   */
  async function register(params: RegisterRequest): Promise<void> {
    const result = await registerApi(params)
    setToken(result.token)
    token.value = result.token
    user.value = result.user
  }

  function logout(): void {
    clearToken()
    token.value = null
    user.value = null
  }

  return {
    user,
    token,
    initialized,
    isLoggedIn,
    displayName,
    fetchUser,
    login,
    register,
    logout,
  }
})
