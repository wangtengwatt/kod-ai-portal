import { createFileRoute, redirect } from '@tanstack/react-router'
import { useQuery, useMutation } from '@tanstack/react-query'
import { useState } from 'react'
import { Plus, AlertCircle, Tag } from 'lucide-react'
import { authStore } from '@/stores/auth'
import { getTopUpInfo, getWallet, getTopUpHistory, createPay, type TopUpItem } from '@/api/wallet'

export const Route = createFileRoute('/console/wallet')({
  beforeLoad: ({ location }) => {
    if (!authStore.isAuthenticated) {
      throw redirect({ to: '/login', search: { redirect: location.href } })
    }
  },
  component: WalletPage,
})

const STATUS_STYLE: Record<string, string> = {
  success: 'bg-green-50 text-green-600', pending: 'bg-yellow-50 text-yellow-600',
  failed: 'bg-red-50 text-red-600', expired: 'bg-gray-50 text-gray-500',
}
const STATUS_LABEL: Record<string, string> = {
  success: '完成', pending: '处理中', failed: '失败', expired: '已过期',
}

/** 支付方式中文名称映射。 */
const PAY_METHOD_LABELS: Record<string, string> = {
  alipay: '支付宝',
  wxpay: '微信支付',
  online: '在线充值',
  stripe: 'Stripe',
  creem: 'Creem',
  waffo: 'Waffo',
  waffo_pancake: 'Waffo Pancake',
}

function payLabel(raw: string): string {
  return PAY_METHOD_LABELS[raw] || raw
}

/** 筛选标签定义 */
const FILTER_TABS = [
  { key: 'all', label: '全部' },
  { key: 'topup', label: '充值' },
  { key: 'consume', label: '消费' },
] as const

function WalletPage() {
  const [page, setPage] = useState(1)
  const [filter, setFilter] = useState<'all' | 'topup' | 'consume'>('all')
  const [showTopUp, setShowTopUp] = useState(false)
  const [topUpAmount, setTopUpAmount] = useState('')
  const [selectedMethod, setSelectedMethod] = useState<string>('')

  const walletQuery = useQuery({ queryKey: ['wallet'], queryFn: getWallet, staleTime: 30_000 })
  const topUpInfoQuery = useQuery({ queryKey: ['topup-info'], queryFn: getTopUpInfo, staleTime: 120_000 })
  const historyQuery = useQuery({
    queryKey: ['topup-history', page],
    queryFn: () => getTopUpHistory(page, 10),
    staleTime: 60_000,
  })

  const payMutation = useMutation({
    mutationFn: ({ amount, method }: { amount: number; method: string }) => createPay(amount, method),
    onSuccess: (data) => {
      setShowTopUp(false); setTopUpAmount('')
      if (data.paymentUrl) window.location.href = data.paymentUrl
    },
  })

  const isLoading = walletQuery.isLoading
  const balance = walletQuery.data?.balance ?? 0
  const topUpInfo = topUpInfoQuery.data
  const amountOptions = topUpInfo?.amount_options ?? [50, 100, 200, 500, 1000, 2000]
  const payMethods: string[] = topUpInfo?.pay_methods ?? []
  const onlineEnabled = topUpInfo?.enable_online_topup ?? true
  const minTopup = topUpInfo?.min_topup ?? 0

  // 折扣信息
  const discountMap = topUpInfo?.discount ?? null
  const hasDiscount = discountMap !== null && typeof discountMap === 'object' && Object.keys(discountMap).length > 0
  const discountValues = hasDiscount ? Object.values(discountMap!).filter((v) => typeof v === 'number' && v > 0 && v < 1) : []
  const showDiscountRate = discountValues.length > 0 ? Math.round((1 - discountValues[0]) * 100) : null

  const allItems: TopUpItem[] = historyQuery.data?.items ?? []
  const filtered = filter === 'all' ? allItems : filter === 'topup' ? allItems : []
  const totalPages = historyQuery.data ? Math.max(1, Math.ceil(historyQuery.data.total / (historyQuery.data.page_size || 10))) : 1

  const handleTopUp = () => {
    const amt = Number(topUpAmount)
    if (!amt || amt <= 0) return
    if (minTopup && amt < minTopup) {
      payMutation.reset() // 清除之前错误，但不能直接显示错误 — 用 alert 或内联提示
      return
    }
    payMutation.mutate({ amount: amt, method: selectedMethod || payMethods[0] || 'online' })
  }

  const handleOpenTopUp = () => {
    setShowTopUp(true)
    setSelectedMethod(payMethods[0] || '')
    setTopUpAmount('')
    payMutation.reset()
  }

  return (
    <div className="animate-fade-in space-y-6 p-6">
      <h1 className="text-2xl font-bold text-gray-900">钱包</h1>

      {(walletQuery.isError || topUpInfoQuery.isError) && (
        <div className="rounded-xl border border-red-200 bg-red-50/90 px-4 py-3 text-sm text-red-700">
          加载失败
          <button type="button" onClick={() => { walletQuery.refetch(); topUpInfoQuery.refetch() }}
            className="ml-3 underline underline-offset-2 hover:text-red-800">重试</button>
        </div>
      )}

      {/* Balance card */}
      <div className="overflow-hidden rounded-2xl bg-gradient-to-br from-gray-900 via-gray-800 to-gray-900 text-white shadow-xl">
        <div className="p-6 sm:p-8">
          <div className="flex flex-col gap-6 sm:flex-row sm:items-center sm:justify-between">
            <div>
              <p className="text-sm text-gray-400">可用余额</p>
              <p className="mt-1 text-4xl font-bold tracking-tight">
                {isLoading ? '...' : `¥${balance.toLocaleString(undefined, { minimumFractionDigits: 2 })}`}
              </p>
              <p className="mt-2 text-xs text-gray-500">余额可用于 API 调用消费</p>
            </div>
            <button type="button" onClick={handleOpenTopUp}
              className="inline-flex items-center gap-2 rounded-xl bg-brand-500 px-6 py-3 text-sm font-semibold text-white shadow-lg shadow-brand-500/30 transition-all hover:bg-brand-400 active:scale-[0.98]">
              <Plus className="h-4 w-4" />充值
            </button>
          </div>
        </div>
        <div className="grid grid-cols-2 border-t border-gray-700/50 bg-gray-800/30">
          <div className="px-6 py-3 text-center"><p className="text-xs text-gray-400">累计消费</p><p className="text-sm font-semibold">{isLoading ? '...' : `¥${(walletQuery.data?.historical_consumption ?? 0).toFixed(2)}`}</p></div>
          <div className="border-l border-gray-700/50 px-6 py-3 text-center"><p className="text-xs text-gray-400">充值次数</p><p className="text-sm font-semibold">{historyQuery.data?.total ?? 0} 笔</p></div>
        </div>
      </div>

      {/* Transactions */}
      <div className="rounded-2xl border border-gray-100 bg-white p-6 shadow-sm">
        <div className="mb-4 flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
          <h2 className="text-lg font-semibold text-gray-900">交易记录</h2>
          <div className="flex gap-1 rounded-lg bg-gray-100 p-1">
            {FILTER_TABS.map(({ key, label }) => (
              <button key={key} type="button" onClick={() => { setFilter(key); setPage(1) }}
                className={`rounded-md px-3 py-1.5 text-xs font-medium transition-all ${filter === key ? 'bg-white text-gray-900 shadow-sm' : 'text-gray-500 hover:text-gray-700'}`}>{label}</button>
            ))}
          </div>
        </div>
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead><tr className="border-b border-gray-100 text-left text-xs text-gray-500">
              <th className="pb-3 pr-4 font-medium">类型</th><th className="pb-3 pr-4 font-medium">支付方式</th>
              <th className="pb-3 pr-4 text-right font-medium">金额</th><th className="pb-3 pr-4 text-right font-medium hidden sm:table-cell">订单号</th>
              <th className="pb-3 pr-4 text-right font-medium hidden md:table-cell">时间</th><th className="pb-3 text-right font-medium hidden sm:table-cell">状态</th>
            </tr></thead>
            <tbody className="divide-y divide-gray-50">
              {historyQuery.isLoading ? Array.from({ length: 5 }).map((_, i) => (
                <tr key={i} className="animate-pulse">
                  <td className="py-3 pr-4"><div className="h-5 w-12 rounded bg-gray-100" /></td>
                  <td className="py-3 pr-4"><div className="h-5 w-16 rounded bg-gray-100" /></td>
                  <td className="py-3 pr-4 text-right"><div className="ml-auto h-5 w-16 rounded bg-gray-100" /></td>
                  <td className="py-3 pr-4 text-right hidden sm:table-cell"><div className="ml-auto h-5 w-20 rounded bg-gray-100" /></td>
                  <td className="py-3 pr-4 text-right hidden md:table-cell"><div className="ml-auto h-5 w-28 rounded bg-gray-100" /></td>
                  <td className="py-3 text-right hidden sm:table-cell"><div className="ml-auto h-5 w-12 rounded bg-gray-100" /></td>
                </tr>
              )) : filtered.map((tx) => (
                <tr key={tx.id} className="transition-colors hover:bg-gray-50">
                  <td className="py-3 pr-4"><span className="inline-flex items-center gap-1 rounded-full bg-green-100 px-2 py-0.5 text-xs font-medium text-green-700">充值</span></td>
                  <td className="py-3 pr-4 text-gray-700">{payLabel(tx.payment_provider) || payLabel(tx.payment_method)}</td>
                  <td className="py-3 pr-4 text-right font-mono font-medium text-green-600">+¥{Number(tx.money || tx.amount).toFixed(2)}</td>
                  <td className="py-3 pr-4 text-right text-gray-500 font-mono text-xs hidden sm:table-cell">{tx.trade_no ? tx.trade_no.slice(0, 12) + '...' : '-'}</td>
                  <td className="py-3 pr-4 text-right text-gray-400 hidden md:table-cell">{tx.create_time || '-'}</td>
                  <td className="py-3 text-right hidden sm:table-cell">
                    <span className={`inline-flex items-center rounded-full px-2 py-0.5 text-xs ${STATUS_STYLE[tx.status] || 'bg-gray-50 text-gray-500'}`}>{STATUS_LABEL[tx.status] || tx.status}</span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        {!historyQuery.isLoading && filtered.length === 0 && <p className="py-8 text-center text-sm text-gray-400">暂无记录</p>}
        {totalPages > 1 && (
          <div className="mt-4 flex items-center justify-between border-t border-gray-100 pt-4">
            <span className="text-xs text-gray-500">共 {historyQuery.data?.total ?? 0} 条，第 {page}/{totalPages} 页</span>
            <div className="flex gap-1">
              <button type="button" disabled={page <= 1} onClick={() => setPage((p) => Math.max(1, p - 1))}
                className="rounded-lg border border-gray-200 px-3 py-1.5 text-xs font-medium text-gray-600 hover:bg-gray-50 disabled:opacity-40">上一页</button>
              <button type="button" disabled={page >= totalPages} onClick={() => setPage((p) => p + 1)}
                className="rounded-lg border border-gray-200 px-3 py-1.5 text-xs font-medium text-gray-600 hover:bg-gray-50 disabled:opacity-40">下一页</button>
            </div>
          </div>
        )}
      </div>

      {/* Top-up modal */}
      {showTopUp && (
        <div className="fixed inset-0 z-[60] flex items-center justify-center px-4">
          <div className="fixed inset-0 bg-black/40" onClick={() => { if (!payMutation.isPending) { setShowTopUp(false); setTopUpAmount('') } }} />
          <div className="relative w-full max-w-sm animate-fade-in-up rounded-2xl border border-gray-100 bg-white p-6 shadow-2xl">
            <h3 className="text-lg font-semibold text-gray-900">账户充值</h3>
            <p className="mt-1 text-sm text-gray-500">选择或输入充值金额</p>

            {/* 在线充值未启用 */}
            {!onlineEnabled && (
              <div className="mt-4 flex items-center gap-2 rounded-lg border border-amber-200 bg-amber-50 px-3 py-2.5 text-sm text-amber-700">
                <AlertCircle className="h-4 w-4 shrink-0" />
                在线充值暂未开放，如有需要请联系客服
              </div>
            )}

            {/* 折扣横幅 */}
            {hasDiscount && (
              <div className="mt-4 flex items-center gap-2 rounded-lg border border-brand-100 bg-brand-50 px-3 py-2.5 text-sm text-brand-700">
                <Tag className="h-4 w-4 shrink-0" />
                {showDiscountRate !== null ? `当前享受 ${showDiscountRate}% 充值优惠` : '当前有充值优惠活动'}
              </div>
            )}

            <div className="mt-4 grid grid-cols-3 gap-2">
              {amountOptions.map((v) => (
                <button key={v} type="button" onClick={() => setTopUpAmount(String(v))}
                  className={`rounded-lg border px-3 py-2 text-sm font-medium transition-all ${topUpAmount === String(v) ? 'border-brand-500 bg-brand-50 text-brand-700' : 'border-gray-200 text-gray-700 hover:border-gray-300'}`}>¥{v}</button>
              ))}
            </div>
            <div className="mt-3 relative">
              <span className="pointer-events-none absolute inset-y-0 left-3 flex items-center text-gray-400">¥</span>
              <input type="number" value={topUpAmount} onChange={(e) => setTopUpAmount(e.target.value)} placeholder={minTopup ? `自定义金额（最低 ¥${minTopup}）` : '自定义金额'}
                className="w-full rounded-xl border border-gray-200 py-2.5 pl-8 pr-4 text-sm focus:border-brand-400 focus:outline-none focus:ring-2 focus:ring-brand-500/20" />
            </div>
            {minTopup > 0 && topUpAmount && Number(topUpAmount) < minTopup && (
              <p className="mt-1.5 text-xs text-red-500">最低充值金额为 ¥{minTopup}</p>
            )}
            {payMethods.length > 0 && (
              <div className="mt-3">
                <p className="mb-2 text-xs text-gray-500">支付方式</p>
                <div className="flex flex-wrap gap-2">
                  {payMethods.map((m) => (
                    <button key={m} type="button" onClick={() => setSelectedMethod(m)}
                      className={`rounded-lg border px-3 py-1.5 text-xs font-medium transition-all ${selectedMethod === m ? 'border-brand-500 bg-brand-50 text-brand-700' : 'border-gray-200 text-gray-600 hover:border-gray-300'}`}>{payLabel(m)}</button>
                  ))}
                </div>
              </div>
            )}
            {payMutation.isError && <p className="mt-2 text-xs text-red-600">{payMutation.error instanceof Error ? payMutation.error.message : '支付失败'}</p>}
            <div className="mt-5 flex gap-3">
              <button type="button" onClick={() => { setShowTopUp(false); setTopUpAmount('') }} disabled={payMutation.isPending}
                className="flex-1 rounded-xl border border-gray-200 py-2.5 text-sm font-medium text-gray-700 hover:bg-gray-50 disabled:opacity-50">取消</button>
              <button type="button" onClick={handleTopUp}
                disabled={!onlineEnabled || !topUpAmount || Number(topUpAmount) <= 0 || (minTopup > 0 && Number(topUpAmount) < minTopup) || payMutation.isPending}
                className="flex-1 rounded-xl bg-brand-600 py-2.5 text-sm font-semibold text-white shadow-sm hover:bg-brand-700 disabled:cursor-not-allowed disabled:opacity-50 active:scale-[0.98]">
                {payMutation.isPending ? '处理中...' : '确认充值'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
