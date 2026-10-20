import request from './request'
import type { UploadResumeResponse } from '@/types'

/** 上传简历文件 */
export async function uploadResume(file: File): Promise<UploadResumeResponse> {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/resume/upload', formData)
}
