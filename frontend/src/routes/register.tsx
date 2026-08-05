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

function RegisterPage() {
  const navigate = useNavigate()
  const { redirect } = Route.useSearch()
  const { login } = useAuth()
  const timerRef = useRef<ReturnType<typeof setInterval> | null>(null)

  const [email, setEmail] = useState('')
  const [code, setCode] = useState('')
  const [password, setPassword] = useState('')
  const [inviteCode, setInviteCode] = useState('')
  const [showPwd, setShowPwd] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)
  const [sending, setSending] = useState(false)
  const [sendOk, setSendOk] = useState(false)
  const [countdown, setCountdown] = useState(0)

  useEffect(() => {
    return () => { if (timerRef.current) clearInterval(timerRef.current) }
  }, [])

  const startCountdown = useCallback(() => {
    setCountdown(60)
    timerRef.current = setInterval(() => {
      setCountdown((c) => { if (c <= 1) { if (timerRef.current) clearInterval(timerRef.current); return 0 }; return c - 1 })
    }, 1000)
  }, [])

  const handleSendCode = useCallback(async () => {
    if (!email.trim()) { setError('请先输入邮箱'); return }
    setSending(true); setError(null); setSendOk(false)
    try {
      await sendCodeApi(email.trim())
      setSendOk(true)
      startCountdown()
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : '发送失败')
    } finally { setSending(false) }
  }, [email, startCountdown])

  const passwordStrength = (pwd: string): { label: string; color: string; width: string } => {
    if (pwd.length < 6) return { label: '弱', color: 'bg-red-500', width: 'w-1/4' }
    if (pwd.length < 8) return { label: '一般', color: 'bg-orange-500', width: 'w-2/4' }
    if (pwd.length < 10 || !/[A-Z]/.test(pwd) || !/[0-9]/.test(pwd)) return { label: '良好', color: 'bg-amber-500', width: 'w-3/4' }
    return { label: '强', color: 'bg-emerald-500', width: 'w-full' }
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault(); setError(null)
    if (!email.trim() || !password.trim() || !code.trim() || !inviteCode.trim()) {
      setError('请填写所有字段'); return
    }
    setLoading(true)
    try {
      const { token, relayMessage } = await registerApi(email.trim(), password, inviteCode.trim(), code.trim())
      login(token)
      if (relayMessage) alert(relayMessage)
      await navigate({ to: redirect })
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : '注册失败，请重试')
    } finally { setLoading(false) }
  }

  const strength = passwordStrength(password)

  return (
    <div className="relative flex min-h-[calc(100vh-10rem)] items-center justify-center overflow-hidden px-4">
      <div className="pointer-events-none absolute inset-0 -z-10">
        <div className="animate-blob absolute -top-40 -right-32 h-72 w-72 rounded-full bg-brand-100/60 blur-3xl" />
        <div className="animate-blob animation-delay-2000 absolute -bottom-32 -left-32 h-80 w-80 rounded-full bg-brand-50/80 blur-3xl" />
      </div>
      <div className="w-full max-w-md animate-fade-in-up rounded-2xl border border-white/40 bg-white/70 p-8 shadow-xl backdrop-blur-xl">
        <div className="mb-8 text-center">
          <h1 className="text-2xl font-bold text-gray-900">注册账号</h1>
          <p className="mt-1.5 text-sm text-gray-500">使用邀请码注册 KOD</p>
        </div>

        <form onSubmit={handleSubmit} className="space-y-4">
          {error && (
            <div className="animate-shake rounded-xl border border-red-200 bg-red-50/90 px-4 py-3 text-sm text-red-700">{error}</div>
          )}

          <div className="group">
            <label className="mb-1.5 block text-sm font-medium text-gray-700">邮箱</label>
            <div className="relative">
              <div className="pointer-events-none absolute inset-y-0 left-0 flex items-center pl-3.5"><MailIcon /></div>
              <input type="email" autoComplete="email" required value={email} onChange={e => { setEmail(e.target.value); setError(null) }} placeholder="your@email.com"
                className="block w-full rounded-xl border border-gray-200 bg-white/80 py-2.5 pl-10 pr-4 text-sm shadow-sm focus:border-brand-400 focus:outline-none focus:ring-2 focus:ring-brand-500/20" />
            </div>
          </div>

          <div>
            <label className="mb-1.5 block text-sm font-medium text-gray-700">验证码</label>
            <div className="flex gap-2">
              <input type="text" maxLength={6} required value={code} onChange={e => { setCode(e.target.value); setError(null) }} placeholder="6位验证码"
                className="flex-1 rounded-xl border border-gray-200 bg-white/80 py-2.5 px-4 text-sm shadow-sm focus:border-brand-400 focus:outline-none focus:ring-2 focus:ring-brand-500/20" />
              <button type="button" disabled={sending || countdown > 0} onClick={handleSendCode}
                className="shrink-0 rounded-xl bg-brand-50 px-4 py-2.5 text-sm font-medium text-brand-600 hover:bg-brand-100 disabled:opacity-50">
                {countdown > 0 ? `${countdown}s` : sending ? '发送中' : sendOk ? '已发送' : '发送验证码'}
              </button>
            </div>
          </div>

          <div className="group">
            <label className="mb-1.5 block text-sm font-medium text-gray-700">密码</label>
            <div className="relative">
              <div className="pointer-events-none absolute inset-y-0 left-0 flex items-center pl-3.5"><LockIcon /></div>
              <input type={showPwd ? 'text' : 'password'} autoComplete="new-password" required minLength={8} value={password} onChange={e => { setPassword(e.target.value); setError(null) }} placeholder="至少 8 位"
                className="block w-full rounded-xl border border-gray-200 bg-white/80 py-2.5 pl-10 pr-11 text-sm shadow-sm focus:border-brand-400 focus:outline-none focus:ring-2 focus:ring-brand-500/20" />
              <button type="button" onClick={() => setShowPwd(!showPwd)} className="absolute inset-y-0 right-0 flex items-center pr-3.5 text-gray-400 hover:text-gray-600" tabIndex={-1}>
                {showPwd ? <EyeOffIcon /> : <EyeIcon />}
              </button>
            </div>
            {password && (
              <div className="mt-2 flex items-center gap-2">
                <div className="h-1.5 flex-1 rounded-full bg-gray-200"><div className={`h-full rounded-full transition-all ${strength.color} ${strength.width}`} /></div>
                <span className="text-xs text-gray-500">{strength.label}</span>
              </div>
            )}
          </div>

          <div>
            <label className="mb-1.5 block text-sm font-medium text-gray-700">邀请码</label>
            <input type="text" required value={inviteCode} onChange={e => { setInviteCode(e.target.value); setError(null) }} placeholder="输入邀请码"
              className="block w-full rounded-xl border border-gray-200 bg-white/80 py-2.5 px-4 text-sm shadow-sm focus:border-brand-400 focus:outline-none focus:ring-2 focus:ring-brand-500/20" />
          </div>

          <button type="submit" disabled={loading} className="w-full rounded-xl bg-gradient-to-r from-brand-600 to-brand-700 py-3 text-sm font-semibold text-white shadow-lg hover:from-brand-500 active:scale-[0.98] disabled:opacity-60">
            {loading ? <span className="flex items-center justify-center"><Spinner />注册中...</span> : '注册'}
          </button>
        </form>
        <p className="mt-4 text-center text-xs text-gray-400">
          已有账号？<a href="/login" className="ml-1 font-medium text-brand-600 hover:text-brand-700">去登录</a>
        </p>
      </div>
    </div>
  )
}
