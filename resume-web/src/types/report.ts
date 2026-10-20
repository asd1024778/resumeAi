/** 雷达图各维度分数 */
export interface ReportRadarScore {
  /** 沟通表达 */
  communication: number
  /** 专业技能 */
  professional: number
  /** 逻辑思维 */
  logic: number
  /** 项目经验 */
  project: number
  /** 基础知识 */
  basic: number
}

/** 逐题复盘条目 */
export interface QuestionReview {
  question: string
  myAnswer: string
  aiScore: number
  aiComment: string
}

/** 简历分析报告 */
export interface AnalysisReport {
  reportId: number
  resumeTitle?: string
  jobTitle?: string
  score: number
  suggestions: string[]
  radar: ReportRadarScore
  reviews: QuestionReview[]
}

/** 简历上传响应 */
export interface UploadResumeResponse extends AnalysisReport {}
