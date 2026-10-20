/** localStorage 中 JWT Token 的键名 */
export const TOKEN_KEY = 'access_token'

/** 读取 JWT Token */
export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY)
}

/** 写入 JWT Token */
export function setToken(token: string): void {
  localStorage.setItem(TOKEN_KEY, token)
}

/** 清除 JWT Token */
export function clearToken(): void {
  localStorage.removeItem(TOKEN_KEY)
}

/** 跳转登录页（Token 过期时调用，需传入 router 实例避免循环依赖） */
export function redirectToLogin(
  push: (location: { path: string; query?: Record<string, string> }) => void,
  currentPath: string,
): void {
  clearToken()
  if (currentPath !== '/login') {
    push({
      path: '/login',
      query: currentPath !== '/login' ? { redirect: currentPath } : undefined,
    })
  }
}
