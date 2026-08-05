import { useCallback, useEffect, useRef, useState } from 'react'

import { streamAssistantChat } from './assistantApi'
import { getAssistantDeviceId } from './assistantDevice'
import {
  createId,
  createSession,
  loadAssistantSession,
  saveAssistantSession,
} from './assistantStorage'
import type { AssistantMessage, AssistantRecommendation } from './assistantTypes'

interface UseAssistantSessionOptions {
  pathname: string
  intro: string
  isOpen: boolean
}

export function useAssistantSession({ pathname, intro, isOpen }: UseAssistantSessionOptions) {
  const initialMessage = useRef<AssistantMessage>({
    id: createId('message'),
    role: 'assistant',
    content: intro,
    createdAt: Date.now(),
    status: 'done',
  })
  const initialSession = useRef(loadAssistantSession(initialMessage.current))
  const [sessionId, setSessionId] = useState(initialSession.current.sessionId)
  const [messages, setMessages] = useState(initialSession.current.messages)
  const [isStreaming, setIsStreaming] = useState(false)
  const [unreadCount, setUnreadCount] = useState(0)
  const abortRef = useRef<AbortController | null>(null)
  const frameRef = useRef<number | null>(null)
  const pendingDeltaRef = useRef('')
  const openRef = useRef(isOpen)

  useEffect(() => {
    openRef.current = isOpen
    if (isOpen) setUnreadCount(0)
  }, [isOpen])

  useEffect(() => {
    saveAssistantSession(sessionId, messages)
  }, [messages, sessionId])

  useEffect(() => () => {
    abortRef.current?.abort()
    if (frameRef.current !== null) cancelAnimationFrame(frameRef.current)
  }, [])

  const updateAssistant = useCallback((messageId: string, update: (message: AssistantMessage) => AssistantMessage) => {
    setMessages((current) => current.map((message) => (
      message.id === messageId ? update(message) : message
    )))
  }, [])

  const flushDelta = useCallback((messageId: string) => {
    frameRef.current = null
    const delta = pendingDeltaRef.current
    pendingDeltaRef.current = ''
    if (!delta) return
    updateAssistant(messageId, (message) => ({ ...message, content: message.content + delta }))
  }, [updateAssistant])

  const sendMessage = useCallback(async (rawContent: string, baseMessages: AssistantMessage[] = messages) => {
    const content = rawContent.trim()
    if (!content || isStreaming || content.length > 500) return false

    const userMessage: AssistantMessage = {
      id: createId('message'), role: 'user', content, createdAt: Date.now(), status: 'done',
    }
    const assistantId = createId('message')
    const assistantMessage: AssistantMessage = {
      id: assistantId, role: 'assistant', content: '', createdAt: Date.now(), status: 'streaming',
    }
    const requestMessages = [...baseMessages, userMessage]
    setMessages([...baseMessages, userMessage, assistantMessage])
    setIsStreaming(true)
    const controller = new AbortController()
    abortRef.current = controller
    let streamError: string | null = null

    try {
      await streamAssistantChat({
        sessionId,
        deviceId: getAssistantDeviceId(),
        currentPath: pathname,
        messages: requestMessages,
        signal: controller.signal,
        callbacks: {
          onDelta: (delta) => {
            pendingDeltaRef.current += delta
            if (frameRef.current === null) {
              frameRef.current = requestAnimationFrame(() => flushDelta(assistantId))
            }
          },
          onRecommendations: (items: AssistantRecommendation[]) => {
            updateAssistant(assistantId, (message) => ({ ...message, recommendations: items }))
          },
          onDone: () => {
            if (frameRef.current !== null) {
              cancelAnimationFrame(frameRef.current)
              frameRef.current = null
            }
            flushDelta(assistantId)
            updateAssistant(assistantId, (message) => ({ ...message, status: 'done' }))
            if (!openRef.current) setUnreadCount((count) => Math.min(9, count + 1))
          },
          onError: (_code, message) => {
            streamError = message
            updateAssistant(assistantId, (current) => ({
              ...current, status: 'error', error: message,
              content: current.content || message,
            }))
            if (!openRef.current) setUnreadCount((count) => Math.min(9, count + 1))
          },
        },
      })
      if (streamError) return false
      return true
    } catch (error) {
      if (controller.signal.aborted) {
        updateAssistant(assistantId, (message) => ({
          ...message, status: 'aborted', content: message.content || '已停止生成。',
        }))
      } else {
        const message = error instanceof Error ? error.message : '蒜宝暂时不可用'
        updateAssistant(assistantId, (current) => ({
          ...current, status: 'error', error: message, content: current.content || message,
        }))
      }
      return false
    } finally {
      abortRef.current = null
      setIsStreaming(false)
    }
  }, [flushDelta, isStreaming, messages, pathname, sessionId, updateAssistant])

  const stopGeneration = useCallback(() => abortRef.current?.abort(), [])

  const clearSession = useCallback(() => {
    abortRef.current?.abort()
    const nextIntro: AssistantMessage = {
      id: createId('message'), role: 'assistant', content: intro, createdAt: Date.now(), status: 'done',
    }
    const next = createSession(nextIntro)
    setSessionId(next.sessionId)
    setMessages(next.messages)
    setUnreadCount(0)
    setIsStreaming(false)
  }, [intro])

  const retryLast = useCallback(() => {
    const lastUserIndex = messages.map((message) => message.role).lastIndexOf('user')
    if (lastUserIndex < 0) return Promise.resolve(false)
    const lastUser = messages[lastUserIndex]
    const baseMessages = messages.slice(0, lastUserIndex)
    return sendMessage(lastUser.content, baseMessages)
  }, [messages, sendMessage])

  return { messages, isStreaming, unreadCount, sendMessage, stopGeneration, clearSession, retryLast }
}
