<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { sendSmsCode } from '@/api/auth'
import { useUserStore } from '@/stores/user'
import { formatError } from '@/utils/formatError'

/** 中国大陆手机号：1 开头 + 第二位 3~9 + 后 9 位数字（与后端 PhoneUtils 规则一致） */
const PHONE_PATTERN = /^1[3-9]\d{9}$/
/** 验证码：后端固定 6 位纯数字 */
const CODE_PATTERN = /^\d{6}$/

/** 可做字段级校验的表单字段 */
type FieldKey = 'phone' | 'code' | 'username' | 'password' | 'confirmPassword'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

/** 表单数据；用户名留空则后端默认使用手机号 */
const form = reactive({
  phone: '',
  code: '',
  username: '',
  password: '',
  confirmPassword: '',
})

/** 字段级错误提示（失焦/提交时填充，不打断输入） */
const fieldErrors = reactive<Record<FieldKey, string>>({
  phone: '',
  code: '',
  username: '',
  password: '',
  confirmPassword: '',
})

/** 提交中（避免重复提交） */
const submitting = ref(false)
/** 验证码发送中 */
const sending = ref(false)
/** 顶部整体错误（来自后端业务错误） */
const formError = ref<string | null>(null)
/** 重新发送倒计时（秒） */
const countdown = ref(0)
/** 开发期后端回传的验证码，仅用于联调提示 */
const devCode = ref<string | null>(null)

/** 倒计时定时器句柄，组件卸载时务必清理 */
let countdownTimer: ReturnType<typeof setInterval> | undefined

/** 手机号合法且不在倒计时中，才允许点「获取验证码」 */
const canSendCode = computed(
  () => !sending.value && countdown.value === 0 && PHONE_PATTERN.test(form.phone),
)

/** 清空所有字段级错误 */
function clearFieldErrors(): void {
  fieldErrors.phone = ''
  fieldErrors.code = ''
  fieldErrors.username = ''
  fieldErrors.password = ''
  fieldErrors.confirmPassword = ''
}

/**
 * 前端校验（后端仍会再校验一次，前端这层只为即时反馈）。
 * @returns 全部通过返回 true
 */
function validate(): boolean {
  clearFieldErrors()

  if (!PHONE_PATTERN.test(form.phone)) {
    fieldErrors.phone = '请输入正确的 11 位手机号'
  }
  if (!CODE_PATTERN.test(form.code)) {
    fieldErrors.code = '请输入 6 位数字验证码'
  }
  if (form.username.trim().length > 64) {
    fieldErrors.username = '用户名不能超过 64 个字符'
  }
  if (form.password.length < 6) {
    fieldErrors.password = '密码长度不能少于 6 位'
  } else if (form.password.length > 64) {
    fieldErrors.password = '密码长度不能超过 64 位'
  }
  if (!form.confirmPassword) {
    fieldErrors.confirmPassword = '请再次输入密码'
  } else if (form.confirmPassword !== form.password) {
    fieldErrors.confirmPassword = '两次输入的密码不一致'
  }

  return !Object.values(fieldErrors).some(Boolean)
}

/** 手机号输入框：只保留数字并限制 11 位 */
function handlePhoneInput(): void {
  form.phone = form.phone.replace(/\D/g, '').slice(0, 11)
  if (fieldErrors.phone) {
    fieldErrors.phone = ''
  }
}

/** 手机号失焦时即时校验，让用户尽早发现格式问题 */
function handlePhoneBlur(): void {
  if (form.phone && !PHONE_PATTERN.test(form.phone)) {
    fieldErrors.phone = '请输入正确的 11 位手机号'
  }
}

/** 验证码输入框：只保留数字并限制 6 位 */
function handleCodeInput(): void {
  form.code = form.code.replace(/\D/g, '').slice(0, 6)
  if (fieldErrors.code) {
    fieldErrors.code = ''
  }
}

/** 启动重发倒计时 */
function startCountdown(seconds: number): void {
  stopCountdown()
  countdown.value = Number.isFinite(seconds) && seconds > 0 ? Math.floor(seconds) : 60
  countdownTimer = setInterval(() => {
    countdown.value -= 1
    if (countdown.value <= 0) {
      stopCountdown()
    }
  }, 1000)
}

/** 停止并重置倒计时 */
function stopCountdown(): void {
  if (countdownTimer !== undefined) {
    clearInterval(countdownTimer)
    countdownTimer = undefined
  }
  if (countdown.value < 0) {
    countdown.value = 0
  }
}

/** 点击「获取验证码」：校验手机号 → 请求后端生成 6 位验证码 */
async function handleSendCode(): Promise<void> {
  formError.value = null
  if (!PHONE_PATTERN.test(form.phone)) {
    fieldErrors.phone = '请输入正确的 11 位手机号'
    return
  }

  sending.value = true
  try {
    const result = await sendSmsCode({ phone: form.phone })
    devCode.value = result.debugCode
    // 手机号被规范化（去掉 +86 / 分隔符）后回填，保证与后端一致
    form.phone = result.phone
    startCountdown(result.resendAfterSeconds)
    ElMessage.success(
      result.debugCode
        ? `验证码已生成（开发模式：${result.debugCode}）`
        : '验证码已发送，请注意查收',
    )
  } catch (err) {
    // 后端可能返回「发送过于频繁，请 xx 秒后再试」，此时同步倒计时更友好
    formError.value = formatError(err)
    const match = /请 (\d+) 秒后再试/.exec(formError.value)
    if (match) {
      startCountdown(Number(match[1]))
    }
  } finally {
    sending.value = false
  }
}

/** 提交注册 */
async function handleRegister(): Promise<void> {
  formError.value = null
  if (!validate()) {
    return
  }

  submitting.value = true
  try {
    await userStore.register({
      phone: form.phone,
      code: form.code,
      password: form.password,
      username: form.username.trim() || undefined,
    })
    ElMessage.success('注册成功，已自动登录')
    // 注册成功即拿到 JWT，回到来源页或首页
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/'
    await router.replace(redirect)
  } catch (err) {
    formError.value = formatError(err)
  } finally {
    submitting.value = false
  }
}

// 组件卸载时清理定时器，防止内存泄漏
onBeforeUnmount(stopCountdown)
</script>

<template>
  <div class="flex min-h-screen items-center justify-center bg-gray-50 px-4 py-10">
    <div class="w-full max-w-md rounded-2xl border border-gray-200 bg-white p-8 shadow-sm">
      <h1 class="mb-2 text-center text-2xl font-bold text-gray-900">注册</h1>
      <p class="mb-6 text-center text-sm text-gray-500">手机号验证码注册，注册后自动登录</p>

      <form class="space-y-4" @submit.prevent="handleRegister">
        <!-- 手机号 -->
        <div>
          <label class="mb-1 block text-sm font-medium text-gray-700">手机号</label>
          <input
            v-model="form.phone"
            type="tel"
            inputmode="numeric"
            maxlength="11"
            autocomplete="tel"
            placeholder="请输入 11 位手机号"
            class="w-full rounded-lg border border-gray-300 px-4 py-2.5 text-sm outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100"
            :class="{ 'border-red-300': fieldErrors.phone }"
            :disabled="submitting"
            @input="handlePhoneInput"
            @blur="handlePhoneBlur"
          />
          <p v-if="fieldErrors.phone" class="mt-1 text-xs text-red-500">
            {{ fieldErrors.phone }}
          </p>
        </div>

        <!-- 验证码 + 获取按钮 -->
        <div>
          <label class="mb-1 block text-sm font-medium text-gray-700">验证码</label>
          <div class="flex gap-2">
            <input
              v-model="form.code"
              type="text"
              inputmode="numeric"
              maxlength="6"
              autocomplete="one-time-code"
              placeholder="6 位数字验证码"
              class="w-full rounded-lg border border-gray-300 px-4 py-2.5 text-sm tracking-widest outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100"
              :class="{ 'border-red-300': fieldErrors.code }"
              :disabled="submitting"
              @input="handleCodeInput"
            />
            <button
              type="button"
              class="shrink-0 whitespace-nowrap rounded-lg border border-blue-600 px-4 py-2.5 text-sm font-medium text-blue-600 transition hover:bg-blue-50 disabled:cursor-not-allowed disabled:border-gray-300 disabled:text-gray-400 disabled:hover:bg-transparent"
              :disabled="!canSendCode || submitting"
              @click="handleSendCode"
            >
              {{ sending ? '发送中...' : countdown > 0 ? `${countdown} 秒后重发` : '获取验证码' }}
            </button>
          </div>
          <p v-if="fieldErrors.code" class="mt-1 text-xs text-red-500">
            {{ fieldErrors.code }}
          </p>
        </div>

        <!-- 开发模式验证码提示（后端 app.sms.debug-return-code=true 时才有值） -->
        <div
          v-if="devCode"
          class="rounded-lg border border-amber-200 bg-amber-50 px-4 py-2.5 text-xs text-amber-700"
        >
          开发模式：本次验证码为
          <span class="font-mono text-sm font-semibold tracking-widest">{{ devCode }}</span>
          （未接入真实短信网关，生产环境不会回传）
        </div>

        <!-- 用户名（可选） -->
        <div>
          <label class="mb-1 block text-sm font-medium text-gray-700">
            用户名
            <span class="font-normal text-gray-400">（可选）</span>
          </label>
          <input
            v-model="form.username"
            type="text"
            autocomplete="username"
            placeholder="留空则默认使用手机号"
            class="w-full rounded-lg border border-gray-300 px-4 py-2.5 text-sm outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100"
            :class="{ 'border-red-300': fieldErrors.username }"
            :disabled="submitting"
          />
          <p v-if="fieldErrors.username" class="mt-1 text-xs text-red-500">
            {{ fieldErrors.username }}
          </p>
        </div>

        <!-- 密码 -->
        <div>
          <label class="mb-1 block text-sm font-medium text-gray-700">密码</label>
          <input
            v-model="form.password"
            type="password"
            autocomplete="new-password"
            placeholder="不少于 6 位"
            class="w-full rounded-lg border border-gray-300 px-4 py-2.5 text-sm outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100"
            :class="{ 'border-red-300': fieldErrors.password }"
            :disabled="submitting"
          />
          <p v-if="fieldErrors.password" class="mt-1 text-xs text-red-500">
            {{ fieldErrors.password }}
          </p>
        </div>

        <!-- 确认密码 -->
        <div>
          <label class="mb-1 block text-sm font-medium text-gray-700">确认密码</label>
          <input
            v-model="form.confirmPassword"
            type="password"
            autocomplete="new-password"
            placeholder="请再次输入密码"
            class="w-full rounded-lg border border-gray-300 px-4 py-2.5 text-sm outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100"
            :class="{ 'border-red-300': fieldErrors.confirmPassword }"
            :disabled="submitting"
          />
          <p v-if="fieldErrors.confirmPassword" class="mt-1 text-xs text-red-500">
            {{ fieldErrors.confirmPassword }}
          </p>
        </div>

        <!-- 后端业务错误 -->
        <div
          v-if="formError"
          class="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-600"
        >
          {{ formError }}
        </div>

        <button
          type="submit"
          class="w-full rounded-lg bg-blue-600 py-2.5 text-sm font-medium text-white hover:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-50"
          :disabled="submitting"
        >
          {{ submitting ? '注册中...' : '注册' }}
        </button>
      </form>

      <p class="mt-4 text-center text-xs text-gray-500">
        已有账号？
        <router-link to="/login" class="text-blue-600 hover:text-blue-700">
          去登录
        </router-link>
      </p>
    </div>
  </div>
</template>
