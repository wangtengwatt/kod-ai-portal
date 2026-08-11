import { Link, useNavigate, useRouterState } from '@tanstack/react-router'
import { useCallback, useEffect, useState } from 'react'
import { Menu, X } from 'lucide-react'
import { useAuth } from '@/stores/auth'
import { KOD_WEB_URL } from '@/config'

const navItems = [
  { to: '/', label: '首页' },
  { to: '/kai', label: 'KAI 期算', highlight: true },
  { to: '/features', label: '产品特性' },
  { to: '/download', label: '下载' },
  { to: '/changelog', label: '更新日志' },
] as const

export function Navbar() {
  const { isAuthenticated, logout } = useAuth()
  const navigate = useNavigate()
  const pathname = useRouterState({ select: (s) => s.location.pathname })
  const [scrolled, setScrolled] = useState(false)
  const [menuOpen, setMenuOpen] = useState(false)

  useEffect(() => {
    const onScroll = () => setScrolled(window.scrollY > 10)
    onScroll()
    window.addEventListener('scroll', onScroll, { passive: true })
    return () => window.removeEventListener('scroll', onScroll)
  }, [])

  useEffect(() => { setMenuOpen(false) }, [pathname])
  useEffect(() => {
    if (!menuOpen) return
    document.body.style.overflow = 'hidden'
    return () => { document.body.style.overflow = '' }
  }, [menuOpen])

  const closeAndNav = useCallback((to: string) => {
    setMenuOpen(false)
    navigate({ to })
  }, [navigate])

  const sharedLinkClass = (active: boolean, highlight?: boolean) =>
    `relative px-3 py-2 text-sm font-medium transition-colors rounded-lg ${
      active
        ? 'text-brand-600 bg-brand-50'
        : highlight
          ? 'text-brand-600/80 hover:text-brand-600 hover:bg-brand-50'
          : 'text-gray-600 hover:text-brand-600 hover:bg-gray-50'
    }`

  return (
    <header className={`sticky top-0 z-50 transition-all duration-300 ${scrolled ? 'border-b border-gray-100/80 bg-white/90 shadow-sm shadow-brand-900/3 backdrop-blur-lg' : 'border-b border-transparent bg-white/70 backdrop-blur'}`}>
      <nav className="mx-auto flex h-14 max-w-6xl items-center justify-between px-4 sm:h-16 sm:px-6">
        <Link to="/" className="flex shrink-0 items-center gap-2">
          <span className="flex h-8 w-8 items-center justify-center rounded-lg bg-brand-600 font-bold text-white shadow-sm shadow-brand-600/25">K</span>
          <span className="text-lg font-bold text-gray-900 sm:text-xl">KOD</span>
        </Link>

        <div className="hidden items-center gap-1 md:flex">
          {navItems.map((item) => {
            const active = pathname === item.to
            return (
              <Link key={item.to} to={item.to} className={sharedLinkClass(active, item.highlight)}>
                {item.label}
                {active && <span className="absolute bottom-0 left-1/2 -translate-x-1/2 h-[2px] w-5 rounded-full bg-brand-500 animate-underline" />}
              </Link>
            )
          })}
          {isAuthenticated ? (
            <>
              <a
                href={KOD_WEB_URL}
                target="_blank"
                rel="noopener noreferrer"
                className="ml-2 rounded-lg bg-brand-600 px-4 py-2 text-sm font-semibold text-white transition-all hover:bg-brand-700 shadow-sm shadow-brand-600/20"
              >
                立即使用
              </a>
              <Link to="/console" className="ml-1 rounded-lg border border-gray-300 px-4 py-2 text-sm font-semibold text-gray-700 transition-all hover:border-brand-600 hover:text-brand-600">
                控制台
              </Link>
            </>
          ) : (
            <Link to="/login" search={{ redirect: KOD_WEB_URL }} className="ml-4 rounded-lg border border-brand-600 px-4 py-2 text-sm font-semibold text-brand-600 transition-all hover:bg-brand-50">
              立即使用
            </Link>
          )}
        </div>

        <div className="flex items-center gap-2 sm:gap-3">
          {!isAuthenticated && (
            <Link to="/login" className="rounded-lg px-2.5 py-1.5 text-xs font-medium text-gray-600 hover:text-brand-600 sm:hidden">登录</Link>
          )}
          {isAuthenticated && (
            <button onClick={() => { logout(); navigate({ to: '/login' }) }} className="rounded-lg px-2 py-1.5 text-xs font-medium text-gray-500 hover:text-red-500 sm:hidden">退出</button>
          )}
          <div className="hidden sm:flex sm:items-center sm:gap-3">
            {isAuthenticated ? (
              <button onClick={() => { logout(); navigate({ to: '/login' }) }} className="rounded-lg border border-gray-300 px-3 py-2 text-sm font-semibold text-gray-700 transition-colors hover:border-red-300 hover:text-red-600">退出</button>
            ) : (
              <Link to="/login" className="rounded-lg border border-gray-300 px-3 py-2 text-sm font-semibold text-gray-700 transition-colors hover:border-brand-600 hover:text-brand-600">登录</Link>
            )}
          </div>
          <Link to="/download" className="btn-shimmer rounded-lg bg-brand-600 px-3 py-2 text-xs font-semibold text-white transition-all hover:bg-brand-700 shadow-sm shadow-brand-600/20 sm:px-4 sm:text-sm">免费下载</Link>

          <button type="button" onClick={() => setMenuOpen((v) => !v)}
            className="flex h-9 w-9 items-center justify-center rounded-lg text-gray-600 hover:bg-gray-100 md:hidden"
            aria-label={menuOpen ? '关闭菜单' : '打开菜单'}>
            {menuOpen ? <X className="h-5 w-5" /> : <Menu className="h-5 w-5" />}
          </button>
        </div>
      </nav>

      {menuOpen && (
        <div className="fixed inset-0 top-14 z-40 md:hidden">
          <div className="absolute inset-0 bg-black/20 backdrop-blur-sm" onClick={() => setMenuOpen(false)} />
          <div className="absolute inset-x-0 top-0 bg-white border-b border-gray-100 shadow-lg animate-fade-in-down">
            <div className="mx-auto max-w-lg px-4 py-4 space-y-1">
              {navItems.map((item) => {
                const active = pathname === item.to
                return (
                  <Link key={item.to} to={item.to}
                    className={`flex items-center gap-3 rounded-xl px-4 py-3 text-base font-medium transition-colors ${active ? 'bg-brand-50 text-brand-600' : 'text-gray-700 hover:bg-gray-50'}`}
                    onClick={() => setMenuOpen(false)}>
                    {item.label}
                    {active && <span className="ml-auto h-1.5 w-1.5 rounded-full bg-brand-500" />}
                  </Link>
                )
              })}
              <hr className="my-3" />
              {isAuthenticated ? (
                <>
                  <a
                    href={KOD_WEB_URL}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="flex items-center gap-3 rounded-xl bg-brand-600 px-4 py-3 text-base font-semibold text-white"
                    onClick={() => setMenuOpen(false)}
                  >
                    立即使用
                  </a>
                  <Link to="/console" className="flex items-center gap-3 rounded-xl border border-gray-200 px-4 py-3 text-base font-medium text-gray-700 hover:bg-gray-50" onClick={() => setMenuOpen(false)}>控制台</Link>
                  <button onClick={() => { logout(); closeAndNav('/login') }} className="w-full rounded-xl px-4 py-3 text-base font-medium text-gray-500 hover:bg-gray-50 text-left">退出登录</button>
                </>
              ) : (
                <>
                  <Link to="/login" search={{ redirect: KOD_WEB_URL }} className="flex items-center gap-3 rounded-xl bg-brand-600 px-4 py-3 text-base font-semibold text-white" onClick={() => setMenuOpen(false)}>立即使用</Link>
                  <Link to="/register" className="flex items-center gap-3 rounded-xl border border-gray-200 px-4 py-3 text-base font-medium text-gray-700 hover:bg-gray-50" onClick={() => setMenuOpen(false)}>注册账号</Link>
                </>
              )}
            </div>
          </div>
        </div>
      )}
    </header>
  )
}
