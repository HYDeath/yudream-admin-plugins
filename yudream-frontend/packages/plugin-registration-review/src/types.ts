export interface ReviewMeta {
  enabled: boolean
  mode: 'quiz' | 'triple' | 'both' | 'triple_quiz'
  passingPercent: number
  drawCount: number
  questionbankAvailable: boolean
  bilibiliIntegration: boolean
  bilibiliMessage: string
}

export type ReviewStatus = 'questionnaire_ongoing' | 'questionnaire_pending_review' | 'passed' | 'failed' | 'approved' | 'rejected'

export interface ReviewApplication {
  id: string
  userId: string
  userLabel: string
  mode: string
  questionBankSessionId: string
  questionBankSessionPath: string
  status: ReviewStatus
  totalCount: number
  correctCount: number
  scorePercent: number
  message: string
  reviewerId: string
  reviewedAt: number
  attemptNumber: number
  createdAt: number
  updatedAt: number
}

export interface Page<T> { records: T[], total: number }
export type SettingValues = Record<string, unknown>
export interface SettingField { key: string, label: string, type: string, default?: unknown, help?: string, min?: number, max?: number, options?: { label: string, value: string }[] }
export interface SettingSection { code: string, title: string, description?: string, fields: SettingField[] }
export interface SettingSchema { version: number, sections: SettingSection[] }
