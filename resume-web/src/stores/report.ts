import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { AnalysisReport } from '@/types'

export const useReportStore = defineStore('report', () => {
  const report = ref<AnalysisReport | null>(null)

  function setReport(data: AnalysisReport): void {
    report.value = data
  }

  function clearReport(): void {
    report.value = null
  }

  return {
    report,
    setReport,
    clearReport,
  }
})
