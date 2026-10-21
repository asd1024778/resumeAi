<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { formatError } from '@/utils/formatError'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const username = ref('')
const password = ref('')
const loading = ref(false)
const error = ref<string | null>(null)

async function handleLogin(): Promise<void> {
  if (!username.value.trim() || !password.value) {
    error.value = '请输入用户名和密码'
    return
  }

  loading.value = true
  error.value = null

  try {
    await userStore.login({
      username: username.value.trim(),
      password: password.value,
    })
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/'
    await router.replace(redirect)
  } catch (err) {
    error.value = formatError(err)
  } finally {
    loading.value = false
  }
}

function handleKeydown(event: KeyboardEvent): void {
  if (event.key === 'Enter' && !loading.value) {
    void handleLogin()
  }
}
</script>

<template>
  <div class="flex min-h-screen items-center justify-center bg-gray-50 px-4">
    <div class="w-full max-w-md rounded-2xl border border-gray-200 bg-white p-8 shadow-sm">
      <h1 class="mb-2 text-center text-2xl font-bold text-gray-900">登录</h1>
      <p class="mb-6 text-center text-sm text-gray-500">AI 简历分析与模拟面试</p>

      <form class="space-y-4" @submit.prevent="handleLogin">
        <div>
          <label class="mb-1 block text-sm font-medium text-gray-700">用户名</label>
          <input
            v-model="username"
            type="text"
            autocomplete="username"
            placeholder="请输入用户名"
            class="w-full rounded-lg border border-gray-300 px-4 py-2.5 text-sm outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100"
            :disabled="loading"
            @keydown="handleKeydown"
          />
        </div>

        <div>
          <label class="mb-1 block text-sm font-medium text-gray-700">密码</label>
          <input
            v-model="password"
            type="password"
            autocomplete="current-password"
            placeholder="请输入密码"
            class="w-full rounded-lg border border-gray-300 px-4 py-2.5 text-sm outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100"
            :disabled="loading"
            @keydown="handleKeydown"
          />
        </div>

        <div
          v-if="error"
          class="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-600"
        >
          {{ error }}
        </div>

        <button
          type="submit"
          class="w-full rounded-lg bg-blue-600 py-2.5 text-sm font-medium text-white hover:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-50"
          :disabled="loading"
        >
          {{ loading ? '登录中...' : '登录' }}
        </button>
      </form>

      <p class="mt-4 text-center text-xs text-gray-500">
        还没有账号？
        <router-link to="/register" class="text-blue-600 hover:text-blue-700">
          手机号注册
        </router-link>
      </p>

      <p class="mt-2 text-center text-xs text-gray-400">
        演示账号：demo / 123456
      </p>
    </div>
  </div>
</template>
