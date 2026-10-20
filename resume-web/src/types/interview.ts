import type { InterviewSession, Message, Question, ScoreRadar } from './entities'

/** 开启面试请求参数 */
export interface StartInterviewRequest {
  resumeId: number
}

/** 开启面试响应数据 */
export interface StartInterviewResponse {
  session: InterviewSession
  firstQuestion?: Question
}

/** 提交答案请求参数 */
export interface SubmitAnswerRequest {
  sessionId: number
  questionId: number
  answer: string
}

/** 提交答案响应数据 */
export interface SubmitAnswerResponse {
  message?: Message
  nextQuestion?: Question
  scoreRadar?: ScoreRadar
}

/** SSE 流式对话请求参数 */
export interface InterviewStreamRequest {
  sessionId: number
  questionId: number
  answer: string
}
