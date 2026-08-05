import type { AssistantMessage, StoredAssistantSession } from './assistantTypes'

const STORAGE_KEY = 'kod:public-assistant:v1'
const MAX_MESSAGES = 30
const MAX_CONTENT_CHARS = 2000

export function createId(prefix: string) {
  if (globalThis.crypto?.randomUUID) return `${prefix}_${globalThis.crypto.randomUUID()}`
  return `${prefix}_${Date.now()}_${Math.random().toString(36).slice(2)}`
}

export function loadAssistantSession(initialMessage: AssistantMessage): StoredAssistantSession {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return createSession(initialMessage)
    const parsed = JSON.parse(raw) as Partial<StoredAssistantSession>
    if (parsed.schemaVersion !== 1 || !parsed.sessionId || !Array.isArray(parsed.messages)) {
      return createSession(initialMessage)
    }
    const messages = parsed.messages
      .filter(isStoredMessage)
      .slice(-MAX_MESSAGES)
      .map((message) => ({
        ...message,
        content: message.content.slice(0, MAX_CONTENT_CHARS),
        status: message.status === 'streaming' ? ('aborted' as const) : message.status,
      }))
    return {
      schemaVersion: 1,
      sessionId: parsed.sessionId,
      updatedAt: typeof parsed.updatedAt === 'number' ? parsed.updatedAt : Date.now(),
      messages: messages.length ? messages : [initialMessage],
    }
  } catch {
    return createSession(initialMessage)
  }
}

export function saveAssistantSession(sessionId: string, messages: AssistantMessage[]) {
  const session: StoredAssistantSession = {
    schemaVersion: 1,
    sessionId,
    updatedAt: Date.now(),
    messages: messages.slice(-MAX_MESSAGES).map((message) => ({
      ...message,
      content: message.content.slice(0, MAX_CONTENT_CHARS),
    })),
  }
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(session))
  } catch {
    // localStorage 满或被禁用时保持当前内存会话可用。
  }
}

export function createSession(initialMessage: AssistantMessage): StoredAssistantSession {
  return {
    schemaVersion: 1,
    sessionId: createId('session'),
    updatedAt: Date.now(),
    messages: [initialMessage],
  }
}

function isStoredMessage(value: unknown): value is AssistantMessage {
  if (!value || typeof value !== 'object') return false
  const message = value as Partial<AssistantMessage>
  return (
    typeof message.id === 'string'
    && (message.role === 'assistant' || message.role === 'user')
    && typeof message.content === 'string'
    && typeof message.createdAt === 'number'
    && ['streaming', 'done', 'error', 'aborted'].includes(message.status ?? '')
  )
}
