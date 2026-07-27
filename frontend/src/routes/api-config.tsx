import { createFileRoute, useNavigate } from '@tanstack/react-router'
import { useState, useCallback, useEffect, type FormEvent } from 'react'
import { Key, Eye, EyeOff, Check, Sparkles, Shield, ArrowRight } from 'lucide-react'
import { useAuth } from '@/stores/auth'
import { saveApiKey } from '@/api/auth'

export const Route = createFileRoute('/api-config')({
  component: ApiConfigPage,
})

/* ---------- inline icons ---------- */

function Spinner() {
  return (
    <svg className="-ml-1 mr-2 h-4 w-4 animate-spin" fill="none" viewBox="0 0 24 24">
      <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
      <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
    </svg>
  )
}

function HexagonGrid() {
  return (
    <svg className="pointer-events-none absolute inset-0 h-full w-full opacity-[0.03]" xmlns="http://www.w3.org/2000/svg">
      <defs>
        <pattern id="hexagons" width="50" height="86.6" patternUnits="userSpaceOnUse" patternTransform="scale(1.5)">
          <path
            d="M25 0L50 14.4v28.8L25 57.7L0 43.3V14.4z"
            fill="none"
            stroke="currentColor"
            strokeWidth="1"
            className="text-brand-600"
          />
          <path
            d="M25 86.6L50 72.2V43.4L25 57.7L0 43.3v28.9z"
            fill="none"
            stroke="currentColor"
            strokeWidth="1"
            className="text-brand-600"
          />
        </pattern>
      </defs>
      <rect width="100%" height="100%" fill="url(#hexagons)" />
    </svg>
  )
}

/* ---------- component ---------- */

function ApiConfigPage() {
  const navigate = useNavigate()
  const { token, isAuthenticated } = useAuth()

  // Redirect to login if not authenticated
  useEffect(() => {
    if (!isAuthenticated || !token) {
      navigate({ to: '/login', search: { redirect: '/api-config' } })
    }
  }, [isAuthenticated, token, navigate])

  const [apiKey, setApiKey] = useState('')
  const [showKey, setShowKey] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [success, setSuccess] = useState(false)
  const [loading, setLoading] = useState(false)
  const [shakeKey, setShakeKey] = useState(0)
  const [focusGlow, setFocusGlow] = useState(false)

  // Don't render the page while redirecting
  if (!isAuthenticated || !token) {
    return null
  }

  const handleSubmit = useCallback(
    async (e: FormEvent) => {
      e.preventDefault()
      setError(null)
      setSuccess(false)

      if (!apiKey.trim()) {
        setError('请输入 API Key')
        setShakeKey((k) => k + 1)
        return
      }

      setLoading(true)
      try {
        await saveApiKey(token!, apiKey.trim())
        setSuccess(true)
        setApiKey('')
      } catch (err: unknown) {
        setError(err instanceof Error ? err.message : '保存失败，请重试')
        setShakeKey((k) => k + 1)
      } finally {
        setLoading(false)
      }
    },
    [apiKey, token],
  )

  return (
    <div className="relative flex min-h-[calc(100vh-10rem)] items-center justify-center overflow-hidden px-4">
      {/* ---- hexagon grid ---- */}
      <HexagonGrid />

      {/* ---- animated background blobs ---- */}
      <div className="pointer-events-none absolute inset-0 -z-10">
        <div className="animate-blob absolute -top-40 -right-32 h-80 w-80 rounded-full bg-brand-100/50 blur-3xl" />
        <div className="animate-blob animation-delay-2000 absolute -bottom-40 -left-32 h-72 w-72 rounded-full bg-purple-100/50 blur-3xl" />
        <div className="animate-blob animation-delay-4000 absolute top-1/2 left-1/3 h-64 w-64 rounded-full bg-cyan-100/40 blur-3xl" />
      </div>

      {/* ---- floating particles ---- */}
      <div className="pointer-events-none absolute inset-0 -z-10 overflow-hidden">
        {[
          { l: '5%', t: '12%', s: 'h-2 w-2', d: '0s', c: 'bg-brand-300/30' },
          { l: '90%', t: '18%', s: 'h-1.5 w-1.5', d: '0.7s', c: 'bg-purple-400/25' },
          { l: '12%', t: '80%', s: 'h-2.5 w-2.5', d: '1.4s', c: 'bg-cyan-300/25' },
          { l: '85%', t: '72%', s: 'h-1.5 w-1.5', d: '2.1s', c: 'bg-brand-200/30' },
          { l: '45%', t: '8%', s: 'h-1 w-1', d: '2.8s', c: 'bg-brand-400/30' },
          { l: '35%', t: '88%', s: 'h-2 w-2', d: '3.5s', c: 'bg-purple-200/25' },
          { l: '70%', t: '55%', s: 'h-1.5 w-1.5', d: '4.2s', c: 'bg-cyan-200/30' },
          { l: '22%', t: '45%', s: 'h-1 w-1', d: '5s', c: 'bg-brand-300/25' },
        ].map((p, i) => (
          <div
            key={i}
            className={`animate-float absolute ${p.s} rounded-full ${p.c}`}
            style={{ left: p.l, top: p.t, animationDelay: p.d }}
          />
        ))}
      </div>

      {/* ---- scanning line ---- */}
      <div className="pointer-events-none absolute inset-0 -z-10 overflow-hidden">
        <div
          className="absolute left-0 h-[1px] w-full animate-scan bg-gradient-to-r from-transparent via-brand-400/20 to-transparent"
          style={{ top: '40%' }}
        />
      </div>

      {/* ---- card ---- */}
      <div
        key={shakeKey}
        className="relative w-full max-w-lg animate-fade-in-up rounded-2xl border border-white/30 bg-white/60 p-8 shadow-2xl shadow-brand-200/20 backdrop-blur-2xl"
      >
        {/* card top glow bar */}
        <div className="absolute inset-x-6 -top-px h-px bg-gradient-to-r from-transparent via-brand-400/50 to-transparent" />

        {/* success overlay */}
        {success && (
          <div className="absolute inset-0 z-10 flex flex-col items-center justify-center rounded-2xl bg-white/90 backdrop-blur-sm animate-success-pop">
            <div className="flex h-16 w-16 items-center justify-center rounded-full bg-green-100 ring-4 ring-green-50">
              <Check className="h-8 w-8 text-green-600" strokeWidth={2.5} />
            </div>
            <h3 className="mt-4 text-xl font-bold text-gray-900">配置成功！</h3>
            <p className="mt-1.5 text-sm text-gray-500">API Key 已安全保存到中转站</p>
            <button
              type="button"
              onClick={() => setSuccess(false)}
              className="mt-6 inline-flex items-center gap-2 rounded-lg bg-brand-600 px-5 py-2.5 text-sm font-semibold text-white shadow-sm transition-all hover:bg-brand-700 active:scale-[0.98]"
            >
              继续配置
              <ArrowRight className="h-4 w-4" />
            </button>
          </div>
        )}

        {/* header */}
        <div className="mb-8 text-center">
          <div className="animate-pulse-glow mx-auto mb-4 flex h-14 w-14 items-center justify-center rounded-2xl bg-gradient-to-br from-brand-500 via-purple-500 to-cyan-500 text-xl font-bold text-white shadow-lg shadow-brand-500/25">
            <Key className="h-6 w-6" />
          </div>
          <h1 className="text-2xl font-bold text-gray-900">API Key 配置</h1>
          <p className="mt-1.5 text-sm text-gray-500">
            为你关联的中转站设置 API 访问密钥
          </p>
        </div>

        {/* info card */}
        <div className="mb-6 flex items-start gap-3 rounded-xl border border-brand-100 bg-brand-50/60 p-4">
          <Shield className="mt-0.5 h-5 w-5 shrink-0 text-brand-500" />
          <div className="text-sm text-gray-600">
            <p className="font-medium text-gray-800">安全提示</p>
            <p className="mt-0.5 leading-relaxed">
              API Key 将加密传输并安全存储在你的中转站中。请妥善保管，不要泄露给他人。
            </p>
          </div>
        </div>

        <form onSubmit={handleSubmit} className="space-y-5">
          {/* error toast with shake */}
          {error && (
            <div
              key={error}
              className="animate-shake rounded-xl border border-red-200 bg-red-50/90 px-4 py-3 text-sm text-red-700 backdrop-blur shadow-sm shadow-red-100/50"
            >
              <span className="flex items-center gap-2">
                <svg className="h-4 w-4 shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                  <path strokeLinecap="round" strokeLinejoin="round" d="M12 9v3.75m9-.75a9 9 0 11-18 0 9 9 0 0118 0zm-9 3.75h.008v.008H12v-.008z" />
                </svg>
                {error}
              </span>
            </div>
          )}

          {/* api key input */}
          <div className="group">
            <label
              htmlFor="apikey-input"
              className="mb-1.5 block text-sm font-medium text-gray-700 transition-colors group-focus-within:text-brand-600"
            >
              API Key
            </label>
            <div
              className={`relative rounded-xl transition-all duration-500 ${
                focusGlow
                  ? 'ring-2 ring-brand-400/30 shadow-lg shadow-brand-200/30'
                  : 'ring-1 ring-gray-200 shadow-sm'
              }`}
            >
              <div className="pointer-events-none absolute inset-y-0 left-0 flex items-center pl-3.5">
                <Key className="h-5 w-5 text-gray-400 transition-colors group-focus-within:text-brand-500" />
              </div>
              <input
                id="apikey-input"
                type={showKey ? 'text' : 'password'}
                autoComplete="off"
                required
                value={apiKey}
                onChange={(e) => {
                  setApiKey(e.target.value)
                  setError(null)
                  setSuccess(false)
                }}
                onFocus={() => setFocusGlow(true)}
                onBlur={() => setFocusGlow(false)}
                placeholder="sk-xxxxxxxxxxxxxxxxxxxxxxxx"
                className="block w-full rounded-xl border-0 bg-white/80 py-3 pl-10 pr-11 text-sm shadow-sm placeholder:text-gray-400 transition-all duration-300 focus:bg-white focus:outline-none"
              />
              <button
                type="button"
                onClick={() => setShowKey(!showKey)}
                className="absolute inset-y-0 right-0 flex items-center pr-3.5 text-gray-400 transition-all duration-200 hover:text-gray-600 hover:scale-110"
                tabIndex={-1}
              >
                {showKey ? <EyeOff className="h-5 w-5" /> : <Eye className="h-5 w-5" />}
              </button>
            </div>
            <p className="mt-1.5 text-xs text-gray-400">
              支持任何兼容的 API Key 格式，如 sk-、api- 等前缀
            </p>
          </div>

          {/* submit */}
          <button
            type="submit"
            disabled={loading}
            className="relative flex w-full items-center justify-center overflow-hidden rounded-xl bg-gradient-to-r from-brand-600 via-purple-600 to-cyan-600 bg-[length:200%_100%] py-3 text-sm font-semibold text-white shadow-lg shadow-brand-500/25 transition-all duration-500 hover:bg-right hover:shadow-brand-500/35 active:scale-[0.98] disabled:cursor-not-allowed disabled:opacity-60 disabled:active:scale-100 disabled:hover:bg-left"
          >
            {loading ? (
              <span className="flex items-center">
                <Spinner />
                保存中...
              </span>
            ) : (
              <span className="flex items-center gap-2">
                <Sparkles className="h-4 w-4" />
                确认配置
              </span>
            )}
          </button>
        </form>
      </div>
    </div>
  )
}
