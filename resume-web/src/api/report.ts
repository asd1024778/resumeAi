import request from './request'
import type { AnalysisReport } from '@/types'

/** 获取报告详情（按用户隔离） */
export async function getReport(reportId: number): Promise<AnalysisReport> {
  return request.get(`/report/${reportId}`)
}
