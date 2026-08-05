import type {
  AssistantMessage,
  AssistantRecommendation,
  AssistantStreamCallbacks,
} from './assistantTypes'

interface StreamAssistantParams {
  sessionId: string
  deviceId: string
  currentPath: string
  messages: AssistantMessage[]
  signal: AbortSignal
  callbacks: AssistantStreamCallbacks
}

const ALLOWED_PATHS = new Set([
  '/', '/kai', '/features', '/download', '/changelog',
  '/kod-ai-services-faqs', '/feedback', '/login', '/register',
  '/jsjsubmit', '/console', '/console/wallet', '/console/logs',
])

export async function streamAssistantChat(params: StreamAssistantParams) {
  const response = await fetch('/api/public/assistant/chat', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'text/event-stream',
      'X-Assistant-Device-Id': params.deviceId,
    },
    body: JSON.stringify({
      sessionId: params.sessionId,
      deviceId: params.deviceId,
      currentPath: params.currentPath,
      messages: params.messages
        .filter((message) => message.content.trim())
        .slice(-8)
        .map(({ role, content }) => ({ role, content })),
    }),
    signal: params.signal,
  })

  if (!response.ok || !response.body) {
    let message = '蒜宝暂时不可用，请稍后再试'
    try {
      const error = await response.json() as { message?: string }
      if (error.message) message = error.message
    } catch {
      // 非 JSON 错误响应使用统一文案。
    }
    throw new Error(message)
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''

  while (true) {
    const { done, value } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })
    const split = buffer.split(/\r?\n\r?\n/)
    buffer = split.pop() ?? ''
    for (const block of split) consumeEvent(block, params.callbacks)
  }
  if (buffer.trim()) consumeEvent(buffer, params.callbacks)
}

function consumeEvent(block: string, callbacks: AssistantStreamCallbacks) {
  let eventName = 'message'
  const dataLines: string[] = []
  for (const line of block.split(/\r?\n/)) {
    if (line.startsWith('event:')) eventName = line.slice(6).trim()
    if (line.startsWith('data:')) dataLines.push(line.slice(5).trimStart())
  }
  if (!dataLines.length) return

  let payload: unknown
  try {
    payload = JSON.parse(dataLines.join('\n'))
  } catch {
    return
  }

  if (eventName === 'start') {
    callbacks.onStart?.((payload as { messageId?: string }).messageId ?? '')
  } else if (eventName === 'delta') {
    callbacks.onDelta((payload as { text?: string }).text ?? '')
  } else if (eventName === 'recommendations') {
    const items = (payload as { items?: AssistantRecommendation[] }).items ?? []
    callbacks.onRecommendations(items.filter(isAllowedRecommendation))
  } else if (eventName === 'done') {
    callbacks.onDone()
  } else if (eventName === 'error') {
    const error = payload as { code?: string; message?: string }
    callbacks.onError(error.code ?? 'UNKNOWN', error.message ?? '蒜宝暂时不可用')
  }
}

function isAllowedRecommendation(item: AssistantRecommendation) {
  return Boolean(item?.label && ALLOWED_PATHS.has(item.path))
}
