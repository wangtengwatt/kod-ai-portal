import { createFileRoute, useNavigate } from '@tanstack/react-router'
import { useState, type FormEvent } from 'react'
import { loginApi } from '@/api/auth'
import { useAuth } from '@/stores/auth'

/** 登录页：邮箱 + 密码。 */
export const Route = createFileRoute('/login')({
  validateSearch: (search: Record<string, unknown>) => ({
    redirect:
      typeof search.redirect === 'string' &&
      (search.redirect.startsWith('/') || search.redirect.startsWith('http'))
        ? search.redirect
        : '/',
  }),
  component: LoginPage,
})

/* ---------- inline SVG icons ---------- */
function MailIcon() {
  return (
    <svg className="h-5 w-5 text-gray-400 transition-colors group-focus-within:text-brand-500" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M21.75 6.75v10.5a2.25 2.25 0 01-2.25 2.25h-15a2.25 2.25 0 01-2.25-2.25V6.75m19.5 0A2.25 2.25 0 0019.5 4.5h-15a2.25 2.25 0 00-2.25 2.25m19.5 0v.243a2.25 2.25 0 01-1.07 1.916l-7.5 4.615a2.25 2.25 0 01-2.36 0L3.32 8.91a2.25 2.25 0 01-1.07-1.916V6.75" />
    </svg>
  )
}

function LockIcon() {
  return (
    <svg className="h-5 w-5 text-gray-400 transition-colors group-focus-within:text-brand-500" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M16.5 10.5V6.75a4.5 4.5 0 10-9 0v3.75m-.75 11.25h10.5a2.25 2.25 0 002.25-2.25v-6.75a2.25 2.25 0 00-2.25-2.25H6.75a2.25 2.25 0 00-2.25 2.25v6.75a2.25 2.25 0 002.25 2.25z" />
    </svg>
  )
}

function EyeIcon() {
  return (
    <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M2.036 12.322a1.012 1.012 0 010-.639C3.423 7.51 7.36 4.5 12 4.5c4.638 0 8.573 3.007 9.963 7.178.07.207.07.431 0 .639C20.577 16.49 16.64 19.5 12 19.5c-4.638 0-8.573-3.007-9.963-7.178z" />
      <path strokeLinecap="round" strokeLinejoin="round" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
    </svg>
  )
}

function EyeOffIcon() {
  return (
    <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M3.98 8.223A10.477 10.477 0 001.934 12C3.226 16.338 7.244 19.5 12 19.5c.993 0 1.953-.138 2.863-.395M6.228 6.228A10.45 10.45 0 0112 4.5c4.756 0 8.773 3.162 10.065 7.498a10.523 10.523 0 01-4.293 5.774M6.228 6.228L3 3m3.228 3.228l3.65 3.65m7.894 7.894L21 21m-3.228-3.228l-3.65-3.65m0 0a3 3 0 10-4.243-4.243m4.242 4.242L9.88 9.88" />
    </svg>
  )
}

function Spinner() {
  return (
    <svg className="animate-spin -ml-1 mr-2 h-4 w-4" fill="none" viewBox="0 0 24 24">
      <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
      <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
    </svg>
  )
}

/* ---------- component ---------- */

function LoginPage() {
  const navigate = useNavigate()
  const { redirect } = Route.useSearch()
  const { login } = useAuth()

  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [showPwd, setShowPwd] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)
  const [shakeKey, setShakeKey] = useState(0) // 每次递增以重新触发抖动

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    setError(null)

    if (!email.trim() || !password.trim()) {
      setError('请填写邮箱和密码')
      setShakeKey((k) => k + 1)
      return
    }

    setLoading(true)
    try {
      const { token } = await loginApi(email.trim(), password)
      login(token)
      if (redirect.startsWith('http')) {
        window.location.href = redirect
      } else {
        await navigate({ to: redirect })
      }
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : '登录失败，请重试')
      setShakeKey((k) => k + 1)
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="relative flex min-h-[calc(100vh-10rem)] items-center justify-center overflow-hidden px-4">
      {/* ---- animated background blobs ---- */}
      <div className="pointer-events-none absolute inset-0 -z-10">
        <div className="animate-blob absolute -top-40 -right-32 h-72 w-72 rounded-full bg-brand-100/60 blur-3xl" />
        <div className="animate-blob animation-delay-2000 absolute -bottom-32 -left-32 h-80 w-80 rounded-full bg-brand-50/80 blur-3xl" />
        <div className="animate-blob animation-delay-4000 absolute top-1/3 left-1/3 h-64 w-64 rounded-full bg-purple-100/40 blur-3xl" />
      </div>

      {/* ---- floating particles ---- */}
      <div className="pointer-events-none absolute inset-0 -z-10 overflow-hidden">
        <div className="animate-float absolute left-[10%] top-[15%] h-2 w-2 rounded-full bg-brand-300/30" style={{ animationDelay: '0s' }} />
        <div className="animate-float absolute left-[85%] top-[20%] h-1.5 w-1.5 rounded-full bg-brand-400/25" style={{ animationDelay: '0.8s' }} />
        <div className="animate-float absolute left-[15%] top-[75%] h-2.5 w-2.5 rounded-full bg-purple-300/25" style={{ animationDelay: '1.6s' }} />
        <div className="animate-float absolute left-[80%] top-[70%] h-1.5 w-1.5 rounded-full bg-brand-200/30" style={{ animationDelay: '2.4s' }} />
        <div className="animate-float absolute left-[50%] top-[10%] h-1 w-1 rounded-full bg-brand-400/30" style={{ animationDelay: '3.2s' }} />
        <div className="animate-float absolute left-[30%] top-[85%] h-2 w-2 rounded-full bg-purple-200/25" style={{ animationDelay: '4s' }} />
      </div>

      {/* ---- card ---- */}
      <div
        key={shakeKey}
        className="w-full max-w-md animate-fade-in-up rounded-2xl border border-white/40 bg-white/70 p-8 shadow-xl shadow-brand-100/30 backdrop-blur-xl"
      >
        {/* logo with glow */}
        <div className="mb-8 text-center">
          <div className="animate-pulse-glow mx-auto mb-4 flex h-14 w-14 items-center justify-center rounded-2xl bg-gradient-to-br from-brand-500 to-brand-700 text-xl font-bold text-white shadow-lg shadow-brand-500/30">
            k
          </div>
          <h1 className="text-2xl font-bold text-gray-900">欢迎回来</h1>
          <p className="mt-1.5 text-sm text-gray-500">
            登录你的 kod 账号
          </p>
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

          {/* email */}
          <div className="group">
            <label
              htmlFor="login-email"
              className="mb-1.5 block text-sm font-medium text-gray-700 transition-colors group-focus-within:text-brand-600"
            >
              邮箱
            </label>
            <div className="relative">
              <div className="pointer-events-none absolute inset-y-0 left-0 flex items-center pl-3.5">
                <MailIcon />
              </div>
              <input
                id="login-email"
                type="email"
                autoComplete="email"
                required
                value={email}
                onChange={(e) => { setEmail(e.target.value); setError(null) }}
                placeholder="your@email.com"
                className="block w-full rounded-xl border border-gray-200 bg-white/80 py-2.5 pl-10 pr-4 text-sm shadow-sm placeholder:text-gray-400 transition-all duration-300 focus:border-brand-400 focus:bg-white focus:outline-none focus:ring-2 focus:ring-brand-500/20 focus:shadow-md focus:shadow-brand-100/30"
              />
            </div>
          </div>

          {/* password */}
          <div className="group">
            <label
              htmlFor="login-password"
              className="mb-1.5 block text-sm font-medium text-gray-700 transition-colors group-focus-within:text-brand-600"
            >
              密码
            </label>
            <div className="relative">
              <div className="pointer-events-none absolute inset-y-0 left-0 flex items-center pl-3.5">
                <LockIcon />
              </div>
              <input
                id="login-password"
                type={showPwd ? 'text' : 'password'}
                autoComplete="current-password"
                required
                value={password}
                onChange={(e) => { setPassword(e.target.value); setError(null) }}
                placeholder="••••••••"
                className="block w-full rounded-xl border border-gray-200 bg-white/80 py-2.5 pl-10 pr-11 text-sm shadow-sm placeholder:text-gray-400 transition-all duration-300 focus:border-brand-400 focus:bg-white focus:outline-none focus:ring-2 focus:ring-brand-500/20 focus:shadow-md focus:shadow-brand-100/30"
              />
              <button
                type="button"
                onClick={() => setShowPwd(!showPwd)}
                className="absolute inset-y-0 right-0 flex items-center pr-3.5 text-gray-400 transition-all duration-200 hover:text-gray-600 hover:scale-110"
                tabIndex={-1}
              >
                {showPwd ? <EyeOffIcon /> : <EyeIcon />}
              </button>
            </div>
          </div>

          {/* submit */}
          <button
            type="submit"
            disabled={loading}
            className="relative flex w-full items-center justify-center overflow-hidden rounded-xl bg-gradient-to-r from-brand-600 to-brand-500 py-2.5 text-sm font-semibold text-white shadow-lg shadow-brand-500/25 transition-all duration-300 hover:from-brand-700 hover:to-brand-600 hover:shadow-brand-500/35 active:scale-[0.98] disabled:cursor-not-allowed disabled:opacity-60 disabled:active:scale-100 disabled:hover:from-brand-600 disabled:hover:to-brand-500"
          >
            {loading ? (
              <span className="flex items-center">
                <Spinner />
                登录中...
              </span>
            ) : (
              '登录'
            )}
          </button>

          {/* switch to register */}
          <p className="text-center text-sm text-gray-500">
            还没有账号？{' '}
            <button
              type="button"
              className="relative inline-block font-semibold text-brand-600 transition-all duration-200 hover:text-brand-500 after:absolute after:bottom-0 after:left-0 after:h-[1.5px] after:w-0 after:bg-brand-500 after:transition-all after:duration-300 hover:after:w-full"
              onClick={() =>
                navigate({
                  to: '/register',
                  search: redirect !== '/' ? { redirect } : undefined,
                })
              }
            >
              立即注册
            </button>
          </p>
        </form>
      </div>
    </div>
  )
}
