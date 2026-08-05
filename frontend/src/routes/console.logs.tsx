import { createFileRoute, redirect } from '@tanstack/react-router'
import { useQuery, useMutation } from '@tanstack/react-query'
import { useState, useMemo } from 'react'
import { Activity, Play, Square, Loader2 } from 'lucide-react'
import { authStore } from '@/stores/auth'
import { getDashboardHourly, type HourlyTrendItem } from '@/api/dashboard'
import { startSync, stopSync, getSyncStatus } from '@/api/log-sync'

export const Route = createFileRoute('/console/logs')({
  beforeLoad: ({ location }) => {
    if (!authStore.isAuthenticated) {
      throw redirect({ to: '/login', search: { redirect: location.href } })
    }
  },
  component: LogsPage,
})

const PAGE_SIZE = 10
const HOURS_RANGE = 72

function LogsPage() {
  const [search, setSearch] = useState('')
  const [page, setPage] = useState(0)

  const syncQuery = useQuery({
    queryKey: ['sync-status'],
    queryFn: getSyncStatus,
    staleTime: 10_000,
    refetchInterval: 15_000,
  })

  const startMutation = useMutation({ mutationFn: startSync, onSuccess: () => syncQuery.refetch() })
  const stopMutation = useMutation({ mutationFn: stopSync, onSuccess: () => syncQuery.refetch() })

  const hourlyQuery = useQuery({
    queryKey: ['dashboard', 'hourly', HOURS_RANGE],
    queryFn: () => getDashboardHourly(HOURS_RANGE),
    staleTime: 30_000,
  })

  const isSyncing = syncQuery.data?.syncing ?? false
  const hourlyData: HourlyTrendItem[] = hourlyQuery.data ?? []

  const filtered = useMemo(() => {
    let list = hourlyData.filter((h) => h.requestCount > 0)
    if (search.trim()) {
      const q = search.trim().toLowerCase()
      list = list.filter((h) => h.hourBucket.includes(q) || String(h.requestCount).includes(q))
    }
    return list.reverse()
  }, [hourlyData, search])

  const totalPages = Math.max(1, Math.ceil(filtered.length / PAGE_SIZE))
  const paged = filtered.slice(page * PAGE_SIZE, (page + 1) * PAGE_SIZE)
  const syncError = startMutation.error || stopMutation.error

  return (
    <div className="animate-fade-in space-y-6 p-6">
      <div>
        <h1 className="text-2xl font-bold text-gray-900">操作日志</h1>
        <p className="mt-1 text-sm text-gray-500">查看 API 用量记录和日志同步状态</p>
      </div>

      {/* Sync status */}
      <div className="rounded-2xl border border-gray-100 bg-white p-5 shadow-sm">
        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
          <div className="flex items-center gap-3">
            <div className={`flex h-10 w-10 items-center justify-center rounded-xl ${isSyncing ? 'bg-green-100' : 'bg-gray-100'}`}>
              <Activity className={`h-5 w-5 ${isSyncing ? 'text-green-600' : 'text-gray-400'}`} />
            </div>
            <div>
              <p className="font-semibold text-gray-900">{syncQuery.isLoading ? '检查中...' : isSyncing ? '日志同步运行中' : '日志同步已停止'}</p>
              <p className="text-sm text-gray-500">{isSyncing ? '后台正在自动同步 API 调用日志' : '点击启动按钮开始同步 API 调用日志'}</p>
            </div>
          </div>
          <div className="flex gap-2">
            {isSyncing ? (
              <button type="button" onClick={() => stopMutation.mutate()} disabled={stopMutation.isPending}
                className="inline-flex items-center gap-2 rounded-lg border border-red-200 px-4 py-2 text-sm font-medium text-red-600 hover:bg-red-50 disabled:opacity-50">
                {stopMutation.isPending ? <><Loader2 className="h-4 w-4 animate-spin" />停止中</> : <><Square className="h-4 w-4" />停止同步</>}
              </button>
            ) : (
              <button type="button" onClick={() => startMutation.mutate()} disabled={startMutation.isPending}
                className="inline-flex items-center gap-2 rounded-lg bg-brand-600 px-4 py-2 text-sm font-medium text-white hover:bg-brand-700 disabled:opacity-50">
                {startMutation.isPending ? <><Loader2 className="h-4 w-4 animate-spin" />启动中</> : <><Play className="h-4 w-4" />启动同步</>}
              </button>
            )}
          </div>
        </div>
        {syncError && <p className="mt-3 text-sm text-red-600">{syncError instanceof Error ? syncError.message : '操作失败'}</p>}
      </div>

      {/* Usage table */}
      <div className="rounded-2xl border border-gray-100 bg-white shadow-sm overflow-hidden">
        <div className="flex items-center justify-between border-b border-gray-100 px-5 py-3.5">
          <h2 className="text-sm font-semibold text-gray-900">小时用量记录<span className="ml-2 font-normal text-gray-400">（共 {filtered.length} 条）</span></h2>
          <div className="relative">
            <div className="pointer-events-none absolute inset-y-0 left-0 flex items-center pl-3">
              <svg className="h-4 w-4 text-gray-400" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}><path strokeLinecap="round" strokeLinejoin="round" d="M21 21l-5.197-5.197m0 0A7.5 7.5 0 105.196 5.196a7.5 7.5 0 0010.607 10.607z" /></svg>
            </div>
            <input type="text" value={search} onChange={(e) => { setSearch(e.target.value); setPage(0) }}
              placeholder="搜索..." className="w-full rounded-lg border border-gray-200 bg-white py-2 pl-9 pr-4 text-sm focus:border-brand-400 focus:outline-none focus:ring-2 focus:ring-brand-500/20 sm:w-56" />
          </div>
        </div>
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead><tr className="border-b border-gray-100 text-left text-xs text-gray-500">
              <th className="pb-3 pl-5 pr-4 font-medium">时间桶</th>
              <th className="pb-3 pr-4 text-right font-medium">请求数</th>
              <th className="pb-3 pr-4 text-right font-medium">配额消耗</th>
              <th className="pb-3 pr-4 text-right font-medium">Token 消耗</th>
              <th className="pb-3 pr-5 text-right font-medium hidden sm:table-cell">流式请求</th>
            </tr></thead>
            <tbody className="divide-y divide-gray-50">
              {hourlyQuery.isLoading ? Array.from({ length: 8 }).map((_, i) => (
                <tr key={i} className="animate-pulse">
                  <td className="py-3 pl-5 pr-4"><div className="h-4 w-32 rounded bg-gray-100" /></td>
                  <td className="py-3 pr-4 text-right"><div className="ml-auto h-4 w-16 rounded bg-gray-100" /></td>
                  <td className="py-3 pr-4 text-right"><div className="ml-auto h-4 w-20 rounded bg-gray-100" /></td>
                  <td className="py-3 pr-4 text-right"><div className="ml-auto h-4 w-24 rounded bg-gray-100" /></td>
                  <td className="py-3 pr-5 text-right hidden sm:table-cell"><div className="ml-auto h-4 w-12 rounded bg-gray-100" /></td>
                </tr>
              )) : paged.map((h, i) => (
                <tr key={`${h.hourBucket}-${i}`} className="transition-colors hover:bg-gray-50">
                  <td className="py-3 pl-5 pr-4">
                    <span className="inline-flex items-center gap-1.5">
                      <span className={`h-2 w-2 rounded-full ${h.requestCount > 100 ? 'bg-purple-400' : h.requestCount > 10 ? 'bg-green-400' : 'bg-gray-300'}`} />
                      <span className="font-mono text-gray-700">{h.hourBucket}</span>
                    </span>
                  </td>
                  <td className="py-3 pr-4 text-right font-mono text-gray-900">{h.requestCount.toLocaleString()}</td>
                  <td className="py-3 pr-4 text-right font-mono text-gray-700">{h.quota.toFixed(2)}</td>
                  <td className="py-3 pr-4 text-right font-mono text-gray-700">{h.tokenUsed.toLocaleString()}</td>
                  <td className="py-3 pr-5 text-right text-gray-500 hidden sm:table-cell">{h.streamCount > 0 ? h.streamCount.toLocaleString() : '-'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        {!hourlyQuery.isLoading && filtered.length === 0 && (
          <div className="py-16 text-center">
            <Activity className="mx-auto h-10 w-10 text-gray-300 mb-3" />
            <p className="text-sm text-gray-500">{search ? '暂无匹配的用量记录' : '暂无用量数据，请先启动日志同步'}</p>
          </div>
        )}
        {totalPages > 1 && (
          <div className="flex items-center justify-between border-t border-gray-100 px-5 py-3">
            <span className="text-xs text-gray-500">共 {filtered.length} 条，第 {page + 1}/{totalPages} 页</span>
            <div className="flex gap-1">
              <button type="button" disabled={page === 0} onClick={() => setPage(page - 1)}
                className="rounded-lg border border-gray-200 px-3 py-1.5 text-xs font-medium text-gray-600 hover:bg-gray-50 disabled:opacity-40">上一页</button>
              <button type="button" disabled={page >= totalPages - 1} onClick={() => setPage(page + 1)}
                className="rounded-lg border border-gray-200 px-3 py-1.5 text-xs font-medium text-gray-600 hover:bg-gray-50 disabled:opacity-40">下一页</button>
            </div>
          </div>
        )}
      </div>
    </div>
  )
}
