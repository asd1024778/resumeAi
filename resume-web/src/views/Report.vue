<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElCollapse, ElCollapseItem } from 'element-plus'
import * as echarts from 'echarts'
import type { ECharts } from 'echarts'
import { startInterview } from '@/api/interview'
import { getReport } from '@/api/report'
import { useInterviewStore } from '@/stores/interview'
import { useReportStore } from '@/stores/report'
import { formatError } from '@/utils/formatError'
import type { AnalysisReport, ReportRadarScore } from '@/types'

const route = useRoute()
const router = useRouter()
const reportStore = useReportStore()
const interviewStore = useInterviewStore()

const chartRef = ref<HTMLElement | null>(null)
const interviewLoading = ref(false)
const interviewError = ref<string | null>(null)
const pageLoading = ref(false)
const pageError = ref<string | null>(null)
let chartInstance: ECharts | null = null

const report = computed<AnalysisReport | null>(() => reportStore.report)

const radarIndicators = [
  { name: '沟通表达', key: 'communication' as keyof ReportRadarScore },
  { name: '专业技能', key: 'professional' as keyof ReportRadarScore },
  { name: '逻辑思维', key: 'logic' as keyof ReportRadarScore },
  { name: '项目经验', key: 'project' as keyof ReportRadarScore },
  { name: '基础知识', key: 'basic' as keyof ReportRadarScore },
]

function buildRadarOption(data: ReportRadarScore): echarts.EChartsOption {
  return {
    tooltip: { trigger: 'item' },
    radar: {
      indicator: radarIndicators.map((item) => ({ name: item.name, max: 100 })),
      radius: '65%',
      splitNumber: 5,
      axisName: { color: '#64748b', fontSize: 13 },
      splitArea: {
        areaStyle: {
          color: ['rgba(59,130,246,0.02)', 'rgba(59,130,246,0.06)'],
        },
      },
    },
    series: [
      {
        type: 'radar',
        data: [
          {
            name: '能力评分',
            value: radarIndicators.map((item) => data[item.key]),
            areaStyle: { color: 'rgba(59,130,246,0.25)' },
            lineStyle: { color: '#3b82f6', width: 2 },
            itemStyle: { color: '#3b82f6' },
          },
        ],
      },
    ],
  }
}

function initChart(): void {
  if (!chartRef.value || !report.value) {
    return
  }
  chartInstance?.dispose()
  chartInstance = echarts.init(chartRef.value)
  chartInstance.setOption(buildRadarOption(report.value.radar))
}

function handleResize(): void {
  chartInstance?.resize()
}

function exportPdf(): void {
  window.print()
}

function goHome(): void {
  router.push('/')
}

async function handleStartInterview(): Promise<void> {
  if (!report.value) {
    return
  }

  interviewLoading.value = true
  interviewError.value = null

  try {
    const result = await startInterview({ resumeId: report.value.reportId })
    interviewStore.reset()
    interviewStore.initSession({
      sessionId: result.session.id,
      questions: result.firstQuestion ? [result.firstQuestion] : [],
      resumeTitle: report.value.resumeTitle ?? '简历',
      jobTitle: report.value.jobTitle ?? '模拟面试',
      durationSeconds: 30 * 60,
      currentQuestionId: result.firstQuestion?.id,
    })
    await router.push({ name: 'interview' })
  } catch (err) {
    interviewError.value = formatError(err)
  } finally {
    interviewLoading.value = false
  }
}

function getScoreColor(score: number): string {
  if (score >= 80) {
    return 'text-green-600'
  }
  if (score >= 60) {
    return 'text-yellow-600'
  }
  return 'text-red-600'
}

onMounted(async () => {
  if (!report.value) {
    pageLoading.value = true
    pageError.value = null
    try {
      const reportId = Number(route.params.id)
      if (Number.isNaN(reportId)) {
        throw new Error('无效的报告编号')
      }
      const data = await getReport(reportId)
      reportStore.setReport(data)
    } catch (err) {
      pageError.value = formatError(err)
      return
    } finally {
      pageLoading.value = false
    }
  }

  if (!report.value) {
    return
  }

  initChart()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  chartInstance?.dispose()
  chartInstance = null
})

watch(
  () => report.value?.radar,
  () => {
    if (report.value && chartInstance) {
      chartInstance.setOption(buildRadarOption(report.value.radar))
    }
  },
  { deep: true },
)
</script>

<template>
  <div v-if="pageLoading" class="flex min-h-screen items-center justify-center text-gray-400">
    加载报告中...
  </div>

  <div
    v-else-if="pageError"
    class="flex min-h-screen flex-col items-center justify-center gap-4 px-4"
  >
    <p class="text-sm text-red-600">{{ pageError }}</p>
    <button
      type="button"
      class="rounded-lg bg-blue-600 px-4 py-2 text-sm text-white hover:bg-blue-700"
      @click="goHome"
    >
      返回首页
    </button>
  </div>

  <div v-else-if="report" id="report-content" class="min-h-screen bg-gray-50">
    <!-- 顶部操作栏 -->
    <header class="no-print border-b border-gray-200 bg-white px-6 py-4 shadow-sm">
      <div class="mx-auto flex max-w-5xl items-center justify-between">
        <button
          type="button"
          class="text-sm text-gray-500 hover:text-gray-700"
          @click="goHome"
        >
          ← 返回首页
        </button>
        <h1 class="text-lg font-semibold text-gray-900">分析报告</h1>
        <div class="flex items-center gap-2">
          <button
            type="button"
            class="rounded-lg bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700"
            @click="exportPdf"
          >
            导出 PDF 报告
          </button>
          <button
            type="button"
            class="rounded-lg bg-green-600 px-4 py-2 text-sm font-medium text-white hover:bg-green-700 disabled:cursor-not-allowed disabled:opacity-50"
            :disabled="interviewLoading"
            @click="handleStartInterview"
          >
            {{ interviewLoading ? '准备中...' : '开始模拟面试' }}
          </button>
        </div>
      </div>
    </header>

    <div
      v-if="interviewError"
      class="no-print mx-auto mt-4 max-w-5xl rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-600"
    >
      {{ interviewError }}
    </div>

    <main class="mx-auto max-w-5xl px-6 py-8">
      <!-- 报告标题 -->
      <section class="mb-8 text-center print:mb-6">
        <h2 class="text-2xl font-bold text-gray-900">
          {{ report.resumeTitle ?? '简历分析报告' }}
        </h2>
        <p v-if="report.jobTitle" class="mt-1 text-sm text-gray-500">
          目标岗位：{{ report.jobTitle }}
        </p>
        <p class="mt-1 text-xs text-gray-400">报告编号：#{{ report.reportId ?? route.params.id }}</p>
      </section>

      <!-- 总分 + 雷达图 -->
      <section class="mb-8 grid gap-6 md:grid-cols-2">
        <!-- 综合评分 -->
        <div class="rounded-2xl border border-gray-200 bg-white p-6 shadow-sm">
          <h3 class="mb-4 text-base font-semibold text-gray-800">综合评分</h3>
          <div class="flex items-end gap-2">
            <span class="text-6xl font-bold text-blue-600">{{ report.score }}</span>
            <span class="mb-2 text-lg text-gray-400">/ 100</span>
          </div>
          <div class="mt-4 h-2 overflow-hidden rounded-full bg-gray-100">
            <div
              class="h-full rounded-full bg-blue-500 transition-all"
              :style="{ width: `${report.score}%` }"
            />
          </div>
        </div>

        <!-- ECharts 雷达图 -->
        <div class="rounded-2xl border border-gray-200 bg-white p-4 shadow-sm">
          <h3 class="mb-2 px-2 text-base font-semibold text-gray-800">能力雷达图</h3>
          <div ref="chartRef" class="h-72 w-full" />
        </div>
      </section>

      <!-- 改进建议 -->
      <section class="mb-8 rounded-2xl border border-gray-200 bg-white p-6 shadow-sm">
        <h3 class="mb-4 text-base font-semibold text-gray-800">改进建议</h3>
        <ul v-if="report.suggestions.length" class="space-y-3">
          <li
            v-for="(item, index) in report.suggestions"
            :key="index"
            class="flex gap-3 text-sm text-gray-700"
          >
            <span
              class="flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-blue-100 text-xs font-medium text-blue-600"
            >
              {{ index + 1 }}
            </span>
            <span class="leading-relaxed">{{ item }}</span>
          </li>
        </ul>
        <p v-else class="text-sm text-gray-400">暂无改进建议</p>
      </section>

      <!-- 逐题复盘 -->
      <section class="rounded-2xl border border-gray-200 bg-white p-6 shadow-sm">
        <h3 class="mb-4 text-base font-semibold text-gray-800">逐题复盘</h3>

        <el-collapse v-if="report.reviews.length" accordion>
          <el-collapse-item
            v-for="(review, index) in report.reviews"
            :key="index"
            :name="String(index)"
          >
            <template #title>
              <span class="text-sm font-medium text-gray-800">
                第 {{ index + 1 }} 题：{{ review.question }}
              </span>
            </template>

            <div class="space-y-4 px-1 pb-2">
              <div>
                <p class="mb-1 text-xs font-medium text-gray-400">问题</p>
                <p class="text-sm text-gray-800">{{ review.question }}</p>
              </div>
              <div>
                <p class="mb-1 text-xs font-medium text-gray-400">我的回答</p>
                <p class="whitespace-pre-wrap rounded-lg bg-gray-50 p-3 text-sm text-gray-700">
                  {{ review.myAnswer }}
                </p>
              </div>
              <div class="flex items-center gap-4">
                <div>
                  <p class="mb-1 text-xs font-medium text-gray-400">AI 评分</p>
                  <span class="text-2xl font-bold" :class="getScoreColor(review.aiScore)">
                    {{ review.aiScore }}
                  </span>
                  <span class="text-sm text-gray-400"> / 100</span>
                </div>
              </div>
              <div>
                <p class="mb-1 text-xs font-medium text-gray-400">AI 评语</p>
                <p class="rounded-lg border border-blue-100 bg-blue-50 p-3 text-sm leading-relaxed text-gray-700">
                  {{ review.aiComment }}
                </p>
              </div>
            </div>
          </el-collapse-item>
        </el-collapse>

        <p v-else class="text-sm text-gray-400">暂无面试复盘数据</p>
      </section>
    </main>
  </div>
</template>

<style scoped>
@media print {
  .no-print {
    display: none !important;
  }

  #report-content {
    background: white;
  }
}
</style>
