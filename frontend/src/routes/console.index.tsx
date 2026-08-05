import { createFileRoute, redirect } from '@tanstack/react-router'
import { useQuery } from '@tanstack/react-query'
import { useAuth, authStore } from '@/stores/auth'
import { getDashboardSummary, getDashboardHourly } from '@/api/dashboard'
import { getWallet } from '@/api/wallet'
import { Coins, Activity, Key, Zap, Plus, ScrollText } from 'lucide-react'

export const Route = createFileRoute('/console/')({
  beforeLoad: ({ location }) => {
    if (!authStore.isAuthenticated) {
      throw redirect({ to: '/login', search: { redirect: location.href } })
    }
  },
  component: ConsoleDashboard,
})

function ConsoleDashboard() {
  const { token } = useAuth()

  const email = (() => {
    try {
      if (!token) return '未登录'
      const payload = token.split('.')[1]
      const decoded = JSON.parse(atob(payload))
      return decoded.sub || decoded.email || '用户'
    } catch { return '用户' }
  })()

  const summaryQuery = useQuery({
    queryKey: ['dashboard', 'summary'],
    queryFn: getDashboardSummary,
    staleTime: 30_000,
  })

  const walletQuery = useQuery({
    queryKey: ['wallet'],
    queryFn: getWallet,
    staleTime: 30_000,
  })

  const hourlyQuery = useQuery({
    queryKey: ['dashboard', 'hourly', 24],
    queryFn: () => getDashboardHourly(24),
    staleTime: 30_000,
  })

  const isLoading = summaryQuery.isLoading || walletQuery.isLoading
  const isError = summaryQuery.isError || walletQuery.isError

  const totalRequests = summaryQuery.data?.reduce((s, m) => s + m.totalRequests, 0) ?? 0
  const totalTokens = summaryQuery.data?.reduce((s, m) => s + m.totalTokens, 0) ?? 0
  const totalQuota = summaryQuery.data?.reduce((s, m) => s + m.totalQuota, 0) ?? 0
  const modelCount = summaryQuery.data?.length ?? 0
  const balance = walletQuery.data?.balance ?? 0

  const hourlyNonZero = (hourlyQuery.data ?? []).filter((h) => h.requestCount > 0)

  return (
    <div className="animate-fade-in space-y-6 p-6">
      {/* Header */}
      <div>
        <h1 className="text-2xl font-bold text-gray-900">欢迎回来，{email}</h1>
        <p className="mt-1 text-sm text-gray-500">以下是你的账户概览</p>
      </div>

      {/* Error */}
      {isError && (
        <div className="rounded-xl border border-red-200 bg-red-50/90 px-4 py-3 text-sm text-red-700">
          加载失败：{summaryQuery.error instanceof Error ? summaryQuery.error.message : walletQuery.error instanceof Error ? walletQuery.error.message : '未知错误'}
          <button type="button" onClick={() => { summaryQuery.refetch(); walletQuery.refetch() }}
            className="ml-3 underline underline-offset-2 hover:text-red-800">重试</button>
        </div>
      )}

      {/* Stats grid */}
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <StatCard title="账户余额" value={isLoading ? '...' : `¥${balance.toFixed(2)}`} icon={Coins} sub="活跃模型" subValue={`${modelCount} 个`} />
        <StatCard title="累计调用" value={isLoading ? '...' : totalRequests.toLocaleString()} icon={Activity} sub="次请求" />
        <StatCard title="累计配额" value={isLoading ? '...' : totalQuota.toLocaleString()} icon={Key} sub="消耗" />
        <StatCard title="累计 Token" value={isLoading ? '...' : totalTokens >= 1_000_000 ? (totalTokens / 1_000_000).toFixed(1) + 'M' : totalTokens.toLocaleString()} icon={Zap} sub="总计" />
      </div>

      {/* Quick actions */}
      <div className="rounded-2xl border border-gray-100 bg-white p-6 shadow-sm">
        <h2 className="mb-4 text-lg font-semibold text-gray-900">快捷操作</h2>
        <div className="flex flex-wrap gap-3">
          <a href="/console/wallet" className="inline-flex items-center gap-2 rounded-lg bg-brand-600 px-4 py-2.5 text-sm font-semibold text-white shadow-sm transition-all hover:bg-brand-700 active:scale-[0.98]">
            <Plus className="h-4 w-4" />充值
          </a>
          <a href="/console/logs" className="inline-flex items-center gap-2 rounded-lg border border-gray-200 bg-white px-4 py-2.5 text-sm font-semibold text-gray-700 transition-all hover:bg-gray-50 active:scale-[0.98]">
            <ScrollText className="h-4 w-4" />查看日志
          </a>
        </div>
      </div>

      {/* Recent hourly usage */}
      <div className="rounded-2xl border border-gray-100 bg-white p-6 shadow-sm">
        <div className="mb-4 flex items-center justify-between">
          <h2 className="text-lg font-semibold text-gray-900">最近 24 小时用量</h2>
          <a href="/console/logs" className="text-sm font-medium text-brand-600 hover:text-brand-700">查看全部 →</a>
        </div>
        {hourlyQuery.isLoading ? (
          <div className="space-y-3">
            {Array.from({ length: 5 }).map((_, i) => (
              <div key={i} className="flex items-center gap-4 rounded-lg border border-gray-50 px-4 py-3 animate-pulse">
                <div className="h-9 w-9 rounded-lg bg-gray-100" />
                <div className="flex-1 space-y-2"><div className="h-4 w-32 rounded bg-gray-100" /><div className="h-3 w-20 rounded bg-gray-50" /></div>
                <div className="h-3 w-16 rounded bg-gray-50" />
              </div>
            ))}
          </div>
        ) : hourlyNonZero.length > 0 ? (
          <div className="space-y-3">
            {hourlyNonZero.slice().reverse().slice(0, 10).map((h) => (
              <div key={h.hourBucket} className="flex items-center gap-4 rounded-lg border border-gray-50 px-4 py-3 transition-colors hover:bg-gray-50">
                <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-green-100 text-green-600">
                  <Activity className="h-5 w-5" />
                </div>
                <div className="flex-1 min-w-0">
                  <p className="text-sm font-medium text-gray-900">API 调用<span className="ml-1 font-normal text-gray-500">· {h.requestCount} 次请求</span></p>
                  <p className="text-xs text-gray-500">{h.tokenUsed.toLocaleString()} tokens · {h.quota.toFixed(2)} 配额</p>
                </div>
                <span className="shrink-0 text-xs text-gray-400">{h.hourBucket}</span>
              </div>
            ))}
          </div>
        ) : (
          <p className="py-8 text-center text-sm text-gray-400">暂无用量数据</p>
        )}
      </div>
    </div>
  )
}

function StatCard({ title, value, icon: Icon, sub, subValue }: {
  title: string; value: string; icon: React.FC<{ className?: string }>; sub: string; subValue?: string
}) {
  return (
    <div className="rounded-2xl border border-gray-100 bg-white p-5 shadow-sm transition-shadow hover:shadow-md">
      <div className="mb-3 flex items-center justify-between">
        <span className="text-sm text-gray-500">{title}</span>
        <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-brand-50 text-brand-600">
          <Icon className="h-5 w-5" />
        </div>
      </div>
      <p className="text-2xl font-bold text-gray-900">{value}</p>
      <p className="mt-1 text-xs text-gray-500">{sub}{subValue ? ` ${subValue}` : ''}</p>
    </div>
  )
}
