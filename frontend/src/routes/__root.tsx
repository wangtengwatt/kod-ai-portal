import { createRootRoute, Outlet, useRouterState } from '@tanstack/react-router'
import { useEffect } from 'react'

import { GarlicGuideAssistant } from '@/components/assistant/GarlicGuideAssistant'
import { Footer } from '@/components/layout/Footer'
import { Navbar } from '@/components/layout/Navbar'

/** 根路由：全局布局。控制台路由使用独立侧边栏布局，其余页面使用 Navbar + Footer。 */
export const Route = createRootRoute({
  component: RootLayout,
})

function RootLayout() {
  const pathname = useRouterState({ select: (s) => s.location.pathname })
  const isConsole = pathname.startsWith('/console')

  // 滚动渐显（非控制台页面）
  useEffect(() => {
    if (isConsole) return
    const observer = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (entry.isIntersecting) entry.target.classList.add('visible')
        })
      },
      { threshold: 0.15, rootMargin: '0px 0px -40px 0px' },
    )
    document.querySelectorAll('.reveal').forEach((el) => observer.observe(el))
    return () => observer.disconnect()
  }, [pathname, isConsole])

  // 控制台：独立布局（由 console.tsx 侧边栏管理）
  if (isConsole) {
    return (
      <div className="bg-gray-50 text-gray-900">
        <Outlet />
      </div>
    )
  }

  // 官网页面：Navbar + Footer
  return (
    <div className="relative flex min-h-screen flex-col bg-white text-gray-900 overflow-hidden">
      <div className="pointer-events-none fixed inset-0 z-0 overflow-hidden">
        <div className="absolute -top-[30%] -right-[10%] h-[60%] w-[50%] rounded-full bg-brand-100/15 blur-[120px] animate-orb hidden sm:block" />
        <div className="absolute -bottom-[20%] -left-[5%] h-[50%] w-[40%] rounded-full bg-indigo-100/10 blur-[100px] animate-orb hidden sm:block" style={{ animationDelay: '-10s' }} />
      </div>

      <div className="relative z-10 flex min-h-screen flex-col">
        <Navbar />
        <main key={pathname} className="animate-page-enter flex-1">
          <Outlet />
        </main>
        <Footer />
      </div>
      <GarlicGuideAssistant />
    </div>
  )
}
