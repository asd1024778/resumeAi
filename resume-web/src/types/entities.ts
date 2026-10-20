/** 用户敏感资料（API 默认返回脱敏后的值） */
export interface UserSensitiveProfile {
  realName?: string
  phone?: string
  idCard?: string
  address?: string
  socialAccount?: string
}

/** 用户 */
export interface User {
  id: number
  username: string
  email?: string
  profile?: UserSensitiveProfile
  avatar?: string
  createdAt?: string
}

/** 简历 */
export interface Resume {
  id: number
  userId: number
  title: string
  content?: string
  fileUrl?: string
  status?: 'draft' | 'published' | 'archived'
  createdAt?: string
  updatedAt?: string
}

/** 面试会话状态 */
export type InterviewSessionStatus =
  | 'pending'
  | 'in_progress'
  | 'completed'
  | 'cancelled'

/** 面试会话 */
export interface InterviewSession {
  id: number
  userId: number
  resumeId: number
  status: InterviewSessionStatus
  startedAt?: string
  endedAt?: string
  totalScore?: number
}

/** 消息角色 */
export type MessageRole = 'user' | 'assistant' | 'system'

/** 对话消息 */
export interface Message {
  id?: number
  sessionId: number
  role: MessageRole
  content: string
  createdAt?: string
}

/** 面试题目 */
export interface Question {
  id: number
  sessionId: number
  content: string
  category?: string
  difficulty?: 'easy' | 'medium' | 'hard'
  order?: number
}

/** 雷达图评分维度 */
export interface ScoreDimension {
  name: string
  score: number
  maxScore?: number
}

/** 雷达图评分结果 */
export interface ScoreRadar {
  sessionId: number
  dimensions: ScoreDimension[]
  totalScore?: number
}
