<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

// 登录、注册页是整屏卡片布局，不显示顶部导航
const showHeader = computed(() => route.name !== 'login' && route.name !== 'register')
const isLoggedIn = computed(() => userStore.isLoggedIn)

async function handleLogout(): Promise<void> {
  userStore.logout()
  await router.push('/login')
}
</script>

<template>
  <div class="min-h-screen">
    <header
      v-if="showHeader"
      class="border-b border-gray-200 bg-white px-6 py-3 shadow-sm"
    >
      <div class="mx-auto flex max-w-5xl items-center justify-between">
        <router-link to="/" class="text-lg font-semibold text-gray-900">
          AI 简历助手
        </router-link>

        <div class="flex items-center gap-4">
          <router-link
            v-if="!isLoggedIn"
            to="/login"
            class="text-sm text-blue-600 hover:text-blue-700"
          >
            登录
          </router-link>
          <router-link
            v-if="!isLoggedIn"
            to="/register"
            class="text-sm text-blue-600 hover:text-blue-700"
          >
            注册
          </router-link>
          <router-link
            v-else
            to="/profile"
            class="text-sm font-medium text-blue-600 hover:text-blue-700"
          >
            {{ userStore.displayName }}
          </router-link>
          <button
            v-if="isLoggedIn"
            type="button"
            class="text-sm text-gray-500 hover:text-gray-700"
            @click="handleLogout"
          >
            退出登录
          </button>
        </div>
      </div>
    </header>

    <RouterView />
  </div>
</template>
