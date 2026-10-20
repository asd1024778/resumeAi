<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { Microphone, Promotion } from '@element-plus/icons-vue'
import { submitAnswer } from '@/api/interview'
import { useInterviewStore } from '@/stores/interview'
import { fetchStream } from '@/utils/fetchStream'
import { formatError } from '@/utils/formatError'
import { renderMarkdown } from '@/utils/markdown'
import { parseSseChunk } from '@/utils/sse'
import { VoiceSocket } from '@/utils/voiceSocket'
import { WavRecorder } from '@/utils/wavRecorder'

/** 聊天消息（组件内展示用） */
interface ChatMessage {
  id: string
  role: 'user' | 'assistant'
  content: string
  streaming: boolean
}

const router = useRouter()
const interviewStore = useInterviewStore()

const messages = ref<ChatMessage[]>([])
const inputText = ref('')
const loading = ref(false)
const streaming = ref(false)
const error = ref<string | null>(null)
const isRecording = ref(false)

const messageListRef = ref<HTMLElement | null>(null)
const sseAbortController = ref<AbortController | null>(null)
const wavRecorder = ref<WavRecorder | null>(null)
const voiceSocket = ref<VoiceSocket | null>(null)

const headerTitle = computed(() => {
  const parts = [interviewStore.jobTitle, interviewStore.resumeTitle].filter(Boolean)
  return parts.join(' · ') || '面试进行中'
})

const canSend = computed(
  () =>
    !loading.value &&
    !streaming.value &&
    !isRecording.value &&
    inputText.value.trim().length > 0 &&
    interviewStore.sessionId !== null &&
    interviewStore.currentQuestionId !== null,
)

function createMessageId(): string {
  return `${Date.now()}-${Math.random().toString(36).slice(2, 9)}`
}

function getMessageHtml(content: string): string {
  return renderMarkdown(content)
}

async function scrollToBottom(): Promise<void> {
  await nextTick()
  const el = messageListRef.value
  if (el) {
    el.scrollTop = el.scrollHeight
  }
}

function appendAssistantMessage(content: string, streamingMsg = false): ChatMessage {
  const msg: ChatMessage = {
    id: createMessageId(),
    role: 'assistant',
    content,
    streaming: streamingMsg,
  }
  messages.value.push(msg)
  return msg
}

function appendUserMessage(content: string): void {
  messages.value.push({
    id: createMessageId(),
    role: 'user',
    content,
    streaming: false,
  })
}

/** 通过 SSE 流式接收 AI 回答，逐字追加并实时 Markdown 渲染 */
async function streamAiResponse(
  sessionId: number,
  questionId: number,
  answer: string,
): Promise<void> {
  const assistantMsg = appendAssistantMessage('', true)
  streaming.value = true
  error.value = null

  sseAbortController.value?.abort()
  sseAbortController.value = new AbortController()

  try {
    await fetchStream({
      url: '/api/interview/stream',
      body: { sessionId, questionId, answer },
      signal: sseAbortController.value.signal,
      onMessage: (event) => {
        const chunk = parseSseChunk(event.data)
        if (chunk) {
          assistantMsg.content += chunk
          void scrollToBottom()
        }
      },
      onError: (err) => {
        if (sseAbortController.value?.signal.aborted) {
          return
        }
        error.value = formatError(err)
      },
      onClose: async () => {
        assistantMsg.streaming = false
        streaming.value = false
        await scrollToBottom()
      },
    })
  } catch (err) {
    if (!sseAbortController.value?.signal.aborted) {
      error.value = formatError(err)
    }
    assistantMsg.streaming = false
    streaming.value = false
  }
}

async function handleSend(): Promise<void> {
  const answer = inputText.value.trim()
  const sessionId = interviewStore.sessionId
  const questionId = interviewStore.currentQuestionId

  if (!answer || sessionId === null || questionId === null) {
    return
  }

  loading.value = true
  error.value = null
  appendUserMessage(answer)
  inputText.value = ''
  await scrollToBottom()

  try {
    const result = await submitAnswer({ sessionId, questionId, answer })

    if (result.nextQuestion) {
      interviewStore.addQuestion(result.nextQuestion)
      appendAssistantMessage(`**下一题：** ${result.nextQuestion.content}`)
    }

    await streamAiResponse(sessionId, questionId, answer)
  } catch (err) {
    error.value = formatError(err)
  } finally {
    loading.value = false
  }
}

function handleKeydown(event: KeyboardEvent): void {
  if (event.key === 'Enter' && !event.shiftKey) {
    event.preventDefault()
    if (canSend.value) {
      void handleSend()
    }
  }
}

async function startRecording(): Promise<void> {
  if (isRecording.value || streaming.value) {
    return
  }

  loading.value = true
  error.value = null

  try {
    voiceSocket.value = new VoiceSocket()
    await voiceSocket.value.connect({
      onAudio: () => {
        /* TTS 播放由 VoiceSocket 内部串行处理 */
      },
      onError: (err) => {
        error.value = formatError(err)
      },
      onClose: () => {
        isRecording.value = false
      },
    })

    wavRecorder.value = new WavRecorder()
    await wavRecorder.value.start({
      onChunk: (pcmBuffer) => {
        voiceSocket.value?.send(pcmBuffer)
      },
      onError: (err) => {
        error.value = formatError(err)
      },
    })

    isRecording.value = true
  } catch (err) {
    error.value = formatError(err)
    stopRecording()
  } finally {
    loading.value = false
  }
}

function stopRecording(): void {
  wavRecorder.value?.stop()
  wavRecorder.value = null
  voiceSocket.value?.close()
  voiceSocket.value = null
  isRecording.value = false
}

async function toggleMicrophone(): Promise<void> {
  if (isRecording.value) {
    stopRecording()
    return
  }
  await startRecording()
}

function cleanupConnections(): void {
  sseAbortController.value?.abort()
  sseAbortController.value = null
  stopRecording()
  interviewStore.stopTimer()
}

function initMessagesFromStore(): void {
  const question = interviewStore.currentQuestion
  if (question) {
    appendAssistantMessage(`**当前题目：** ${question.content}`)
  }
}

onMounted(() => {
  if (interviewStore.sessionId === null) {
    error.value = '未找到面试会话，请从首页重新开始'
    return
  }
  initMessagesFromStore()
  void scrollToBottom()
})

onUnmounted(() => {
  cleanupConnections()
})

watch(
  () => messages.value.length,
  () => {
    void scrollToBottom()
  },
)
</script>

<template>
  <div class="flex h-screen flex-col bg-gray-50">
    <!-- 顶部：岗位/简历名称 + 倒计时 -->
    <header
      class="flex shrink-0 items-center justify-between border-b border-gray-200 bg-white px-6 py-4 shadow-sm"
    >
      <div>
        <h1 class="text-lg font-semibold text-gray-900">{{ headerTitle }}</h1>
        <p v-if="interviewStore.currentQuestion" class="mt-1 text-sm text-gray-500">
          第 {{ interviewStore.questions.findIndex((q) => q.id === interviewStore.currentQuestionId) + 1 }}
          / {{ interviewStore.questions.length }} 题
        </p>
      </div>
      <div class="flex items-center gap-4">
        <span
          class="rounded-full px-3 py-1 text-sm font-medium"
          :class="
            interviewStore.remainingTime <= 60
              ? 'bg-red-100 text-red-600'
              : 'bg-blue-100 text-blue-600'
          "
        >
          剩余 {{ interviewStore.formattedRemainingTime }}
        </span>
        <button
          type="button"
          class="text-sm text-gray-500 hover:text-gray-700"
          @click="router.push('/')"
        >
          退出
        </button>
      </div>
    </header>

    <!-- 错误提示 -->
    <div
      v-if="error"
      class="mx-4 mt-3 rounded-lg border border-red-200 bg-red-50 px-4 py-2 text-sm text-red-600"
    >
      {{ error }}
    </div>

    <!-- 中间：滚动消息列表 -->
    <main ref="messageListRef" class="flex-1 overflow-y-auto px-4 py-6">
      <div v-if="interviewStore.sessionId === null" class="flex h-full items-center justify-center">
        <p class="text-gray-400">暂无面试会话</p>
      </div>

      <ul v-else class="mx-auto flex max-w-3xl flex-col gap-4">
        <li
          v-for="msg in messages"
          :key="msg.id"
          class="flex"
          :class="msg.role === 'user' ? 'justify-end' : 'justify-start'"
        >
          <div
            class="max-w-[80%] rounded-2xl px-4 py-3 text-sm leading-relaxed shadow-sm"
            :class="
              msg.role === 'user'
                ? 'bg-blue-600 text-white'
                : 'border border-gray-200 bg-white text-gray-800'
            "
          >
            <!-- 用户消息：纯文本 -->
            <p v-if="msg.role === 'user'" class="whitespace-pre-wrap">{{ msg.content }}</p>

            <!-- AI 消息：Markdown 实时渲染 + 打字机光标 -->
            <div v-else class="markdown-body">
              <div v-html="getMessageHtml(msg.content)" />
              <span
                v-if="msg.streaming"
                class="ml-0.5 inline-block h-4 w-0.5 animate-pulse bg-blue-500 align-middle"
              />
            </div>
          </div>
        </li>

        <!-- 加载态 -->
        <li v-if="loading && !streaming" class="flex justify-start">
          <div class="rounded-2xl border border-gray-200 bg-white px-4 py-3 text-sm text-gray-400">
            思考中...
          </div>
        </li>
      </ul>
    </main>

    <!-- 底部：输入区 -->
    <footer class="shrink-0 border-t border-gray-200 bg-white px-4 py-4">
      <div class="mx-auto flex max-w-3xl items-end gap-3">
        <textarea
          v-model="inputText"
          rows="2"
          placeholder="输入你的回答，Enter 发送，Shift+Enter 换行"
          class="flex-1 resize-none rounded-xl border border-gray-300 px-4 py-3 text-sm outline-none transition focus:border-blue-500 focus:ring-2 focus:ring-blue-100 disabled:bg-gray-50 disabled:text-gray-400"
          :disabled="loading || streaming || isRecording"
          @keydown="handleKeydown"
        />

        <!-- 麦克风按钮 -->
        <button
          type="button"
          class="flex h-11 w-11 shrink-0 items-center justify-center rounded-full transition"
          :class="
            isRecording
              ? 'animate-pulse bg-red-500 text-white hover:bg-red-600'
              : 'bg-gray-100 text-gray-600 hover:bg-gray-200 disabled:opacity-50'
          "
          :disabled="loading || streaming"
          :title="isRecording ? '停止录音' : '开始语音输入'"
          @click="toggleMicrophone"
        >
          <Microphone class="h-5 w-5" />
        </button>

        <!-- 发送按钮 -->
        <button
          type="button"
          class="flex h-11 w-11 shrink-0 items-center justify-center rounded-full bg-blue-600 text-white transition hover:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-50"
          :disabled="!canSend"
          title="发送"
          @click="handleSend"
        >
          <Promotion class="h-5 w-5" />
        </button>
      </div>

      <p v-if="isRecording" class="mx-auto mt-2 max-w-3xl text-center text-xs text-red-500">
        正在录音，音频通过 WebSocket 实时传输...
      </p>
    </footer>
  </div>
</template>

<style scoped>
/* Markdown 渲染样式 */
.markdown-body :deep(pre) {
  margin: 0.5rem 0;
  overflow-x: auto;
  border-radius: 0.5rem;
  background: #1e293b;
  padding: 0.75rem 1rem;
  color: #e2e8f0;
  font-size: 0.8125rem;
  line-height: 1.5;
}

.markdown-body :deep(code) {
  font-family: ui-monospace, Consolas, monospace;
  font-size: 0.8125rem;
}

.markdown-body :deep(p) {
  margin: 0.25rem 0;
}

.markdown-body :deep(p:first-child) {
  margin-top: 0;
}

.markdown-body :deep(p:last-child) {
  margin-bottom: 0;
}

.markdown-body :deep(strong) {
  font-weight: 600;
}

.markdown-body :deep(pre code) {
  background: transparent;
  padding: 0;
  color: inherit;
}

.markdown-body :deep(:not(pre) > code) {
  border-radius: 0.25rem;
  background: #f1f5f9;
  padding: 0.125rem 0.375rem;
  color: #334155;
}
</style>
