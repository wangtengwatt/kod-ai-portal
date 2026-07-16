import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createRouter, RouterProvider } from '@tanstack/react-router'

import { routeTree } from './routeTree.gen'
import './styles/index.css'

// TanStack Query 客户端：官网数据（如下载版本、公告）后续接入后端接口
const queryClient = new QueryClient()

// TanStack Router 实例：路由树由 @tanstack/router-plugin 构建时生成
const router = createRouter({ routeTree })

// 为 router 提供类型注册，获得类型安全的 <Link> 与导航
declare module '@tanstack/react-router' {
  interface Register {
    router: typeof router
  }
}

const rootEl = document.getElementById('root')
if (!rootEl) {
  throw new Error('根节点 #root 不存在，无法挂载应用')
}

createRoot(rootEl).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
    </QueryClientProvider>
  </StrictMode>
)
