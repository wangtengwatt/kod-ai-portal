import { Link } from '@tanstack/react-router'

/** 官网顶部导航：品牌标识 + 主要栏目 + 下载入口。 */
export function Navbar() {
  const navItems = [
    { to: '/', label: '首页' },
    { to: '/features', label: '产品特性' },
    { to: '/changelog', label: '更新日志' },
    { to: '/download', label: '下载' },
  ] as const

  return (
    <header className="sticky top-0 z-50 border-b border-gray-100 bg-white/80 backdrop-blur">
      <nav className="mx-auto flex h-16 max-w-6xl items-center justify-between px-6">
        <Link to="/" className="flex items-center gap-2">
          <span className="flex h-8 w-8 items-center justify-center rounded-lg bg-brand-600 font-bold text-white">
            k
          </span>
          <span className="text-xl font-bold text-gray-900">kod</span>
        </Link>

        <div className="hidden items-center gap-8 md:flex">
          {navItems.map((item) => (
            <Link
              key={item.to}
              to={item.to}
              className="text-sm font-medium text-gray-600 transition-colors hover:text-brand-600"
              activeProps={{ className: 'text-brand-600' }}
            >
              {item.label}
            </Link>
          ))}
        </div>

        <Link
          to="/download"
          className="rounded-lg bg-brand-600 px-4 py-2 text-sm font-semibold text-white transition-colors hover:bg-brand-700"
        >
          免费下载
        </Link>
      </nav>
    </header>
  )
}
