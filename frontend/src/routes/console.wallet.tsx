import { createFileRoute, redirect, useNavigate } from '@tanstack/react-router'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useState, useEffect, useRef, useCallback } from 'react'
import { Plus, AlertCircle, Tag, CheckCircle2, XCircle, Clock, Loader2, ExternalLink, RefreshCw, Wallet, Sparkles, Smartphone, Copy, Check, ChevronLeft, ChevronRight } from 'lucide-react'
import { authStore } from '@/stores/auth'
import { getTopUpInfo, getWallet, getTopUpHistory, createPay, getOrderStatus, type TopUpItem, type PayMethod, type OrderDetail } from '@/api/wallet'

export const Route = createFileRoute('/console/wallet')({
  beforeLoad: ({ location }) => {
    if (!authStore.isAuthenticated) {
      throw redirect({ to: '/login', search: { redirect: location.href } })
    }
  },
  validateSearch: (search: Record<string, unknown>) => ({
    order_no: typeof search.order_no === 'string' ? search.order_no : undefined,
    status: typeof search.status === 'string' ? search.status : undefined,
  }),
  component: WalletPage,
})

/* ------------------------------------------------------------------ */
/*  常量                                                               */
/* ------------------------------------------------------------------ */

const STATUS_STYLE: Record<string, string> = {
  success: 'bg-green-50 text-green-600',
  pending: 'bg-yellow-50 text-yellow-600',
  failed: 'bg-red-50 text-red-600',
  expired: 'bg-gray-50 text-gray-500',
}

const STATUS_LABEL: Record<string, string> = {
  success: '完成',
  pending: '处理中',
  failed: '失败',
  expired: '已过期',
}

const PROVIDER_LABELS: Record<string, string> = {
  epay: '易支付',
  stripe: 'Stripe',
  creem: 'Creem',
  waffo: 'Waffo',
  waffo_pancake: 'Waffo Pancake',
  alipay: '支付宝',
  wxpay: '微信支付',
  online: '在线充值',
}

const POLL_INTERVAL = 3000
const POLL_TIMEOUT = 5 * 60 * 1000 // 5 分钟

function providerLabel(raw: string): string {
  return PROVIDER_LABELS[raw] || raw
}

function fmtTime(epochSeconds: number): string {
  if (!epochSeconds) return '-'
  return new Date(epochSeconds * 1000).toLocaleDateString()
}

function fmtDateTime(epochSeconds: number): string {
  if (!epochSeconds) return '-'
  const now = new Date()
  const d = new Date(epochSeconds * 1000)
  const diffMs = now.getTime() - d.getTime()
  const diffMin = Math.floor(diffMs / 60000)
  if (diffMin < 1) return '刚刚'
  if (diffMin < 60) return `${diffMin} 分钟前`
  if (diffMin < 1440) return `${Math.floor(diffMin / 60)} 小时前`
  return d.toLocaleDateString('zh-CN', { month: 'short', day: 'numeric' }) + ' ' + d.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
}

function fmtExactTime(epochSeconds: number): string {
  if (!epochSeconds) return '-'
  const d = new Date(epochSeconds * 1000)
  return d.toLocaleDateString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit' }) + ' ' + d.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', second: '2-digit' })
}

const FILTER_TABS = [
  { key: 'all', label: '全部' },
  { key: 'topup', label: '充值' },
  { key: 'consume', label: '消费' },
] as const

/* ------------------------------------------------------------------ */
/*  Skeleton 组件                                                       */
/* ------------------------------------------------------------------ */

function TableSkeleton() {
  return (
    <tbody>
      {Array.from({ length: 5 }).map((_, i) => (
        <tr key={i} className="border-b border-gray-50">
          <td className="py-3 pr-4"><div className="h-4 w-14 rounded bg-gray-100 animate-pulse" /></td>
          <td className="py-3 pr-4"><div className="h-4 w-12 rounded bg-gray-100 animate-pulse" /></td>
          <td className="py-3 pr-4 text-right"><div className="ml-auto h-4 w-16 rounded bg-gray-100 animate-pulse" /></td>
          <td className="py-3 pr-4 text-right hidden sm:table-cell"><div className="ml-auto h-4 w-20 rounded bg-gray-100 animate-pulse" /></td>
          <td className="py-3 pr-4 text-right hidden md:table-cell"><div className="ml-auto h-4 w-24 rounded bg-gray-100 animate-pulse" /></td>
          <td className="py-3 text-right hidden sm:table-cell"><div className="ml-auto h-4 w-10 rounded bg-gray-100 animate-pulse" /></td>
        </tr>
      ))}
    </tbody>
  )
}

/* ------------------------------------------------------------------ */
/*  支付状态机类型                                                       */
/* ------------------------------------------------------------------ */

type PayPhase = 'idle' | 'paying' | 'success' | 'failed' | 'timeout'

/* ------------------------------------------------------------------ */
/*  页面主组件                                                           */
/* ------------------------------------------------------------------ */

function WalletPage() {
  const qc = useQueryClient()
  const navigate = useNavigate()
  const { order_no, status } = Route.useSearch()
  const [page, setPage] = useState(1)
  const [filter, setFilter] = useState<'all' | 'topup' | 'consume'>('all')
  const [showTopUp, setShowTopUp] = useState(false)
  const [topUpAmount, setTopUpAmount] = useState('')
  const [selectedMethod, setSelectedMethod] = useState('')
  const [payPhase, setPayPhase] = useState<PayPhase>('idle')
  const [currentOrderNo, setCurrentOrderNo] = useState('')
  const [currentQrcode, setCurrentQrcode] = useState('')
  const [currentPayUrl, setCurrentPayUrl] = useState('')
  const [copiedOrderNo, setCopiedOrderNo] = useState('')
  const pollingRef = useRef<ReturnType<typeof setInterval> | null>(null)
  const pollStartRef = useRef<number>(0)

  const handleCopyOrderNo = (orderNo: string) => {
    navigator.clipboard.writeText(orderNo).then(() => {
      setCopiedOrderNo(orderNo)
      setTimeout(() => setCopiedOrderNo(''), 2000)
    }).catch(() => {})
  }

  /* ---- 渲染辅助 ---- */
  const statusBadge = (s: string) => {
    const config: Record<string, { icon: typeof CheckCircle2; cls: string; label: string }> = {
      success: { icon: CheckCircle2, cls: 'bg-green-50 text-green-600', label: '完成' },
      pending: { icon: Clock, cls: 'bg-yellow-50 text-yellow-600', label: '处理中' },
      failed: { icon: XCircle, cls: 'bg-red-50 text-red-600', label: '失败' },
      expired: { icon: Clock, cls: 'bg-gray-50 text-gray-500', label: '已过期' },
    }
    const c = config[s] || { icon: Clock, cls: 'bg-gray-50 text-gray-500', label: s }
    const Icon = c.icon
    return (
      <span className={`inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-xs font-medium ${c.cls}`}>
        <Icon className="h-3 w-3" />{c.label}
      </span>
    )
  }

  const renderDesktopRow = (tx: TopUpItem) => (
    <tr key={tx.id} className="group cursor-pointer transition-colors hover:bg-gray-50/60" onClick={() => handleViewDetail(tx.trade_no)}>
      <td className="py-3 pl-6 pr-4">
        <div className="flex items-center gap-2">
          <span className="font-mono text-xs text-gray-500">{tx.trade_no ? tx.trade_no.slice(-16) : '-'}</span>
          {tx.trade_no && (
            <button type="button" onClick={(e) => { e.stopPropagation(); handleCopyOrderNo(tx.trade_no) }}
              className="rounded p-0.5 text-gray-300 opacity-0 group-hover:opacity-100 hover:text-gray-500 transition-all"
              title="复制订单号">
              {copiedOrderNo === tx.trade_no ? <Check className="h-3 w-3 text-green-500" /> : <Copy className="h-3 w-3" />}
            </button>
          )}
        </div>
      </td>
      <td className="py-3 pr-4">
        <span className="inline-flex items-center gap-1.5">
          <span className={`flex h-5 w-5 items-center justify-center rounded text-[10px] font-bold ${tx.payment_method === 'alipay' ? 'bg-blue-100 text-blue-600' : 'bg-green-100 text-green-600'}`}>
            {tx.payment_method === 'alipay' ? '支' : '微'}
          </span>
          {providerLabel(tx.payment_method) || '在线充值'}
        </span>
      </td>
      <td className="py-3 pr-4 text-right">
        <span className={`font-mono font-semibold tabular-nums ${tx.status === 'success' ? 'text-green-600' : tx.status === 'failed' ? 'text-red-500' : 'text-gray-400'}`}>
          {tx.status === 'success' ? '+' : ''}¥{Number(tx.money || tx.amount).toFixed(2)}
        </span>
      </td>
      <td className="py-3 pr-4">{statusBadge(tx.status)}</td>
      <td className="py-3 pr-6 text-right text-xs text-gray-400">{fmtDateTime(tx.create_time)}</td>
    </tr>
  )

  const renderMobileCard = (tx: TopUpItem) => (
    <div key={tx.id} className="px-6 py-3.5 cursor-pointer active:bg-gray-50" onClick={() => handleViewDetail(tx.trade_no)}>
      <div className="flex items-start justify-between">
        <div className="flex items-center gap-2.5">
          <span className={`flex h-8 w-8 items-center justify-center rounded-xl ${tx.status === 'success' ? 'bg-green-100 text-green-600' : tx.status === 'failed' ? 'bg-red-100 text-red-500' : 'bg-yellow-100 text-yellow-600'}`}>
            {tx.status === 'success' ? <CheckCircle2 className="h-4 w-4" /> : tx.status === 'failed' ? <XCircle className="h-4 w-4" /> : <Clock className="h-4 w-4" />}
          </span>
          <div>
            <p className="text-sm font-medium text-gray-900">{providerLabel(tx.payment_method) || '在线充值'}</p>
            <p className="text-xs text-gray-400">{fmtDateTime(tx.create_time)}</p>
          </div>
        </div>
        <div className="text-right">
          <p className={`font-mono font-semibold tabular-nums ${tx.status === 'success' ? 'text-green-600' : tx.status === 'failed' ? 'text-red-500' : 'text-gray-400'}`}>
            {tx.status === 'success' ? '+' : ''}¥{Number(tx.money || tx.amount).toFixed(2)}
          </p>
          {statusBadge(tx.status)}
        </div>
      </div>
      {tx.trade_no && (
        <div className="mt-2 flex items-center gap-1.5">
          <span className="font-mono text-[11px] text-gray-400">{tx.trade_no.slice(-16)}</span>
          <button type="button" onClick={() => handleCopyOrderNo(tx.trade_no)}
            className="rounded p-0.5 text-gray-300 hover:text-gray-500 transition-colors">
            {copiedOrderNo === tx.trade_no ? <Check className="h-3 w-3 text-green-500" /> : <Copy className="h-3 w-3" />}
          </button>
        </div>
      )}
    </div>
  )
  useEffect(() => {
    if (order_no) {
      qc.invalidateQueries({ queryKey: ['wallet'] })
      qc.invalidateQueries({ queryKey: ['topup-history'] })
      // 清除 URL 参数，避免刷新重复处理
      navigate({ search: {} as never, replace: true })
    }
  }, [order_no, qc, navigate])

  // ---- 清理轮询 ----
  useEffect(() => {
    return () => {
      if (pollingRef.current) clearInterval(pollingRef.current)
    }
  }, [])

  // ---- 数据查询 ----
  const walletQuery = useQuery({
    queryKey: ['wallet'],
    queryFn: getWallet,
    staleTime: 30_000,
  })

  const topUpInfoQuery = useQuery({
    queryKey: ['topup-info'],
    queryFn: getTopUpInfo,
    staleTime: 120_000,
  })

  const historyQuery = useQuery({
    queryKey: ['topup-history', page],
    queryFn: () => getTopUpHistory(page, 10),
    staleTime: 60_000,
  })

  // ---- 支付 mutation ----
  const payMutation = useMutation({
    mutationFn: ({ amount, method }: { amount: number; method: string }) => createPay(amount, method),
    onSuccess: (data) => {
      setCurrentOrderNo(data.orderNo)
      setCurrentQrcode(data.qrcode || '')
      setCurrentPayUrl(data.paymentUrl || '')
      setPayPhase('paying')
      pollStartRef.current = Date.now()
      // 有固定金额 pay_url 时直接新标签页打开支付页面
      if (data.paymentUrl && !data.qrcode) {
        window.open(data.paymentUrl, '_blank', 'noopener,noreferrer')
      }
      startPolling(data.orderNo)
    },
    onError: () => {
      setPayPhase('failed')
    },
  })

  // ---- 轮询订单状态 ----
  const startPolling = useCallback((orderNo: string) => {
    if (pollingRef.current) clearInterval(pollingRef.current)

    pollingRef.current = setInterval(async () => {
      try {
        const order = await getOrderStatus(orderNo)
        if (order.status === 'success') {
          clearInterval(pollingRef.current!)
          pollingRef.current = null
          setPayPhase('success')
          qc.invalidateQueries({ queryKey: ['wallet'] })
          qc.invalidateQueries({ queryKey: ['topup-history'] })
          setTimeout(() => {
            setShowTopUp(false)
            setTopUpAmount('')
            setPayPhase('idle')
            setCurrentOrderNo('')
          }, 2000)
        } else if (order.status === 'failed' || order.status === 'expired') {
          clearInterval(pollingRef.current!)
          pollingRef.current = null
          setPayPhase('failed')
          qc.invalidateQueries({ queryKey: ['topup-history'] })
        }
      } catch {
        // 网络错误忽略，继续轮询
      }

      // 超时
      if (Date.now() - pollStartRef.current > POLL_TIMEOUT) {
        clearInterval(pollingRef.current!)
        pollingRef.current = null
        setPayPhase('timeout')
      }
    }, POLL_INTERVAL)
  }, [qc])

  // ---- 停止轮询 ----
  const stopPolling = useCallback(() => {
    if (pollingRef.current) {
      clearInterval(pollingRef.current)
      pollingRef.current = null
    }
    setPayPhase('idle')
    setCurrentOrderNo('')
    setCurrentQrcode('')
    setCurrentPayUrl('')
  }, [])

  // ---- 重试 ----
  const handleRetry = useCallback(() => {
    setPayPhase('idle')
    setCurrentOrderNo('')
    setCurrentQrcode('')
    setCurrentPayUrl('')
  }, [])

  // ---- 订单详情弹窗 ----
  const [showDetail, setShowDetail] = useState(false)
  const [detailOrder, setDetailOrder] = useState<OrderDetail | null>(null)
  const [detailLoading, setDetailLoading] = useState(false)

  const handleViewDetail = async (orderNo: string) => {
    setShowDetail(true)
    setDetailLoading(true)
    setDetailOrder(null)
    try {
      const detail = await getOrderStatus(orderNo)
      setDetailOrder(detail)
    } catch {
      setDetailOrder(null)
    } finally {
      setDetailLoading(false)
    }
  }

  // ---- 派生数据 ----
  const isLoading = walletQuery.isLoading
  const balance = walletQuery.data?.balance ?? 0
  const consumption = walletQuery.data?.historical_consumption ?? 0
  const topUpInfo = topUpInfoQuery.data
  const amountOptions = topUpInfo?.amount_options ?? [50, 100, 200, 500, 1000, 2000]
  const payMethods: PayMethod[] = topUpInfo?.pay_methods ?? []
  const onlineEnabled = topUpInfo?.enable_online_topup ?? true
  const minTopup = topUpInfo?.min_topup ?? 0

  // 折扣
  const discountMap = topUpInfo?.discount ?? null
  const hasDiscount = discountMap !== null && typeof discountMap === 'object' && Object.keys(discountMap).length > 0

  const allItems: TopUpItem[] = historyQuery.data?.items ?? []
  const filtered = filter === 'all' ? allItems : filter === 'topup' ? allItems : []
  const totalPages = historyQuery.data ? Math.max(1, Math.ceil(historyQuery.data.total / (historyQuery.data.page_size || 10))) : 1

  // ---- 交互 ----
  const handleOpenTopUp = () => {
    setShowTopUp(true)
    setSelectedMethod(payMethods[0]?.type ?? '')
    setTopUpAmount('')
    setPayPhase('idle')
    setCurrentOrderNo('')
    payMutation.reset()
  }

  const handleTopUp = () => {
    const amt = Number(topUpAmount)
    if (!amt || amt <= 0) return
    if (minTopup > 0 && amt < minTopup) return
    const method = selectedMethod || payMethods[0]?.type || 'alipay'
    payMutation.mutate({ amount: amt, method })
  }

  const getDiscountRate = (amount: number): number | null => {
    if (!discountMap) return null
    const rate = discountMap[amount] ?? discountMap[String(amount)]
    if (typeof rate === 'number' && rate > 0 && rate < 1) return rate
    return null
  }

  // ---- 渲染 ----
  return (
    <div className="animate-fade-in space-y-6 p-4 sm:p-6">
      <h1 className="text-2xl font-bold text-gray-900">钱包</h1>

      {/* 加载错误 */}
      {(walletQuery.isError || topUpInfoQuery.isError) && (
        <div className="rounded-xl border border-red-200 bg-red-50/90 px-4 py-3 text-sm text-red-700">
          加载失败
          <button type="button" onClick={() => { walletQuery.refetch(); topUpInfoQuery.refetch() }}
            className="ml-3 underline underline-offset-2 hover:text-red-800">重试</button>
        </div>
      )}

      {/* 余额卡片 */}
      <div className="overflow-hidden rounded-2xl bg-gradient-to-br from-gray-900 via-gray-800 to-gray-900 text-white shadow-xl">
        <div className="relative p-6 sm:p-8">
          {/* 装饰光晕 */}
          <div className="pointer-events-none absolute -right-12 -top-12 h-40 w-40 rounded-full bg-brand-500/10 blur-3xl" />
          <div className="pointer-events-none absolute -bottom-8 left-20 h-24 w-24 rounded-full bg-blue-500/10 blur-2xl" />
          <div className="relative flex flex-col gap-5 sm:flex-row sm:items-center sm:justify-between">
            <div>
              <p className="text-sm text-gray-400">可用余额</p>
              <p className="mt-1 text-4xl font-bold tracking-tight tabular-nums">
                {isLoading ? (
                  <span className="inline-block h-10 w-32 animate-pulse rounded bg-gray-700/50 align-middle" />
                ) : (
                  `¥${balance.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
                )}
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
          <div className="px-6 py-3.5 text-center">
            <p className="text-xs text-gray-400">累计消费</p>
            <p className="text-sm font-semibold tabular-nums">
              {isLoading ? <span className="inline-block h-5 w-16 animate-pulse rounded bg-gray-700/50 align-middle" /> : `¥${consumption.toFixed(2)}`}
            </p>
          </div>
          <div className="border-l border-gray-700/50 px-6 py-3.5 text-center">
            <p className="text-xs text-gray-400">充值次数</p>
            <p className="text-sm font-semibold tabular-nums">{historyQuery.data?.total ?? 0} 笔</p>
          </div>
        </div>
      </div>

      {/* 交易记录 */}
      <div className="rounded-2xl border border-gray-100 bg-white shadow-sm">
        <div className="border-b border-gray-100 px-6 py-4">
          <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
            <h2 className="text-lg font-semibold text-gray-900">交易记录</h2>
            <div className="flex gap-1 rounded-lg bg-gray-100 p-1">
              {FILTER_TABS.map(({ key, label }) => (
                <button key={key} type="button" onClick={() => { setFilter(key); setPage(1) }}
                  className={`rounded-md px-3 py-1.5 text-xs font-medium transition-all ${filter === key ? 'bg-white text-gray-900 shadow-sm' : 'text-gray-500 hover:text-gray-700'}`}>{label}</button>
              ))}
            </div>
          </div>
        </div>

        {historyQuery.isLoading ? (
          <div className="p-6"><TableSkeleton /></div>
        ) : filtered.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-16 text-center">
            <div className="flex h-16 w-16 items-center justify-center rounded-2xl bg-gray-50">
              <Wallet className="h-8 w-8 text-gray-300" />
            </div>
            <p className="mt-4 text-sm font-medium text-gray-500">暂无交易记录</p>
            <p className="mt-1 text-xs text-gray-400">充值后记录将在此显示</p>
            <button type="button" onClick={handleOpenTopUp}
              className="mt-4 inline-flex items-center gap-2 rounded-lg bg-brand-50 px-4 py-2 text-sm font-medium text-brand-600 hover:bg-brand-100 transition-colors">
              <Plus className="h-3.5 w-3.5" />立即充值
            </button>
          </div>
        ) : (
          <>
            {/* Desktop 表头 */}
            <div className="hidden sm:block overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b border-gray-100 text-left text-xs font-medium text-gray-400">
                    <th className="pb-3 pl-6 pr-4">订单号</th>
                    <th className="pb-3 pr-4">类型</th>
                    <th className="pb-3 pr-4 text-right">金额</th>
                    <th className="pb-3 pr-4">状态</th>
                    <th className="pb-3 pr-6 text-right">时间</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-50">
                  {filtered.map((tx) => renderDesktopRow(tx))}
                </tbody>
              </table>
            </div>

            {/* Mobile 卡片列表 */}
            <div className="sm:hidden divide-y divide-gray-50">
              {filtered.map((tx) => renderMobileCard(tx))}
            </div>
          </>
        )}

        {totalPages > 1 && (
          <div className="flex items-center justify-between border-t border-gray-100 px-6 py-3">
            <span className="text-xs text-gray-400">共 {historyQuery.data?.total ?? 0} 条</span>
            <div className="flex items-center gap-1">
              <button type="button" disabled={page <= 1} onClick={() => setPage((p) => Math.max(1, p - 1))}
                className="inline-flex items-center gap-0.5 rounded-lg border border-gray-200 px-2.5 py-1.5 text-xs font-medium text-gray-600 hover:bg-gray-50 disabled:opacity-30 transition-colors">
                <ChevronLeft className="h-3.5 w-3.5" />
              </button>
              {Array.from({ length: totalPages }, (_, i) => i + 1).filter(p => p === 1 || p === totalPages || Math.abs(p - page) <= 1).map((p, idx, arr) => (
                <span key={p}>
                  {idx > 0 && arr[idx - 1] !== p - 1 && <span className="text-gray-300 px-1">…</span>}
                  <button type="button" onClick={() => setPage(p)}
                    className={`inline-flex h-7 w-7 items-center justify-center rounded-lg text-xs font-medium transition-colors ${p === page ? 'bg-brand-600 text-white shadow-sm' : 'text-gray-500 hover:bg-gray-100'}`}>{p}</button>
                </span>
              ))}
              <button type="button" disabled={page >= totalPages} onClick={() => setPage((p) => p + 1)}
                className="inline-flex items-center gap-0.5 rounded-lg border border-gray-200 px-2.5 py-1.5 text-xs font-medium text-gray-600 hover:bg-gray-50 disabled:opacity-30 transition-colors">
                <ChevronRight className="h-3.5 w-3.5" />
              </button>
            </div>
          </div>
        )}
      </div>

      {/* 充值弹窗 */}
      {showTopUp && (
        <div className="fixed inset-0 z-[60] flex md:items-center md:justify-center">
          {/* 遮罩 */}
          <div className="fixed inset-0 bg-black/40 backdrop-blur-sm"
            onClick={() => { if (!payMutation.isPending && payPhase !== 'paying') { setShowTopUp(false); setTopUpAmount(''); stopPolling() } }} />

          {/* 弹窗主体 — 移动端底部抽屉 */}
          <div className="fixed inset-x-0 bottom-0 max-h-[90vh] overflow-y-auto rounded-t-2xl bg-white shadow-2xl md:relative md:inset-auto md:w-full md:max-w-sm md:rounded-2xl animate-fade-in-up">
            {/* 移动端拖拽条 */}
            <div className="sticky top-0 z-10 flex justify-center bg-white pt-3 pb-1 md:hidden">
              <div className="h-1 w-10 rounded-full bg-gray-300" />
            </div>

            <div className="px-6 pb-6 pt-4 md:pt-6">
              {/* 标题栏 */}
              <div className="mb-5 flex items-center justify-between">
                <div>
                  <h3 className="text-lg font-semibold text-gray-900">账户充值</h3>
                  <p className="mt-0.5 text-sm text-gray-500">选择或输入充值金额</p>
                </div>
                {payPhase !== 'paying' && (
                  <button type="button" onClick={() => { setShowTopUp(false); setTopUpAmount(''); stopPolling() }}
                    className="rounded-lg p-1.5 text-gray-400 hover:bg-gray-100 hover:text-gray-600 transition-colors">
                    <XCircle className="h-5 w-5" />
                  </button>
                )}
              </div>

              {/* 在线充值未启用 */}
              {!onlineEnabled && (
                <div className="mb-5 flex items-center gap-2 rounded-lg border border-amber-200 bg-amber-50 px-3 py-2.5 text-sm text-amber-700">
                  <AlertCircle className="h-4 w-4 shrink-0" />在线充值暂未开放，如有需要请联系客服
                </div>
              )}

              {/* 折扣横幅 */}
              {hasDiscount && (
                <div className="mb-5 flex items-center gap-2 rounded-lg border border-brand-100 bg-brand-50 px-3 py-2.5 text-sm text-brand-700">
                  <Sparkles className="h-4 w-4 shrink-0" />
                  <span>当前充值享受折扣优惠</span>
                </div>
              )}

              {/* ---- 支付进行中 ---- */}
              {payPhase === 'paying' && (
                <div className="flex flex-col items-center py-4 text-center">
                  {currentQrcode ? (
                    <>
                      <div className="rounded-2xl border-2 border-brand-200 bg-white p-3 shadow-sm">
                        <img
                          src={`https://api.qrserver.com/v1/create-qr-code/?size=220x220&data=${encodeURIComponent(currentQrcode)}`}
                          alt="支付二维码"
                          className="h-[220px] w-[220px] object-contain"
                        />
                      </div>
                      <div className="mt-3 flex items-center gap-2 rounded-full bg-brand-50 px-4 py-1.5 text-sm text-brand-700">
                        <Smartphone className="h-4 w-4" />
                        <span>请使用{selectedMethod === 'alipay' ? '支付宝' : '微信'}扫码支付 ¥{topUpAmount}</span>
                      </div>
                    </>
                  ) : (
                    <>
                      <div className="relative">
                        <div className="flex h-20 w-20 items-center justify-center rounded-full bg-brand-50">
                          <Loader2 className="h-10 w-10 animate-spin text-brand-500" />
                        </div>
                        <div className="absolute -bottom-1 -right-1 flex h-7 w-7 items-center justify-center rounded-full bg-green-100">
                          <ExternalLink className="h-3.5 w-3.5 text-green-600" />
                        </div>
                      </div>
                      <h4 className="mt-5 text-base font-semibold text-gray-900">等待支付中</h4>
                      <p className="mt-1.5 text-sm text-gray-500">已在新标签页打开支付页面，请在页面完成付款</p>
                      <button type="button" onClick={() => currentPayUrl && window.open(currentPayUrl, '_blank', 'noopener,noreferrer')}
                        className="mt-4 inline-flex items-center gap-1.5 rounded-lg bg-brand-50 px-4 py-2 text-xs font-medium text-brand-600 hover:bg-brand-100 transition-colors">
                        <ExternalLink className="h-3.5 w-3.5" />重新打开支付页面
                      </button>
                    </>
                  )}
                  <div className="mt-4 space-y-1 text-xs text-gray-400">
                    <p>订单号：<span className="font-mono text-gray-600">{currentOrderNo}</span></p>
                    <p>支付完成后将自动刷新余额</p>
                  </div>
                  <button type="button" onClick={stopPolling}
                    className="mt-3 inline-flex items-center gap-1.5 rounded-lg border border-gray-200 px-4 py-2 text-xs font-medium text-gray-500 hover:bg-gray-50 transition-colors">
                    <XCircle className="h-3.5 w-3.5" />取消等待
                  </button>
                </div>
              )}

              {/* ---- 支付成功 ---- */}
              {payPhase === 'success' && (
                <div className="flex flex-col items-center py-10 text-center animate-fade-in-up">
                  <div className="flex h-20 w-20 items-center justify-center rounded-full bg-green-100">
                    <CheckCircle2 className="h-10 w-10 text-green-500" />
                  </div>
                  <h4 className="mt-5 text-base font-semibold text-gray-900">充值成功！</h4>
                  <p className="mt-1.5 text-sm text-gray-500">余额已到账，即将自动关闭</p>
                </div>
              )}

              {/* ---- 支付失败 ---- */}
              {payPhase === 'failed' && (
                <div className="flex flex-col items-center py-8 text-center">
                  <div className="flex h-20 w-20 items-center justify-center rounded-full bg-red-100">
                    <XCircle className="h-10 w-10 text-red-500" />
                  </div>
                  <h4 className="mt-5 text-base font-semibold text-gray-900">
                    {payMutation.isError ? '下单失败' : '支付未完成'}
                  </h4>
                  <p className="mt-1.5 text-sm text-gray-500">
                    {payMutation.isError ? (payMutation.error instanceof Error ? payMutation.error.message : '请稍后重试') : '订单未支付成功，请重试'}
                  </p>
                  <div className="mt-5 flex gap-3">
                    <button type="button" onClick={handleRetry}
                      className="inline-flex items-center gap-2 rounded-xl bg-brand-600 px-5 py-2.5 text-sm font-semibold text-white shadow-sm hover:bg-brand-700 active:scale-[0.98] transition-all">
                      <RefreshCw className="h-4 w-4" />重新支付
                    </button>
                    <button type="button" onClick={() => { setShowTopUp(false); setPayPhase('idle') }}
                      className="rounded-xl border border-gray-200 px-5 py-2.5 text-sm font-medium text-gray-600 hover:bg-gray-50 transition-colors">取消</button>
                  </div>
                </div>
              )}

              {/* ---- 超时 ---- */}
              {payPhase === 'timeout' && (
                <div className="flex flex-col items-center py-8 text-center">
                  <div className="flex h-20 w-20 items-center justify-center rounded-full bg-amber-100">
                    <Clock className="h-10 w-10 text-amber-500" />
                  </div>
                  <h4 className="mt-5 text-base font-semibold text-gray-900">等待超时</h4>
                  <p className="mt-1.5 text-sm text-gray-500">已超过 5 分钟，请检查支付状态或重新支付</p>
                  <div className="mt-5 flex gap-3">
                    <button type="button" onClick={() => { qc.invalidateQueries({ queryKey: ['wallet'] }); qc.invalidateQueries({ queryKey: ['topup-history'] }); setPayPhase('idle'); setShowTopUp(false) }}
                      className="inline-flex items-center gap-2 rounded-xl bg-brand-600 px-5 py-2.5 text-sm font-semibold text-white shadow-sm hover:bg-brand-700 active:scale-[0.98] transition-all">
                      <RefreshCw className="h-4 w-4" />刷新状态
                    </button>
                  </div>
                </div>
              )}

              {/* ---- 表单（idle 状态）---- */}
              {payPhase === 'idle' && (
                <>
                  {/* 金额选择 */}
                  <div className="mb-5">
                    <p className="mb-3 text-sm font-medium text-gray-700">选择充值金额</p>
                    <div className="grid grid-cols-3 gap-2.5">
                      {amountOptions.map((v) => {
                        const rate = getDiscountRate(v)
                        return (
                          <button key={v} type="button" onClick={() => setTopUpAmount(String(v))}
                            className={`relative rounded-xl border py-3 text-center transition-all ${
                              topUpAmount === String(v)
                                ? 'border-brand-500 bg-brand-50 shadow-sm'
                                : 'border-gray-200 hover:border-gray-300 hover:bg-gray-50'
                            }`}>
                            {rate && (
                              <span className="absolute -right-2 -top-2 rounded-full bg-red-500 px-1.5 py-0.5 text-[10px] font-bold text-white shadow-sm">
                                {Math.round((1 - rate) * 100)}折
                              </span>
                            )}
                            <span className={`block text-lg font-bold ${topUpAmount === String(v) ? 'text-brand-700' : 'text-gray-900'}`}>¥{v}</span>
                            {rate && (
                              <span className="block text-[11px] text-gray-400 line-through">¥{v}</span>
                            )}
                          </button>
                        )
                      })}
                    </div>
                    <div className="mt-3">
                      <div className="relative">
                        <span className="pointer-events-none absolute inset-y-0 left-3.5 flex items-center text-gray-400">¥</span>
                        <input type="number" value={topUpAmount} onChange={(e) => setTopUpAmount(e.target.value)}
                          placeholder={minTopup > 0 ? `自定义金额（最低 ¥${minTopup}）` : '自定义金额'}
                          className="w-full rounded-xl border border-gray-200 py-2.5 pl-9 pr-4 text-sm focus:border-brand-400 focus:outline-none focus:ring-2 focus:ring-brand-500/20" />
                      </div>
                      {/* 快捷翻倍 */}
                      {topUpAmount && Number(topUpAmount) > 0 && (
                        <div className="mt-2 flex gap-2">
                          {[2, 5].map((mult) => (
                            <button key={mult} type="button" onClick={() => setTopUpAmount(String(Number(topUpAmount) * mult))}
                              className="rounded-lg border border-gray-200 px-2.5 py-1 text-xs text-gray-500 hover:bg-gray-50 transition-colors">
                              ×{mult}
                            </button>
                          ))}
                        </div>
                      )}
                    </div>
                    {minTopup > 0 && topUpAmount && Number(topUpAmount) < minTopup && (
                      <p className="mt-1.5 text-xs text-red-500">最低充值金额为 ¥{minTopup}</p>
                    )}
                    {/* 折扣提示 */}
                    {hasDiscount && topUpAmount && Number(topUpAmount) > 0 && getDiscountRate(Number(topUpAmount)) && (
                      <div className="mt-2 flex items-center gap-1.5 rounded-lg bg-brand-50 px-3 py-2 text-xs text-brand-700">
                        <Tag className="h-3.5 w-3.5 shrink-0" />
                        实付 ¥{(Number(topUpAmount) * getDiscountRate(Number(topUpAmount))!).toFixed(2)}，优惠 {Math.round((1 - getDiscountRate(Number(topUpAmount))!) * 100)}%
                      </div>
                    )}
                  </div>

                  {/* 支付方式 */}
                  {payMethods.length > 0 && (
                    <div className="mb-5">
                      <p className="mb-3 text-sm font-medium text-gray-700">支付方式</p>
                      <div className="grid grid-cols-2 gap-2.5">
                        {payMethods.map((m: PayMethod) => (
                          <button key={m.type} type="button" onClick={() => setSelectedMethod(m.type)}
                            className={`flex items-center gap-2.5 rounded-xl border px-4 py-3 text-sm font-medium transition-all ${
                              selectedMethod === m.type
                                ? 'border-brand-500 bg-brand-50 text-brand-700 shadow-sm'
                                : 'border-gray-200 text-gray-600 hover:border-gray-300 hover:bg-gray-50'
                            }`}>
                            <span className={`flex h-7 w-7 items-center justify-center rounded-lg text-xs font-bold ${selectedMethod === m.type ? 'bg-brand-500 text-white' : 'bg-gray-100 text-gray-400'}`}>
                              {m.type === 'alipay' ? '支' : '微'}
                            </span>
                            {m.name}
                          </button>
                        ))}
                      </div>
                    </div>
                  )}

                  {/* 错误 */}
                  {payMutation.isError && (
                    <p className="mb-4 text-sm text-red-600">{payMutation.error instanceof Error ? payMutation.error.message : '支付失败'}</p>
                  )}

                  {/* 按钮 */}
                  <div className="flex gap-3">
                    <button type="button" onClick={() => { setShowTopUp(false); setTopUpAmount('') }}
                      className="flex-1 rounded-xl border border-gray-200 py-2.5 text-sm font-medium text-gray-700 hover:bg-gray-50 active:scale-[0.98] transition-all">
                      取消
                    </button>
                    <button type="button" onClick={handleTopUp}
                      disabled={!onlineEnabled || !topUpAmount || Number(topUpAmount) <= 0 || (minTopup > 0 && Number(topUpAmount) < minTopup) || payMutation.isPending}
                      className="flex-1 rounded-xl bg-brand-600 py-2.5 text-sm font-semibold text-white shadow-sm hover:bg-brand-700 disabled:cursor-not-allowed disabled:opacity-50 active:scale-[0.98] transition-all">
                      {payMutation.isPending ? (
                        <span className="inline-flex items-center gap-1.5"><Loader2 className="h-4 w-4 animate-spin" />处理中...</span>
                      ) : '确认充值'}
                    </button>
                  </div>
                </>
              )}
            </div>
          </div>
        </div>
      )}

      {/* 订单详情弹窗 */}
      {showDetail && (
        <div className="fixed inset-0 z-[70] flex items-center justify-center px-4">
          <div className="fixed inset-0 bg-black/40 backdrop-blur-sm" onClick={() => { setShowDetail(false); setDetailOrder(null) }} />
          <div className="relative w-full max-w-sm animate-fade-in-up rounded-2xl border border-gray-100 bg-white p-6 shadow-2xl">
            <div className="mb-5 flex items-center justify-between">
              <h3 className="text-lg font-semibold text-gray-900">订单详情</h3>
              <button type="button" onClick={() => { setShowDetail(false); setDetailOrder(null) }}
                className="rounded-lg p-1.5 text-gray-400 hover:bg-gray-100 hover:text-gray-600 transition-colors">
                <XCircle className="h-5 w-5" />
              </button>
            </div>
            {detailLoading ? (
              <div className="flex items-center justify-center py-12">
                <Loader2 className="h-8 w-8 animate-spin text-brand-500" />
              </div>
            ) : detailOrder ? (
              <div className="space-y-4">
                <div className="flex items-center gap-3 rounded-xl bg-gray-50 p-4">
                  {detailOrder.status === 'success' ? (
                    <div className="flex h-12 w-12 items-center justify-center rounded-full bg-green-100"><CheckCircle2 className="h-6 w-6 text-green-600" /></div>
                  ) : detailOrder.status === 'pending' ? (
                    <div className="flex h-12 w-12 items-center justify-center rounded-full bg-yellow-100"><Clock className="h-6 w-6 text-yellow-600" /></div>
                  ) : detailOrder.status === 'failed' ? (
                    <div className="flex h-12 w-12 items-center justify-center rounded-full bg-red-100"><XCircle className="h-6 w-6 text-red-500" /></div>
                  ) : (
                    <div className="flex h-12 w-12 items-center justify-center rounded-full bg-gray-100"><Clock className="h-6 w-6 text-gray-400" /></div>
                  )}
                  <div>
                    <p className="text-sm font-semibold text-gray-900">{detailOrder.product_name || '充值订单'}</p>
                    <p className="text-xs text-gray-500">{STATUS_LABEL[detailOrder.status] || detailOrder.status}</p>
                  </div>
                </div>
                <div className="divide-y divide-gray-100 rounded-xl border border-gray-100">
                  {[
                    { label: '订单 ID', value: String(detailOrder.id) },
                    { label: '订单号', value: detailOrder.order_no, mono: true },
                    { label: '商品名称', value: detailOrder.product_name || '-' },
                    { label: '充值金额', value: `¥${Number(detailOrder.amount).toFixed(2)}` },
                    { label: '实付金额', value: `¥${Number(detailOrder.money).toFixed(2)}` },
                    { label: '支付方式', value: providerLabel(detailOrder.payment_method) },
                    { label: '支付渠道', value: providerLabel(detailOrder.payment_provider) },
                    { label: '创建时间', value: fmtExactTime(detailOrder.create_time) },
                    { label: '更新时间', value: fmtExactTime(detailOrder.update_time) },
                    { label: '完成时间', value: detailOrder.complete_time ? fmtExactTime(detailOrder.complete_time) : '-' },
                  ].map(({ label, value, mono }) => (
                    <div key={label} className="flex items-center justify-between px-4 py-3">
                      <span className="text-sm text-gray-500">{label}</span>
                      <span className={`text-sm font-medium text-gray-900 max-w-[180px] truncate ${mono ? 'font-mono text-xs' : ''}`}>{value}</span>
                    </div>
                  ))}
                </div>
                <button type="button" onClick={() => handleCopyOrderNo(detailOrder.order_no)}
                  className="flex w-full items-center justify-center gap-2 rounded-xl border border-gray-200 py-2.5 text-sm font-medium text-gray-600 hover:bg-gray-50 transition-colors">
                  {copiedOrderNo === detailOrder.order_no ? <><Check className="h-4 w-4 text-green-500" />已复制</> : <><Copy className="h-4 w-4" />复制订单号</>}
                </button>
              </div>
            ) : (
              <div className="flex flex-col items-center justify-center py-12 text-center">
                <XCircle className="h-10 w-10 text-gray-300 mb-3" />
                <p className="text-sm text-gray-500">加载订单详情失败</p>
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  )
}
