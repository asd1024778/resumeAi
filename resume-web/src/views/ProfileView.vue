<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getCurrentUser, getFullProfile, updateProfile } from '@/api/auth'
import { useUserStore } from '@/stores/user'
import { formatError } from '@/utils/formatError'
import type { UpdateUserProfileRequest } from '@/types/profile'
import type { User, UserSensitiveProfile } from '@/types'

type ViewMode = 'masked' | 'full' | 'edit'

const userStore = useUserStore()

const loading = ref(false)
const saving = ref(false)
const viewMode = ref<ViewMode>('masked')
const maskedUser = ref<User | null>(null)
const fullUser = ref<User | null>(null)

const form = reactive<UpdateUserProfileRequest>({
  realName: '',
  phone: '',
  idCard: '',
  address: '',
  socialAccount: '',
})

const displayUser = computed(() => {
  if (viewMode.value === 'masked') {
    return maskedUser.value
  }
  return fullUser.value ?? maskedUser.value
})

const profileFields: Array<{ key: keyof UserSensitiveProfile; label: string; placeholder: string }> = [
  { key: 'realName', label: '真实姓名', placeholder: '请输入真实姓名' },
  { key: 'phone', label: '手机号码', placeholder: '请输入手机号' },
  { key: 'idCard', label: '身份证号', placeholder: '请输入身份证号' },
  { key: 'address', label: '现实地址', placeholder: '请输入详细地址' },
  { key: 'socialAccount', label: '社交媒体账号', placeholder: '如微信号、GitHub 等' },
]

function emptyText(value?: string): string {
  return value?.trim() ? value : '未填写'
}

async function loadMaskedProfile(): Promise<void> {
  loading.value = true
  try {
    maskedUser.value = await getCurrentUser()
    userStore.user = maskedUser.value
  } catch (err) {
    ElMessage.error(formatError(err))
  } finally {
    loading.value = false
  }
}

async function showFullProfile(): Promise<void> {
  loading.value = true
  try {
    fullUser.value = await getFullProfile()
    viewMode.value = 'full'
    ElMessage.success('已加载完整资料，请注意保护隐私')
  } catch (err) {
    ElMessage.error(formatError(err))
  } finally {
    loading.value = false
  }
}

async function enterEditMode(): Promise<void> {
  loading.value = true
  try {
    if (!fullUser.value) {
      fullUser.value = await getFullProfile()
    }
    const profile = fullUser.value.profile ?? {}
    form.realName = profile.realName ?? ''
    form.phone = profile.phone ?? ''
    form.idCard = profile.idCard ?? ''
    form.address = profile.address ?? ''
    form.socialAccount = profile.socialAccount ?? ''
    viewMode.value = 'edit'
  } catch (err) {
    ElMessage.error(formatError(err))
  } finally {
    loading.value = false
  }
}

function cancelEdit(): void {
  viewMode.value = fullUser.value ? 'full' : 'masked'
}

async function handleSave(): Promise<void> {
  saving.value = true
  try {
    maskedUser.value = await updateProfile({ ...form })
    userStore.user = maskedUser.value
    fullUser.value = null
    viewMode.value = 'masked'
    ElMessage.success('资料已保存，对外展示将自动脱敏')
  } catch (err) {
    ElMessage.error(formatError(err))
  } finally {
    saving.value = false
  }
}

function backToMasked(): void {
  viewMode.value = 'masked'
}

onMounted(() => {
  void loadMaskedProfile()
})
</script>

<template>
  <div class="mx-auto max-w-3xl px-4 py-8">
    <div class="mb-6 flex flex-wrap items-center justify-between gap-3">
      <div>
        <h1 class="text-2xl font-bold text-gray-900">个人中心</h1>
        <p class="mt-1 text-sm text-gray-500">查看与管理您的账号及敏感资料</p>
      </div>
      <span
        v-if="viewMode === 'masked'"
        class="rounded-full bg-amber-50 px-3 py-1 text-xs font-medium text-amber-700 ring-1 ring-amber-200"
      >
        敏感信息已脱敏展示
      </span>
      <span
        v-else-if="viewMode === 'full'"
        class="rounded-full bg-blue-50 px-3 py-1 text-xs font-medium text-blue-700 ring-1 ring-blue-200"
      >
        完整资料（仅本人可见）
      </span>
      <span
        v-else
        class="rounded-full bg-green-50 px-3 py-1 text-xs font-medium text-green-700 ring-1 ring-green-200"
      >
        编辑模式
      </span>
    </div>

    <div v-if="loading && !displayUser" class="rounded-2xl border border-gray-200 bg-white p-10 text-center text-gray-500">
      加载中...
    </div>

    <template v-else-if="displayUser">
      <!-- 基础信息 -->
      <section class="mb-6 rounded-2xl border border-gray-200 bg-white p-6 shadow-sm">
        <h2 class="mb-4 text-lg font-semibold text-gray-900">账号信息</h2>
        <dl class="grid gap-4 sm:grid-cols-2">
          <div>
            <dt class="text-sm text-gray-500">用户名</dt>
            <dd class="mt-1 font-medium text-gray-900">{{ displayUser.username }}</dd>
          </div>
          <div>
            <dt class="text-sm text-gray-500">邮箱</dt>
            <dd class="mt-1 font-medium text-gray-900">{{ emptyText(displayUser.email) }}</dd>
          </div>
        </dl>
      </section>

      <!-- 脱敏 / 完整 展示 -->
      <section
        v-if="viewMode !== 'edit'"
        class="mb-6 rounded-2xl border border-gray-200 bg-white p-6 shadow-sm"
      >
        <h2 class="mb-4 text-lg font-semibold text-gray-900">敏感资料</h2>
        <dl class="grid gap-4">
          <div
            v-for="field in profileFields"
            :key="field.key"
            class="border-b border-gray-100 pb-4 last:border-0 last:pb-0"
          >
            <dt class="text-sm text-gray-500">{{ field.label }}</dt>
            <dd class="mt-1 font-medium text-gray-900">
              {{ emptyText(displayUser.profile?.[field.key]) }}
            </dd>
          </div>
        </dl>

        <p v-if="viewMode === 'masked'" class="mt-4 text-xs text-gray-400">
          以上信息经后端脱敏处理。如需查看或修改完整内容，请使用下方按钮。
        </p>
      </section>

      <!-- 编辑表单 -->
      <section
        v-else
        class="mb-6 rounded-2xl border border-gray-200 bg-white p-6 shadow-sm"
      >
        <h2 class="mb-4 text-lg font-semibold text-gray-900">编辑敏感资料</h2>
        <form class="space-y-4" @submit.prevent="handleSave">
          <div v-for="field in profileFields" :key="field.key">
            <label class="mb-1 block text-sm font-medium text-gray-700">{{ field.label }}</label>
            <input
              v-model="form[field.key]"
              type="text"
              :placeholder="field.placeholder"
              class="w-full rounded-lg border border-gray-300 px-4 py-2.5 text-sm outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100"
              :disabled="saving"
            />
          </div>

          <div class="flex flex-wrap gap-3 pt-2">
            <button
              type="submit"
              class="rounded-lg bg-blue-600 px-5 py-2.5 text-sm font-medium text-white hover:bg-blue-700 disabled:opacity-50"
              :disabled="saving"
            >
              {{ saving ? '保存中...' : '保存' }}
            </button>
            <button
              type="button"
              class="rounded-lg border border-gray-300 px-5 py-2.5 text-sm text-gray-700 hover:bg-gray-50"
              :disabled="saving"
              @click="cancelEdit"
            >
              取消
            </button>
          </div>
        </form>
      </section>

      <!-- 操作按钮 -->
      <div v-if="viewMode !== 'edit'" class="flex flex-wrap gap-3">
        <button
          v-if="viewMode === 'masked'"
          type="button"
          class="rounded-lg border border-gray-300 px-5 py-2.5 text-sm text-gray-700 hover:bg-gray-50 disabled:opacity-50"
          :disabled="loading"
          @click="showFullProfile"
        >
          查看完整资料
        </button>
        <button
          type="button"
          class="rounded-lg bg-blue-600 px-5 py-2.5 text-sm font-medium text-white hover:bg-blue-700 disabled:opacity-50"
          :disabled="loading"
          @click="enterEditMode"
        >
          编辑资料
        </button>
        <button
          v-if="viewMode === 'full'"
          type="button"
          class="rounded-lg border border-gray-300 px-5 py-2.5 text-sm text-gray-700 hover:bg-gray-50"
          @click="backToMasked"
        >
          返回脱敏视图
        </button>
      </div>
    </template>
  </div>
</template>
