import type { GuideRoute } from './garlicGuideData'

export type AssistantMessageStatus = 'streaming' | 'done' | 'error' | 'aborted'

export interface AssistantRecommendation {
  label: string
  path: GuideRoute
  reason?: string
}

export interface AssistantMessage {
  id: string
  role: 'assistant' | 'user'
  content: string
  createdAt: number
  status: AssistantMessageStatus
  error?: string
  recommendations?: AssistantRecommendation[]
}

export interface StoredAssistantSession {
  schemaVersion: 1
  sessionId: string
  updatedAt: number
  messages: AssistantMessage[]
}

export interface AssistantStreamCallbacks {
  onStart?: (messageId: string) => void
  onDelta: (text: string) => void
  onRecommendations: (items: AssistantRecommendation[]) => void
  onDone: () => void
  onError: (code: string, message: string) => void
}
