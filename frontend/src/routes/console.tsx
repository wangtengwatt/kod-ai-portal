import { createFileRoute, Link, Outlet, useRouterState } from '@tanstack/react-router'
import { useAuth } from '@/stores/auth'
import { useState } from 'react'
import { LayoutDashboard, BarChart3, Wallet, ScrollText, Home, LogOut } from 'lucide-react'

/**
 * 控制台布局路由 —— 深色侧边栏 + 内容区。
 * 所有 /console/* 页面共享此布局。
 */
export const Route = createFileRoute('/console')({
  component: ConsoleLayout,
})

const NAV_ITEMS = [
  { to: '/console', label: '概览', icon: LayoutDashboard, exact: true },
  { to: '/console/dashboard', label: '看板', icon: BarChart3 },
  { to: '/console/wallet', label: '钱包', icon: Wallet },
  { to: '/console/logs', label: '操作日志', icon: ScrollText },
] as const

function ConsoleLayout() {
  const { logout } = useAuth()
  const pathname = useRouterState({ select: (s) => s.location.pathname })
  const [sidebarOpen, setSidebarOpen] = useState(false)

  const isActive = (to: string, exact?: boolean) => {
    if (exact) return pathname === to
    return pathname.startsWith(to)
  }

  return (
    <div className="flex h-screen overflow-hidden bg-gray-50">
      {/* Mobile overlay */}
      {sidebarOpen && (
        <div className="fixed inset-0 z-40 bg-black/30 lg:hidden" onClick={() => setSidebarOpen(false)} />
      )}

      {/* Sidebar */}
      <aside
        className={`fixed inset-y-0 left-0 z-50 flex w-64 flex-col bg-gray-900 text-gray-300 transition-transform lg:static lg:translate-x-0 ${
          sidebarOpen ? 'translate-x-0' : '-translate-x-full'
        }`}
      >
        {/* Logo */}
        <div className="flex h-16 items-center gap-3 border-b border-gray-800 px-5">
          <span className="flex h-9 w-9 items-center justify-center rounded-xl bg-gradient-to-br from-brand-500 to-brand-700 text-lg font-bold text-white shadow-lg shadow-brand-500/20">K</span>
          <div>
            <p className="text-sm font-semibold text-white">KOD 控制台</p>
          </div>
        </div>

        {/* Nav */}
        <nav className="flex-1 space-y-0.5 overflow-y-auto px-3 py-4">
          {NAV_ITEMS.map((item) => {
            const active = isActive(item.to, 'exact' in item ? item.exact : false)
            return (
              <Link
                key={item.to}
                to={item.to}
                onClick={() => setSidebarOpen(false)}
                className={`flex items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-medium transition-all ${
                  active
                    ? 'bg-brand-600/20 text-white shadow-sm'
                    : 'text-gray-400 hover:bg-gray-800 hover:text-gray-200'
                }`}
              >
                <item.icon className={`h-5 w-5 ${active ? 'text-brand-400' : 'text-gray-500'}`} />
                {item.label}
              </Link>
            )
          })}
        </nav>

        {/* Bottom */}
        <div className="border-t border-gray-800 p-4">
          <Link
            to="/"
            className="mb-2 flex items-center gap-2 rounded-lg px-3 py-2 text-sm text-gray-400 transition-colors hover:bg-gray-800 hover:text-gray-200"
          >
            <Home className="h-4 w-4" />
            返回官网
          </Link>
          <button
            type="button"
            onClick={() => { logout(); window.location.href = '/login' }}
            className="flex w-full items-center gap-2 rounded-lg px-3 py-2 text-sm text-gray-400 transition-colors hover:bg-red-900/30 hover:text-red-400"
          >
            <LogOut className="h-4 w-4" />
            退出登录
          </button>
        </div>
      </aside>

      {/* Main area */}
      <div className="flex flex-1 flex-col overflow-hidden">
        {/* Mobile top bar */}
        <header className="flex h-14 items-center justify-between border-b border-gray-200 bg-white px-4 lg:hidden">
          <button type="button" onClick={() => setSidebarOpen(true)}
            className="rounded-lg p-2 text-gray-600 hover:bg-gray-100">
            <svg className="h-6 w-6" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M3.75 6.75h16.5M3.75 12h16.5m-16.5 5.25h16.5" />
            </svg>
          </button>
          <span className="text-sm font-semibold text-gray-700">KOD 控制台</span>
          <div className="w-10" />
        </header>

        {/* Content */}
        <main className="flex-1 overflow-y-auto">
          <Outlet />
        </main>
      </div>
    </div>
  )
}
