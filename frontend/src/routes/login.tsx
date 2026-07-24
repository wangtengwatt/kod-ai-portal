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
    <svg
      className="h-5 w-5 text-gray-400"
      fill="none"
      viewBox="0 0 24 24"
      stroke="currentColor"
      strokeWidth={1.5}
    >
      <path
        strokeLinecap="round"
        strokeLinejoin="round"
        d="M21.75 6.75v10.5a2.25 2.25 0 01-2.25 2.25h-15a2.25 2.25 0 01-2.25-2.25V6.75m19.5 0A2.25 2.25 0 0019.5 4.5h-15a2.25 2.25 0 00-2.25 2.25m19.5 0v.243a2.25 2.25 0 01-1.07 1.916l-7.5 4.615a2.25 2.25 0 01-2.36 0L3.32 8.91a2.25 2.25 0 01-1.07-1.916V6.75"
      />
    </svg>
  )
}

function LockIcon() {
  return (
    <svg
      className="h-5 w-5 text-gray-400"
      fill="none"
      viewBox="0 0 24 24"
      stroke="currentColor"
      strokeWidth={1.5}
    >
      <path
        strokeLinecap="round"
        strokeLinejoin="round"
        d="M16.5 10.5V6.75a4.5 4.5 0 10-9 0v3.75m-.75 11.25h10.5a2.25 2.25 0 002.25-2.25v-6.75a2.25 2.25 0 00-2.25-2.25H6.75a2.25 2.25 0 00-2.25 2.25v6.75a2.25 2.25 0 002.25 2.25z"
      />
    </svg>
  )
}

function EyeIcon() {
  return (
    <svg
      className="h-5 w-5"
      fill="none"
      viewBox="0 0 24 24"
      stroke="currentColor"
      strokeWidth={1.5}
    >
      <path
        strokeLinecap="round"
        strokeLinejoin="round"
        d="M2.036 12.322a1.012 1.012 0 010-.639C3.423 7.51 7.36 4.5 12 4.5c4.638 0 8.573 3.007 9.963 7.178.07.207.07.431 0 .639C20.577 16.49 16.64 19.5 12 19.5c-4.638 0-8.573-3.007-9.963-7.178z"
      />
      <path
        strokeLinecap="round"
        strokeLinejoin="round"
        d="M15 12a3 3 0 11-6 0 3 3 0 016 0z"
      />
    </svg>
  )
}

function EyeOffIcon() {
  return (
    <svg
      className="h-5 w-5"
      fill="none"
      viewBox="0 0 24 24"
      stroke="currentColor"
      strokeWidth={1.5}
    >
      <path
        strokeLinecap="round"
        strokeLinejoin="round"
        d="M3.98 8.223A10.477 10.477 0 001.934 12C3.226 16.338 7.244 19.5 12 19.5c.993 0 1.953-.138 2.863-.395M6.228 6.228A10.45 10.45 0 0112 4.5c4.756 0 8.773 3.162 10.065 7.498a10.523 10.523 0 01-4.293 5.774M6.228 6.228L3 3m3.228 3.228l3.65 3.65m7.894 7.894L21 21m-3.228-3.228l-3.65-3.65m0 0a3 3 0 10-4.243-4.243m4.242 4.242L9.88 9.88"
      />
    </svg>
  )
}

function Spinner() {
  return (
    <svg
      className="animate-spin -ml-1 mr-2 h-4 w-4"
      fill="none"
      viewBox="0 0 24 24"
    >
      <circle
        className="opacity-25"
        cx="12"
        cy="12"
        r="10"
        stroke="currentColor"
        strokeWidth="4"
      />
      <path
        className="opacity-75"
        fill="currentColor"
        d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"
      />
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
  const [shakeError, setShakeError] = useState(false)

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setShakeError(false)

    if (!email.trim() || !password.trim()) {
      setError('请填写邮箱和密码')
      setShakeError(true)
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
      setShakeError(true)
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

      {/* ---- card ---- */}
      <div
        className={`w-full max-w-md animate-fade-in-up rounded-2xl border border-white/40 bg-white/70 p-8 shadow-xl shadow-brand-100/30 backdrop-blur-xl ${shakeError ? 'animate-shake' : ''}`}
      >
        {/* logo */}
        <div className="mb-8 text-center">
          <div className="mx-auto mb-4 flex h-12 w-12 items-center justify-center rounded-xl bg-brand-600 text-xl font-bold text-white shadow-lg shadow-brand-600/30">
            k
          </div>
          <h1 className="text-2xl font-bold text-gray-900">欢迎回来</h1>
          <p className="mt-1.5 text-sm text-gray-500">
            登录你的 kod 账号
          </p>
        </div>

        <form onSubmit={handleSubmit} className="space-y-5">
          {/* error toast */}
          {error && (
            <div className="animate-fade-in rounded-xl border border-red-100 bg-red-50/80 px-4 py-3 text-sm text-red-700 backdrop-blur">
              {error}
            </div>
          )}

          {/* email */}
          <div>
            <label
              htmlFor="login-email"
              className="mb-1.5 block text-sm font-medium text-gray-700"
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
                onChange={(e) => setEmail(e.target.value)}
                placeholder="your@email.com"
                className="block w-full rounded-xl border border-gray-200 bg-white/80 py-2.5 pl-10 pr-4 text-sm shadow-sm placeholder:text-gray-400 transition-all duration-200 focus:border-brand-500 focus:bg-white focus:outline-none focus:ring-2 focus:ring-brand-500/20"
              />
            </div>
          </div>

          {/* password */}
          <div>
            <label
              htmlFor="login-password"
              className="mb-1.5 block text-sm font-medium text-gray-700"
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
                onChange={(e) => setPassword(e.target.value)}
                placeholder="••••••••"
                className="block w-full rounded-xl border border-gray-200 bg-white/80 py-2.5 pl-10 pr-11 text-sm shadow-sm placeholder:text-gray-400 transition-all duration-200 focus:border-brand-500 focus:bg-white focus:outline-none focus:ring-2 focus:ring-brand-500/20"
              />
              <button
                type="button"
                onClick={() => setShowPwd(!showPwd)}
                className="absolute inset-y-0 right-0 flex items-center pr-3.5 text-gray-400 transition-colors hover:text-gray-600"
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
            className="flex w-full items-center justify-center rounded-xl bg-brand-600 py-2.5 text-sm font-semibold text-white shadow-lg shadow-brand-600/25 transition-all duration-200 hover:bg-brand-700 hover:shadow-brand-600/35 active:scale-[0.98] disabled:cursor-not-allowed disabled:opacity-60 disabled:active:scale-100"
          >
            {loading ? (
              <>
                <Spinner />
                登录中...
              </>
            ) : (
              '登录'
            )}
          </button>

          {/* switch to register */}
          <p className="text-center text-sm text-gray-500">
            还没有账号？{' '}
            <button
              type="button"
              className="font-semibold text-brand-600 transition-colors hover:text-brand-500"
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
