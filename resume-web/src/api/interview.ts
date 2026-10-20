import request from './request'
import type {
  StartInterviewRequest,
  StartInterviewResponse,
  SubmitAnswerRequest,
  SubmitAnswerResponse,
} from '@/types'

/** 开启面试 */
export async function startInterview(
  params: StartInterviewRequest,
): Promise<StartInterviewResponse> {
  return request.post('/interview/start', params)
}

/** 提交答案 */
export async function submitAnswer(
  params: SubmitAnswerRequest,
): Promise<SubmitAnswerResponse> {
  return request.post('/interview/answer', params)
}
