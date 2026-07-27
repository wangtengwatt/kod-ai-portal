import { Link, useNavigate } from '@tanstack/react-router'
import { useAuth } from '@/stores/auth'
import { KOD_WEB_URL } from '@/config'

/** 官网顶部导航：品牌标识 + tagline + 主要栏目 + 登录/用户状态 + 下载入口。 */
export function Navbar() {
  const { isAuthenticated, logout } = useAuth()
  const navigate = useNavigate()

  const navItems = [
    { to: '/', label: '首页' },
    { to: '/kai', label: 'KAI 期算', highlight: true },
    { to: '/features', label: '产品特性' },
    { to: '/pricing', label: '定价' },
    { to: '/download', label: '下载' },
    { to: '/changelog', label: '更新日志' },
  ] as const

  return (
    <header className="sticky top-0 z-50 border-b border-gray-100 bg-white/80 backdrop-blur">
      <nav className="mx-auto flex h-16 max-w-6xl items-center justify-between px-6">
        <Link to="/" className="flex items-center gap-2">
          <span className="flex h-8 w-8 items-center justify-center rounded-lg bg-brand-600 font-bold text-white">
            k
          </span>
          <span className="text-xl font-bold text-gray-900">KOD</span>
          <span className="hidden text-xs font-medium text-brand-600 lg:inline-block">
            蒜粒期货 AI 助手
          </span>
        </Link>

        <div className="hidden items-center gap-8 md:flex">
          {navItems.map((item) => (
            <Link
              key={item.to}
              to={item.to}
              className={`text-sm font-medium transition-colors ${
                'highlight' in item && item.highlight
                  ? 'text-brand-600 hover:text-brand-700'
                  : 'text-gray-600 hover:text-brand-600'
              }`}
              activeProps={{ className: 'text-brand-600' }}
            >
              {item.label}
            </Link>
          ))}
          {isAuthenticated ? (
            <>
              <a
                href={KOD_WEB_URL}
                target="_blank"
                rel="noopener noreferrer"
                className="rounded-lg border border-brand-600 px-4 py-2 text-sm font-semibold text-brand-600 transition-colors hover:bg-brand-50"
              >
                立即使用
              </a>
              <Link
                to="/api-config"
                className="rounded-lg border border-purple-400 px-4 py-2 text-sm font-semibold text-purple-600 transition-all hover:bg-purple-50 hover:border-purple-500 hover:shadow-[0_0_12px_rgba(168,85,247,0.15)]"
              >
                API Key
              </Link>
            </>
          ) : (
            <>
              <Link
                to="/login"
                search={{ redirect: KOD_WEB_URL }}
                className="rounded-lg border border-brand-600 px-4 py-2 text-sm font-semibold text-brand-600 transition-colors hover:bg-brand-50"
              >
                立即使用
              </Link>
              <Link
                to="/login"
                search={{ redirect: '/api-config' }}
                className="rounded-lg border border-purple-400 px-4 py-2 text-sm font-semibold text-purple-600 transition-all hover:bg-purple-50 hover:border-purple-500 hover:shadow-[0_0_12px_rgba(168,85,247,0.15)]"
              >
                API Key
              </Link>
            </>
          )}
        </div>

        <div className="flex items-center gap-3">
          {isAuthenticated ? (
            <button
              type="button"
              onClick={() => { logout(); navigate({ to: '/login' }) }}
              className="rounded-lg border border-gray-300 px-4 py-2 text-sm font-semibold text-gray-700 transition-colors hover:border-red-300 hover:text-red-600"
            >
              退出
            </button>
          ) : (
            <Link
              to="/login"
              className="rounded-lg border border-gray-300 px-4 py-2 text-sm font-semibold text-gray-700 transition-colors hover:border-brand-600 hover:text-brand-600"
            >
              登录
            </Link>
          )}
          <Link
            to="/download"
            className="rounded-lg bg-brand-600 px-4 py-2 text-sm font-semibold text-white transition-colors hover:bg-brand-700"
          >
            免费下载
          </Link>
        </div>
      </nav>
    </header>
  )
}
