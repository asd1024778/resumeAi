<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { UploadFilled } from '@element-plus/icons-vue'
import { uploadResume } from '@/api/resume'
import { useReportStore } from '@/stores/report'
import { formatError } from '@/utils/formatError'

const ALLOWED_EXTENSIONS = ['.pdf', '.docx', '.txt']

const router = useRouter()
const reportStore = useReportStore()

const isDragging = ref(false)
const loading = ref(false)
const error = ref<string | null>(null)
const selectedFile = ref<File | null>(null)
const fileInputRef = ref<HTMLInputElement | null>(null)

function isAllowedFile(file: File): boolean {
  const dotIndex = file.name.lastIndexOf('.')
  if (dotIndex === -1) {
    return false
  }
  const ext = file.name.slice(dotIndex).toLowerCase()
  return ALLOWED_EXTENSIONS.includes(ext)
}

function formatFileSize(bytes: number): string {
  if (bytes < 1024) {
    return `${bytes} B`
  }
  if (bytes < 1024 * 1024) {
    return `${(bytes / 1024).toFixed(1)} KB`
  }
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

function handleDragOver(event: DragEvent): void {
  event.preventDefault()
  isDragging.value = true
}

function handleDragLeave(): void {
  isDragging.value = false
}

function handleDrop(event: DragEvent): void {
  event.preventDefault()
  isDragging.value = false
  error.value = null

  const file = event.dataTransfer?.files[0]
  if (!file) {
    return
  }
  validateAndSelect(file)
}

function handleFileChange(event: Event): void {
  error.value = null
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (file) {
    validateAndSelect(file)
  }
}

function validateAndSelect(file: File): void {
  if (!isAllowedFile(file)) {
    error.value = '仅支持 .pdf、.docx、.txt 格式的文件'
    selectedFile.value = null
    return
  }
  selectedFile.value = file
}

function openFilePicker(): void {
  fileInputRef.value?.click()
}

async function handleUpload(): Promise<void> {
  if (!selectedFile.value) {
    error.value = '请先选择要上传的简历文件'
    return
  }

  loading.value = true
  error.value = null

  try {
    const report = await uploadResume(selectedFile.value)
    reportStore.setReport(report)
    await router.push({ name: 'report', params: { id: String(report.reportId) } })
  } catch (err) {
    error.value = formatError(err)
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="flex min-h-screen flex-col items-center justify-center bg-gray-50 px-4">
    <div class="mb-8 text-center">
      <h1 class="text-3xl font-bold text-gray-900">AI 简历分析</h1>
      <p class="mt-2 text-sm text-gray-500">上传简历，获取智能评分与面试改进建议</p>
    </div>

    <!-- 拖拽上传区域 -->
    <div
      class="w-full max-w-xl rounded-2xl border-2 border-dashed bg-white p-10 text-center transition"
      :class="
        isDragging
          ? 'border-blue-500 bg-blue-50'
          : 'border-gray-300 hover:border-blue-400 hover:bg-gray-50'
      "
      @dragover="handleDragOver"
      @dragleave="handleDragLeave"
      @drop="handleDrop"
    >
      <div class="mx-auto mb-4 flex h-16 w-16 items-center justify-center rounded-full bg-blue-100 text-blue-600">
        <UploadFilled class="h-8 w-8" />
      </div>

      <p class="text-base font-medium text-gray-700">
        拖拽文件到此处，或
        <button
          type="button"
          class="text-blue-600 underline hover:text-blue-700"
          @click="openFilePicker"
        >
          点击选择文件
        </button>
      </p>
      <p class="mt-2 text-xs text-gray-400">支持 .pdf、.docx、.txt，单文件上传</p>

      <input
        ref="fileInputRef"
        type="file"
        class="hidden"
        accept=".pdf,.docx,.txt"
        @change="handleFileChange"
      />

      <!-- 已选文件 -->
      <div
        v-if="selectedFile"
        class="mx-auto mt-6 max-w-sm rounded-lg border border-gray-200 bg-gray-50 px-4 py-3 text-left"
      >
        <p class="truncate text-sm font-medium text-gray-800">{{ selectedFile.name }}</p>
        <p class="mt-0.5 text-xs text-gray-400">{{ formatFileSize(selectedFile.size) }}</p>
      </div>
    </div>

    <!-- 错误提示 -->
    <div
      v-if="error"
      class="mt-4 w-full max-w-xl rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-600"
    >
      {{ error }}
    </div>

    <!-- 上传按钮 -->
    <button
      type="button"
      class="no-print mt-6 rounded-xl bg-blue-600 px-8 py-3 text-sm font-medium text-white transition hover:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-50"
      :disabled="!selectedFile || loading"
      @click="handleUpload"
    >
      {{ loading ? '上传分析中...' : '开始分析' }}
    </button>
  </div>
</template>
