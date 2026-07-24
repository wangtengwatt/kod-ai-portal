import { createFileRoute, useNavigate } from '@tanstack/react-router'
import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react'
import { registerApi, sendCodeApi } from '@/api/auth'
import { useAuth } from '@/stores/auth'

/** 注册页：邮箱 + 验证码 + 密码 + 邀请码。 */
export const Route = createFileRoute('/register')({
  validateSearch: (search: Record<string, unknown>) => ({
    redirect:
      typeof search.redirect === 'string' && search.redirect.startsWith('/')
        ? search.redirect
        : '/',
  }),
  component: RegisterPage,
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
function HashIcon() {
  return (
    <svg className="h-5 w-5 text-gray-400 transition-colors group-focus-within:text-brand-500" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M5.25 8.25h15m-16.5 7.5h15m-1.8-13.5l-3.9 19.5m-2.1-19.5l-3.9 19.5" />
    </svg>
  )
}
function TicketIcon() {
  return (
    <svg className="h-5 w-5 text-gray-400 transition-colors group-focus-within:text-brand-500" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M16.5 6v.75m0 3v.75m0 3v.75m0 3V18m-9-5.25h5.25M7.5 15h3M3.375 5.25c-.621 0-1.125.504-1.125 1.125v3.026c.7.163 1.237.785 1.237 1.599s-.537 1.436-1.237 1.599v3.026c0 .621.504 1.125 1.125 1.125h17.25c.621 0 1.125-.504 1.125-1.125v-3.026c-.7-.163-1.237-.785-1.237-1.599s.537-1.436 1.237-1.599V6.375c0-.621-.504-1.125-1.125-1.125H3.375z" />
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
function SendIcon() {
  return (
    <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M6 12L3.269 3.126A59.768 59.768 0 0121.485 12 59.77 59.77 0 013.27 20.876L5.999 12zm0 0h7.5" />
    </svg>
  )
}
function CheckIcon() {
  return (
    <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2.5}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M4.5 12.75l6 6 9-13.5" />
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

/* ---------- 密码强度条 ---------- */
function passwordStrength(pwd: string): { score: number; label: string; color: string } {
  if (!pwd) return { score: 0, label: '', color: 'bg-gray-200' }
  let score = 0
  if (pwd.length >= 6) score++
  if (pwd.length >= 10) score++
  if (/[A-Z]/.test(pwd)) score++
  if (/[0-9]/.test(pwd)) score++
  if (/[^A-Za-z0-9]/.test(pwd)) score++
  if (score <= 1) return { score: 1, label: '弱', color: 'bg-red-400' }
  if (score <= 2) return { score: 2, label: '一般', color: 'bg-yellow-400' }
  if (score <= 3) return { score: 3, label: '良好', color: 'bg-green-400' }
  return { score: 4, label: '强', color: 'bg-green-500' }
}

/* ---------- component ---------- */

function RegisterPage() {
  const navigate = useNavigate()
  const { redirect } = Route.useSearch()
  const { login } = useAuth()

  const [email, setEmail] = useState('')
  const [code, setCode] = useState('')
  const [password, setPassword] = useState('')
  const [showPwd, setShowPwd] = useState(false)
  const [inviteCode, setInviteCode] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)
  const [shakeKey, setShakeKey] = useState(0)

  // 发送验证码
  const [sending, setSending] = useState(false)
  const [countdown, setCountdown] = useState(0)
  const [sendOk, setSendOk] = useState(false)
  const timerRef = useRef<ReturnType<typeof setInterval> | null>(null)

  useEffect(() => {
    if (countdown <= 0) {
      if (timerRef.current) { clearInterval(timerRef.current); timerRef.current = null }
      return
    }
    timerRef.current = setInterval(() => setCountdown((n) => n - 1), 1000)
    return () => { if (timerRef.current) clearInterval(timerRef.current) }
  }, [countdown])

  useEffect(() => () => { if (timerRef.current) clearInterval(timerRef.current) }, [])

  const handleSendCode = useCallback(async () => {
    if (!email.trim()) { setError('请先填写邮箱'); setShakeKey((k) => k + 1); return }
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.trim())) { setError('邮箱格式不正确'); setShakeKey((k) => k + 1); return }
    setError(null)
    setSending(true)
    try {
      await sendCodeApi(email.trim())
      setSendOk(true)
      setCountdown(60)
      setTimeout(() => setSendOk(false), 3000)
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : '发送失败，请重试')
      setShakeKey((k) => k + 1)
    } finally {
      setSending(false)
    }
  }, [email])

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    setError(null)

    if (!email.trim() || !password || !code.trim() || !inviteCode.trim()) {
      setError('请填写所有字段')
      setShakeKey((k) => k + 1)
      return
    }
    if (code.trim().length !== 6 || !/^\d+$/.test(code.trim())) {
      setError('验证码为 6 位数字')
      setShakeKey((k) => k + 1)
      return
    }
    if (password.length < 6) {
      setError('密码长度不能少于 6 位')
      setShakeKey((k) => k + 1)
      return
    }

    setLoading(true)
    try {
      const { token } = await registerApi(email.trim(), password, inviteCode.trim(), code.trim())
      login(token)
      await navigate({ to: redirect })
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : '注册失败，请重试')
      setShakeKey((k) => k + 1)
    } finally {
      setLoading(false)
    }
  }

  const strength = passwordStrength(password)

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
        <div className="animate-float absolute left-[8%] top-[18%] h-2 w-2 rounded-full bg-brand-300/30" style={{ animationDelay: '0s' }} />
        <div className="animate-float absolute left-[88%] top-[25%] h-1.5 w-1.5 rounded-full bg-brand-400/25" style={{ animationDelay: '0.7s' }} />
        <div className="animate-float absolute left-[12%] top-[78%] h-2.5 w-2.5 rounded-full bg-purple-300/25" style={{ animationDelay: '1.4s' }} />
        <div className="animate-float absolute left-[82%] top-[68%] h-1.5 w-1.5 rounded-full bg-brand-200/30" style={{ animationDelay: '2.1s' }} />
        <div className="animate-float absolute left-[55%] top-[8%] h-1 w-1 rounded-full bg-brand-400/30" style={{ animationDelay: '2.8s' }} />
        <div className="animate-float absolute left-[25%] top-[88%] h-2 w-2 rounded-full bg-purple-200/25" style={{ animationDelay: '3.5s' }} />
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
          <h1 className="text-2xl font-bold text-gray-900">创建账号</h1>
          <p className="mt-1.5 text-sm text-gray-500">
            验证邮箱，加入 kod
          </p>
        </div>

        <form onSubmit={handleSubmit} className="space-y-4">
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

          {/* email + send code */}
          <div className="group">
            <label htmlFor="reg-email" className="mb-1.5 block text-sm font-medium text-gray-700 transition-colors group-focus-within:text-brand-600">邮箱</label>
            <div className="flex gap-2">
              <div className="relative flex-1">
                <div className="pointer-events-none absolute inset-y-0 left-0 flex items-center pl-3.5">
                  <MailIcon />
                </div>
                <input
                  id="reg-email"
                  type="email"
                  autoComplete="email"
                  required
                  value={email}
                  onChange={(e) => { setEmail(e.target.value); setError(null) }}
                  placeholder="your@email.com"
                  className="block w-full rounded-xl border border-gray-200 bg-white/80 py-2.5 pl-10 pr-4 text-sm shadow-sm placeholder:text-gray-400 transition-all duration-300 focus:border-brand-400 focus:bg-white focus:outline-none focus:ring-2 focus:ring-brand-500/20 focus:shadow-md focus:shadow-brand-100/30"
                />
              </div>
              <button
                type="button"
                onClick={handleSendCode}
                disabled={sending || countdown > 0 || !email.trim()}
                className={`shrink-0 rounded-xl px-3.5 py-2.5 text-sm font-medium transition-all duration-300 active:scale-95 disabled:cursor-not-allowed ${
                  sendOk
                    ? 'animate-success-pop border border-green-400 bg-green-50 text-green-600'
                    : 'border border-brand-500 bg-white text-brand-600 hover:bg-brand-50 hover:shadow-md hover:shadow-brand-100/30 disabled:border-gray-200 disabled:text-gray-400 disabled:hover:shadow-none'
                }`}
              >
                <span className="flex items-center gap-1.5">
                  {sending ? <Spinner /> : sendOk ? <CheckIcon /> : countdown > 0 ? null : <SendIcon />}
                  {sending ? '发送中' : sendOk ? '已发送' : countdown > 0 ? `${countdown}s` : '获取验证码'}
                </span>
              </button>
            </div>
          </div>

          {/* verification code */}
          <div className="group">
            <label htmlFor="reg-code" className="mb-1.5 block text-sm font-medium text-gray-700 transition-colors group-focus-within:text-brand-600">验证码</label>
            <div className="relative">
              <div className="pointer-events-none absolute inset-y-0 left-0 flex items-center pl-3.5">
                <HashIcon />
              </div>
              <input
                id="reg-code"
                type="text"
                autoComplete="one-time-code"
                required
                maxLength={6}
                value={code}
                onChange={(e) => { setCode(e.target.value.replace(/\D/g, '').slice(0, 6)); setError(null) }}
                placeholder="6 位数字验证码"
                className="block w-full rounded-xl border border-gray-200 bg-white/80 py-2.5 pl-10 pr-4 text-sm tracking-[0.3em] shadow-sm placeholder:tracking-normal placeholder:text-gray-400 transition-all duration-300 focus:border-brand-400 focus:bg-white focus:outline-none focus:ring-2 focus:ring-brand-500/20 focus:shadow-md focus:shadow-brand-100/30"
              />
            </div>
          </div>

          {/* password */}
          <div className="group">
            <label htmlFor="reg-password" className="mb-1.5 block text-sm font-medium text-gray-700 transition-colors group-focus-within:text-brand-600">密码</label>
            <div className="relative">
              <div className="pointer-events-none absolute inset-y-0 left-0 flex items-center pl-3.5">
                <LockIcon />
              </div>
              <input
                id="reg-password"
                type={showPwd ? 'text' : 'password'}
                autoComplete="new-password"
                required
                minLength={6}
                value={password}
                onChange={(e) => { setPassword(e.target.value); setError(null) }}
                placeholder="至少 6 位"
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
            {/* 密码强度指示器 */}
            {password && (
              <div className="mt-2 animate-fade-in">
                <div className="flex h-1.5 gap-1 overflow-hidden rounded-full">
                  {[1, 2, 3, 4].map((level) => (
                    <div
                      key={level}
                      className={`h-full flex-1 rounded-full transition-all duration-500 ${
                        level <= strength.score ? strength.color : 'bg-gray-200'
                      }`}
                    />
                  ))}
                </div>
                <p className={`mt-1 text-xs font-medium transition-colors duration-300 ${
                  strength.score <= 1 ? 'text-red-500' :
                  strength.score <= 2 ? 'text-yellow-500' :
                  'text-green-500'
                }`}>
                  {strength.label}
                </p>
              </div>
            )}
          </div>

          {/* invite code */}
          <div className="group">
            <label htmlFor="reg-invite" className="mb-1.5 block text-sm font-medium text-gray-700 transition-colors group-focus-within:text-brand-600">邀请码</label>
            <div className="relative">
              <div className="pointer-events-none absolute inset-y-0 left-0 flex items-center pl-3.5">
                <TicketIcon />
              </div>
              <input
                id="reg-invite"
                type="text"
                autoComplete="off"
                required
                value={inviteCode}
                onChange={(e) => { setInviteCode(e.target.value); setError(null) }}
                placeholder="请输入邀请码"
                className="block w-full rounded-xl border border-gray-200 bg-white/80 py-2.5 pl-10 pr-4 text-sm shadow-sm placeholder:text-gray-400 transition-all duration-300 focus:border-brand-400 focus:bg-white focus:outline-none focus:ring-2 focus:ring-brand-500/20 focus:shadow-md focus:shadow-brand-100/30"
              />
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
                注册中...
              </span>
            ) : (
              '注册'
            )}
          </button>

          {/* switch to login */}
          <p className="text-center text-sm text-gray-500">
            已有账号？{' '}
            <button
              type="button"
              className="relative inline-block font-semibold text-brand-600 transition-all duration-200 hover:text-brand-500 after:absolute after:bottom-0 after:left-0 after:h-[1.5px] after:w-0 after:bg-brand-500 after:transition-all after:duration-300 hover:after:w-full"
              onClick={() =>
                navigate({ to: '/login', search: redirect !== '/' ? { redirect } : undefined })
              }
            >
              去登录
            </button>
          </p>
        </form>
      </div>
    </div>
  )
}
