import { createRootRoute, Outlet, useRouterState } from '@tanstack/react-router'

import { Footer } from '@/components/layout/Footer'
import { Navbar } from '@/components/layout/Navbar'

/** 根路由：全局布局（顶部导航 + 内容出口 + 页脚）。 */
export const Route = createRootRoute({
  component: RootLayout,
})

function RootLayout() {
  const pathname = useRouterState({ select: (s) => s.location.pathname })

  return (
    <div className="flex min-h-screen flex-col bg-white text-gray-900">
      <Navbar />
      <main key={pathname} className="animate-page-enter flex-1">
        <Outlet />
      </main>
      <Footer />
    </div>
  )
}
