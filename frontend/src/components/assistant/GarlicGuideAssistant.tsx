import { FormEvent, KeyboardEvent, MouseEvent, useEffect, useId, useRef, useState } from 'react'
import { useNavigate, useRouterState } from '@tanstack/react-router'
import { ArrowUp, RotateCcw, Sparkles, Square, X } from 'lucide-react'

import { getRouteGuide, type GuideAction } from './garlicGuideData'
import type { AssistantMessage } from './assistantTypes'
import { useAssistantSession } from './useAssistantSession'

const MAX_INPUT_CHARS = 500

const mascotSizes = {
  sm: 'h-6 w-6',
  md: 'h-9 w-9',
  lg: 'h-[44px] w-[44px]',
}

/** 蒜宝 — 使用 蒜宝.svg */
function SuanbaoMascot({ size = 'md' as const }: { size?: 'sm' | 'md' | 'lg' }) {
  return (
    <img src="/images/suanbao.svg" alt="蒜宝" className={`block shrink-0 ${mascotSizes[size]}`} />
  )
}

/** 蒜宝输入中动画 — 三个弹跳圆点 */
function TypingDots() {
  return (
    <span className="inline-flex items-center gap-1 px-1" aria-label="蒜宝正在输入">
      <span className="h-1.5 w-1.5 rounded-full bg-brand-400 animate-bounce" style={{ animationDelay: '0ms' }} />
      <span className="h-1.5 w-1.5 rounded-full bg-brand-400 animate-bounce" style={{ animationDelay: '150ms' }} />
      <span className="h-1.5 w-1.5 rounded-full bg-brand-400 animate-bounce" style={{ animationDelay: '300ms' }} />
    </span>
  )
}

function MessageContent({ message }: { message: AssistantMessage }) {
  const isStreamingEmpty = message.status === 'streaming' && !message.content
  return (
    <>
      <div className={`whitespace-pre-wrap rounded-2xl px-4 py-3 text-sm leading-relaxed shadow-sm message-enter ${message.role === 'user' ? 'rounded-br-md bg-brand-600 text-white' : 'rounded-bl-md border border-gray-100 bg-white text-gray-700'}`}>
        {isStreamingEmpty ? <span className="inline-flex items-center gap-1.5 text-gray-400 text-xs">蒜宝正在输入<TypingDots /></span> : message.content}
      </div>
      {message.status === 'error' && (
        <div className="mt-1.5 flex items-center gap-1.5">
          <span className="text-xs text-red-500">{(message.error ?? '').includes('频率') ? '提问太快啦，稍等片刻再试吧' : (message.error ?? '').includes('不可用') ? '蒜宝暂时离开了，请稍后再试' : '发送失败，可点击下方重试'}</span>
        </div>
      )}
      {message.recommendations && message.recommendations.length > 0 && (
        <div className="mt-2 flex flex-wrap gap-2">
          {message.recommendations.map((item) => (
            <span key={`${message.id}-${item.path}`} data-path={item.path} className="assistant-recommendation contents">
              <button type="button" className="rounded-full border border-brand-200 bg-white px-3 py-1.5 text-xs font-medium text-brand-700 transition-all hover:border-brand-400 hover:bg-brand-50 hover:shadow-sm" title={item.reason}>{item.label}</button>
            </span>
          ))}
        </div>
      )}
    </>
  )
}

export function GarlicGuideAssistant() {
  const pathname = useRouterState({ select: (state) => state.location.pathname })
  const navigate = useNavigate()
  const panelTitleId = useId()
  const inputRef = useRef<HTMLInputElement>(null)
  const messagesEndRef = useRef<HTMLDivElement>(null)
  const messagesContainerRef = useRef<HTMLDivElement>(null)
  const routeGuide = getRouteGuide(pathname)
  const [isOpen, setIsOpen] = useState(false)
  const [showHint, setShowHint] = useState(true)
  const [input, setInput] = useState('')
  const { messages, isStreaming, unreadCount, sendMessage, stopGeneration, clearSession, retryLast } = useAssistantSession({ pathname, intro: routeGuide.intro, isOpen })
  const inputCharsLeft = MAX_INPUT_CHARS - input.length
  const hasOnlyIntro = messages.length === 1 && messages[0].role === 'assistant'

  useEffect(() => {
    if (isOpen) inputRef.current?.focus()
  }, [isOpen])

  // 自动滚动到底部（流式输出时更频繁地滚动）
  useEffect(() => {
    if (!isOpen) return
    const el = messagesEndRef.current
    if (!el) return
    el.scrollIntoView({ behavior: isStreaming ? 'auto' : 'smooth', block: 'nearest' })
  }, [isOpen, messages, isStreaming])

  // 流式输出时额外用定时器保证滚动跟手
  useEffect(() => {
    if (!isOpen || !isStreaming) return
    const timer = setInterval(() => {
      messagesEndRef.current?.scrollIntoView({ behavior: 'auto', block: 'nearest' })
    }, 100)
    return () => clearInterval(timer)
  }, [isOpen, isStreaming])

  useEffect(() => {
    const handleEscape = (event: globalThis.KeyboardEvent) => {
      if (event.key === 'Escape') setIsOpen(false)
    }
    window.addEventListener('keydown', handleEscape)
    return () => window.removeEventListener('keydown', handleEscape)
  }, [])

  const submitPrompt = async (prompt: string) => {
    const ok = await sendMessage(prompt)
    if (ok) {
      setInput('')
      setShowHint(false)
    }
  }

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    void submitPrompt(input)
  }

  const handleInputKeyDown = (event: KeyboardEvent<HTMLInputElement>) => {
    if (event.key === 'Enter' && !event.nativeEvent.isComposing) {
      event.preventDefault()
      void submitPrompt(input)
    }
  }

  const handleAction = (action: GuideAction) => {
    setIsOpen(false)
    navigate({ to: action.to })
  }

  const handleMessageClick = (event: MouseEvent<HTMLDivElement>) => {
    const target = event.target as HTMLElement
    const container = target.closest<HTMLElement>('[data-path]')
    const path = container?.dataset.path as GuideAction['to'] | undefined
    if (path) handleAction({ label: target.textContent ?? '', to: path })
  }

  return (
    <aside className="pointer-events-none fixed inset-x-0 bottom-0 z-[60]" aria-label="蒜宝">
      {isOpen && (
        <section id="garlic-guide-panel" role="dialog" aria-modal="false" aria-labelledby={panelTitleId} className="pointer-events-auto absolute inset-2 flex flex-col overflow-hidden rounded-3xl border border-brand-100 bg-white shadow-2xl shadow-brand-900/15 animate-fade-in-up sm:inset-auto sm:bottom-6 sm:right-6 sm:w-[400px] sm:max-h-[calc(100dvh-6rem)]">
          {/* 头部 */}
          <header className="shrink-0 relative border-b border-brand-100 bg-gradient-to-br from-brand-50 via-white to-indigo-50 px-4 py-3">
            <div className="relative flex items-center gap-2.5">
              <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-white/80 shadow-sm ring-1 ring-brand-100">
                <SuanbaoMascot size="md" />
              </div>
              <div className="min-w-0 flex-1">
                <div className="flex items-center gap-1.5">
                  <h2 id={panelTitleId} className="font-bold text-gray-900">蒜宝</h2>
                  <Sparkles className="h-3.5 w-3.5 text-brand-500" />
                </div>
              </div>
              <button type="button" onClick={clearSession} className="rounded-lg p-1.5 text-gray-400 hover:bg-white hover:text-brand-600 transition-colors" aria-label="重置对话" title="重置对话">
                <RotateCcw className="h-4 w-4" />
              </button>
              <button type="button" onClick={() => setIsOpen(false)} className="rounded-lg p-1.5 text-gray-400 hover:bg-white hover:text-gray-700 transition-colors" aria-label="最小化蒜宝">
                <X className="h-5 w-5" />
              </button>
            </div>
          </header>

          {/* 消息列表 */}
          <div ref={messagesContainerRef} onClick={handleMessageClick} className="min-h-0 flex-1 space-y-3 overscroll-contain overflow-y-auto bg-gray-50/60 px-4 py-4 scroll-smooth" aria-live="polite">
            {messages.map((message) => (
              <div key={message.id} className={`flex message-row ${message.role === 'user' ? 'justify-end' : 'justify-start'}`}>
                <div className={`flex max-w-[88%] gap-2 ${message.role === 'user' ? 'flex-row-reverse' : ''}`}>
                  {message.role === 'assistant' && (
                    <div className="mt-0.5 flex h-7 w-7 shrink-0 items-center justify-center rounded-md bg-white/80 shadow-sm ring-1 ring-brand-100">
                      <SuanbaoMascot size="sm" />
                    </div>
                  )}
                  <div>
                    <MessageContent message={message} />
                  </div>
                </div>
              </div>
            ))}
            {/* 欢迎引导 — 仅首次打开时展示 */}
            {hasOnlyIntro && (
              <div className="animate-fade-in py-2">
                <p className="text-xs text-gray-400 text-center">👋 蒜宝已上线，试试问我这些问题：</p>
              </div>
            )}
            <div ref={messagesEndRef} />
          </div>

          {/* 输入区域 */}
          <div className="shrink-0 border-t border-gray-100 bg-white px-4 pb-[calc(1rem+env(safe-area-inset-bottom))] pt-3">
            {/* 快捷提问 */}
            <div className="mb-3 flex gap-2 overflow-x-auto pb-1 [scrollbar-width:none]">
              {routeGuide.suggestions.map((suggestion) => (
                <button key={suggestion} type="button" disabled={isStreaming} onClick={() => void submitPrompt(suggestion)} className="shrink-0 rounded-full bg-brand-50 px-3 py-1.5 text-xs font-medium text-brand-700 hover:bg-brand-100 disabled:opacity-50 transition-colors">
                  {suggestion}
                </button>
              ))}
            </div>

            {/* 输入框 */}
            <form onSubmit={handleSubmit} className="flex items-center gap-2 rounded-2xl border border-gray-200 bg-gray-50 p-1.5 focus-within:border-brand-400 focus-within:bg-white transition-colors">
              <label htmlFor="garlic-guide-input" className="sr-only">向蒜宝提问</label>
              <input
                ref={inputRef}
                id="garlic-guide-input"
                value={input}
                onChange={(event) => setInput(event.target.value)}
                onKeyDown={handleInputKeyDown}
                disabled={isStreaming}
                maxLength={MAX_INPUT_CHARS}
                placeholder="问我：怎么下载、API Key 在哪…"
                className="min-w-0 flex-1 bg-transparent px-3 py-2 text-sm text-gray-800 outline-none placeholder:text-gray-400 disabled:opacity-60"
                autoComplete="off"
              />
              {isStreaming ? (
                <button type="button" onClick={stopGeneration} className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-gray-800 text-white hover:bg-gray-700 transition-colors" aria-label="停止生成">
                  <Square className="h-4 w-4" />
                </button>
              ) : (
                <button type="submit" disabled={!input.trim()} className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-brand-600 text-white hover:bg-brand-700 disabled:bg-gray-300 transition-colors" aria-label="发送问题">
                  <ArrowUp className="h-4 w-4" />
                </button>
              )}
            </form>

            {/* 字符计数 + 重试 */}
            <div className="mt-1.5 flex items-center justify-between">
              <span className={`text-[10px] transition-colors ${inputCharsLeft <= 50 ? 'text-red-400' : inputCharsLeft <= 100 ? 'text-amber-500' : 'text-gray-400'}`}>
                {input.length > 0 ? `剩余 ${inputCharsLeft} 字符` : ''}
              </span>
              {messages.at(-1)?.status === 'error' && (
                <button type="button" onClick={() => void retryLast()} className="text-xs font-medium text-brand-600 hover:text-brand-700 transition-colors">
                  重新发送 ↗
                </button>
              )}
            </div>

            {messages.length <= 1 && (
              <p className="mt-1 text-center text-[10px] text-gray-400">请勿输入密码、验证码或 API Key；重要信息以官网页面为准</p>
            )}
          </div>
        </section>
      )}

      {/* 提示气泡 */}
      {!isOpen && showHint && (
        <div className="pointer-events-auto absolute bottom-24 right-20 hidden animate-fade-in rounded-2xl rounded-br-md border border-brand-100 bg-white px-4 py-2.5 text-sm text-gray-700 shadow-lg sm:block">
          <button type="button" onClick={() => setShowHint(false)} className="absolute -right-2 -top-2 flex h-5 w-5 items-center justify-center rounded-full bg-gray-700 text-white hover:bg-gray-800 transition-colors" aria-label="关闭提示">
            <X className="h-3 w-3" />
          </button>
          第一次来？问蒜宝 👋
        </div>
      )}

      {/* 悬浮按钮 — 面板打开时隐藏，用头部的 X 关闭 */}
      {!isOpen && (
      <button
        type="button"
        onClick={() => { setShowHint(false); setIsOpen(true) }}
        aria-label="打开蒜宝"
        aria-controls="garlic-guide-panel"
        className="group pointer-events-auto absolute bottom-[calc(1rem+env(safe-area-inset-bottom))] right-4 flex h-[60px] w-[60px] items-center justify-center transition-all duration-300 sm:bottom-6 sm:right-6"
      >
        {/* 悬浮光晕 — hover 时渐显 */}
        <span className="absolute -inset-4 rounded-full bg-brand-400/10 blur-2xl opacity-0 transition-all duration-700 group-hover:opacity-100 pointer-events-none" />

        {/* 呼吸光环 — 持续脉动 */}
        <span className="absolute -inset-[3px] rounded-full border-2 border-brand-300/50 animate-ring-pulse pointer-events-none" />

        {/* 主体圆形 */}
        <span className="relative flex h-full w-full items-center justify-center rounded-full border-[3px] border-white bg-gradient-to-br from-amber-50 via-white to-brand-50 shadow-lg shadow-brand-900/10 transition-all duration-300 group-hover:shadow-xl group-hover:shadow-brand-900/20 group-active:scale-[0.94]">
          <span className="animate-float">
            <SuanbaoMascot size="lg" />
          </span>
        </span>

        {/* 未读角标 */}
        {!isOpen && unreadCount > 0 && (
          <span className="absolute -right-0.5 -top-0.5 flex h-[22px] min-w-[22px] items-center justify-center rounded-full border-[3px] border-white bg-red-500 px-[5px] text-[10px] font-bold text-white shadow-sm animate-fade-in">
            {unreadCount >= 9 ? '9+' : unreadCount}
          </span>
        )}

        {/* 在线绿点 */}
        {!isOpen && unreadCount === 0 && (
          <span className="absolute right-[3px] top-[3px] h-[13px] w-[13px] rounded-full border-[3px] border-white bg-lime-400 shadow-sm" title="蒜宝在线" />
        )}
      </button>
      )}
    </aside>
  )
}
