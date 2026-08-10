import { createFileRoute } from '@tanstack/react-router'
import { useAuth } from '@/stores/auth'
import { useCallback, useEffect, useState } from 'react'
import {
  getTopUpInfo,
  getWallet,
  getTopUpHistory,
  createPay,
  calculateAmount,
  type TopUpInfo,
  type WalletInfo,
  type TopUpItem,
} from '@/api/wallet'
import { Loader2, Wallet, History, ExternalLink, TrendingUp, Clock, CheckCircle2, XCircle, AlertCircle, Tag } from 'lucide-react'

export const Route = createFileRoute('/wallet')({
  component: WalletPage,
})

/* ------------------------------------------------------------------ */
/*  常量                                                               */
/* ------------------------------------------------------------------ */

/** 支付方式中文名称映射。 */
const PAY_METHOD_LABELS: Record<string, string> = {
  alipay: '支付宝',
  wxpay: '微信支付',
  online: '在线支付',
  stripe: 'Stripe',
  creem: 'Creem',
  waffo: 'Waffo',
  waffo_pancake: 'Waffo Pancake',
}

/** 支付方式 / provider -> 显示名称。 */
function payLabel(raw: string): string {
  return PAY_METHOD_LABELS[raw] || raw
}

/* ------------------------------------------------------------------ */
/*  子组件                                                            */
/* ------------------------------------------------------------------ */

/** 余额卡片 */
function BalanceCard({ wallet, loading }: { wallet: WalletInfo | null; loading: boolean }) {
  return (
    <div className="rounded-2xl border border-gray-200 bg-white p-8 shadow-sm">
      <div className="flex items-center gap-3 mb-6">
        <div className="flex h-12 w-12 items-center justify-center rounded-xl bg-brand-100">
          <Wallet className="h-6 w-6 text-brand-600" />
        </div>
        <div>
          <h2 className="text-lg font-semibold text-gray-900">我的钱包</h2>
          <p className="text-sm text-gray-500">余额与消费概览</p>
        </div>
      </div>

      {loading ? (
        <div className="flex items-center justify-center py-8">
          <Loader2 className="h-8 w-8 animate-spin text-brand-500" />
        </div>
      ) : wallet ? (
        <div className="grid grid-cols-2 gap-6">
          <div>
            <p className="text-sm text-gray-500 mb-1">当前余额</p>
            <p className="text-3xl font-bold text-gray-900">
              ¥{(Number(wallet.balance) || 0).toFixed(4)}
            </p>
          </div>
          <div>
            <p className="text-sm text-gray-500 mb-1">
              <span className="inline-flex items-center gap-1">
                <TrendingUp className="h-3.5 w-3.5" />
                历史消耗
              </span>
            </p>
            <p className="text-3xl font-bold text-gray-400">
              ¥{(Number(wallet.historical_consumption) || 0).toFixed(4)}
            </p>
          </div>
        </div>
      ) : (
        <p className="text-gray-400 text-sm">暂无数据</p>
      )}
    </div>
  )
}

/** 充值面板 */
function TopUpPanel({
  info,
  onSuccess,
}: {
  info: TopUpInfo
  onSuccess: () => void
}) {
  const [selectedAmount, setSelectedAmount] = useState<number | null>(null)
  const [customAmount, setCustomAmount] = useState('')
  const [selectedMethod, setSelectedMethod] = useState<string | null>(null)
  const [calcMoney, setCalcMoney] = useState<string | null>(null)
  const [calcLoading, setCalcLoading] = useState(false)
  const [payLoading, setPayLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  /** 有效充值金额：优先自定义，否则预设。 */
  const effectiveAmount = customAmount ? Number(customAmount) : selectedAmount

  /** 选择预设金额。 */
  const handleAmountSelect = useCallback(async (amount: number) => {
    setSelectedAmount(amount)
    setCustomAmount('')
    setError(null)
    setCalcLoading(true)
    try {
      const money = await calculateAmount(amount)
      setCalcMoney(money)
    } catch {
      setCalcMoney(String(amount))
    } finally {
      setCalcLoading(false)
    }
  }, [])

  /** 自定义金额变更后防抖计算实付。 */
  const handleCustomAmountChange = useCallback(async (value: string) => {
    setCustomAmount(value)
    setSelectedAmount(null)
    setError(null)
    const amt = Number(value)
    if (!amt || amt <= 0) {
      setCalcMoney(null)
      return
    }
    setCalcLoading(true)
    try {
      const money = await calculateAmount(amt)
      setCalcMoney(money)
    } catch {
      setCalcMoney(String(amt))
    } finally {
      setCalcLoading(false)
    }
  }, [])

  /** 发起支付。 */
  const handlePay = useCallback(async () => {
    if (!effectiveAmount || !selectedMethod) return

    // 最小金额校验
    if (info.min_topup && effectiveAmount < info.min_topup) {
      setError(`最低充值金额为 ¥${info.min_topup}`)
      return
    }

    setPayLoading(true)
    setError(null)
    try {
      const result = await createPay(effectiveAmount, selectedMethod)
      window.open(result.paymentUrl, '_blank', 'noopener,noreferrer')
      onSuccess()
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : '支付失败，请重试')
    } finally {
      setPayLoading(false)
    }
  }, [effectiveAmount, selectedMethod, info.min_topup, onSuccess])

  // --- 在线充值未启用 ---
  if (!info.enable_online_topup) {
    return (
      <div className="rounded-2xl border border-gray-200 bg-white p-8 shadow-sm">
        <h2 className="text-lg font-semibold text-gray-900 mb-6">账户充值</h2>
        <div className="flex flex-col items-center justify-center py-10 text-center">
          <AlertCircle className="h-10 w-10 text-amber-400 mb-3" />
          <p className="text-sm font-medium text-gray-700">在线充值暂未开放</p>
          <p className="mt-1 text-xs text-gray-400">如有需要请联系客服</p>
        </div>
      </div>
    )
  }

  // --- 折扣提示 ---
  const discountMap = info.discount
  const hasDiscount = discountMap !== null && typeof discountMap === 'object' && Object.keys(discountMap).length > 0
  // 取第一个可用的折扣率用于展示（若按支付方式区分则显示范围）
  const discountValues = hasDiscount ? Object.values(discountMap!).filter((v) => typeof v === 'number' && v > 0 && v < 1) : []
  const showDiscountRate = discountValues.length > 0 ? Math.round((1 - discountValues[0]) * 100) : null

  return (
    <div className="rounded-2xl border border-gray-200 bg-white p-8 shadow-sm">
      <h2 className="text-lg font-semibold text-gray-900 mb-6">账户充值</h2>

      {/* 错误提示 */}
      {error && (
        <div className="mb-4 rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
          {error}
        </div>
      )}

      {/* 折扣横幅 */}
      {hasDiscount && (
        <div className="mb-6 flex items-center gap-2 rounded-lg border border-brand-100 bg-brand-50 px-4 py-3 text-sm">
          <Tag className="h-4 w-4 text-brand-600 shrink-0" />
          <span className="text-brand-700">
            {showDiscountRate !== null
              ? `当前享受 ${showDiscountRate}% 充值优惠`
              : '当前有充值优惠活动'}
          </span>
        </div>
      )}

      {/* 金额选择 */}
      <div className="mb-6">
        <p className="text-sm font-medium text-gray-700 mb-3">选择充值金额</p>
        <div className="grid grid-cols-3 gap-3 sm:grid-cols-4">
          {info.amount_options.map((amount) => (
            <button
              key={amount}
              type="button"
              onClick={() => handleAmountSelect(amount)}
              className={`rounded-lg border px-4 py-3 text-sm font-semibold transition-all ${
                selectedAmount === amount && !customAmount
                  ? 'border-brand-500 bg-brand-50 text-brand-700 shadow-sm'
                  : 'border-gray-200 text-gray-700 hover:border-brand-300 hover:bg-brand-50/50'
              }`}
            >
              ¥{amount}
            </button>
          ))}
        </div>

        {/* 自定义金额 */}
        <div className="mt-3">
          <div className="relative">
            <span className="pointer-events-none absolute inset-y-0 left-3 flex items-center text-sm text-gray-400">¥</span>
            <input
              type="number"
              min={info.min_topup || 1}
              value={customAmount}
              onChange={(e) => handleCustomAmountChange(e.target.value)}
              placeholder={`自定义金额${info.min_topup ? `（最低 ¥${info.min_topup}）` : ''}`}
              className="w-full rounded-lg border border-gray-200 py-2.5 pl-8 pr-4 text-sm placeholder:text-gray-400 focus:border-brand-400 focus:outline-none focus:ring-2 focus:ring-brand-500/20"
            />
          </div>
        </div>

        {/* 实付金额 */}
        <div className="mt-3 min-h-[20px]">
          {calcLoading ? (
            <span className="inline-flex items-center gap-1 text-sm text-gray-400">
              <Loader2 className="h-3 w-3 animate-spin" />
              计算中...
            </span>
          ) : calcMoney && effectiveAmount ? (
            <p className="text-sm text-gray-500">
              实付金额：
              <span className="font-semibold text-gray-900">¥{calcMoney}</span>
              {hasDiscount && Number(calcMoney) < effectiveAmount && (
                <span className="ml-1 text-xs text-brand-600">
                  （省 ¥{(effectiveAmount - Number(calcMoney)).toFixed(2)}）
                </span>
              )}
            </p>
          ) : null}
        </div>
      </div>

      {/* 支付方式选择 */}
      {info.pay_methods.length > 0 && (
        <div className="mb-6">
          <p className="text-sm font-medium text-gray-700 mb-3">选择支付方式</p>
          <div className="grid grid-cols-2 gap-3 sm:grid-cols-3">
            {info.pay_methods.map((method) => (
              <button
                key={method}
                type="button"
                onClick={() => setSelectedMethod(method)}
                className={`rounded-lg border px-4 py-3 text-sm font-medium transition-all ${
                  selectedMethod === method
                    ? 'border-brand-500 bg-brand-50 text-brand-700 shadow-sm'
                    : 'border-gray-200 text-gray-600 hover:border-brand-300 hover:bg-brand-50/50'
                }`}
              >
                {payLabel(method)}
              </button>
            ))}
          </div>
        </div>
      )}

      {/* 充值按钮 */}
      <button
        type="button"
        disabled={!effectiveAmount || !selectedMethod || payLoading || effectiveAmount <= 0}
        onClick={handlePay}
        className="flex w-full items-center justify-center gap-2 rounded-xl bg-brand-600 py-3 text-sm font-semibold text-white shadow-lg shadow-brand-200 transition-all hover:bg-brand-700 active:scale-[0.98] disabled:cursor-not-allowed disabled:opacity-50"
      >
        {payLoading ? (
          <>
            <Loader2 className="h-4 w-4 animate-spin" />
            处理中...
          </>
        ) : (
          <>
            <ExternalLink className="h-4 w-4" />
            前往支付
          </>
        )}
      </button>
    </div>
  )
}

/** 充值记录表格 */
function TopUpHistory({
  refreshKey,
}: {
  refreshKey: number
}) {
  const [items, setItems] = useState<TopUpItem[]>([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(1)
  const [loading, setLoading] = useState(false)
  const pageSize = 10

  useEffect(() => {
    setLoading(true)
    getTopUpHistory(page, pageSize)
      .then((data) => {
        setItems(data.items)
        setTotal(data.total)
      })
      .catch(() => {})
      .finally(() => setLoading(false))
  }, [page, refreshKey])

  const totalPages = Math.max(1, Math.ceil(total / pageSize))

  const statusBadge = (status: string) => {
    switch (status) {
      case 'success':
        return (
          <span className="inline-flex items-center gap-1 rounded-full bg-green-50 px-2.5 py-0.5 text-xs font-medium text-green-700">
            <CheckCircle2 className="h-3 w-3" />
            成功
          </span>
        )
      case 'pending':
        return (
          <span className="inline-flex items-center gap-1 rounded-full bg-yellow-50 px-2.5 py-0.5 text-xs font-medium text-yellow-700">
            <Clock className="h-3 w-3" />
            处理中
          </span>
        )
      case 'failed':
        return (
          <span className="inline-flex items-center gap-1 rounded-full bg-red-50 px-2.5 py-0.5 text-xs font-medium text-red-700">
            <XCircle className="h-3 w-3" />
            失败
          </span>
        )
      default:
        return (
          <span className="rounded-full bg-gray-50 px-2.5 py-0.5 text-xs text-gray-600">
            {status}
          </span>
        )
    }
  }

  return (
    <div className="rounded-2xl border border-gray-200 bg-white p-8 shadow-sm">
      <div className="flex items-center gap-3 mb-6">
        <div className="flex h-12 w-12 items-center justify-center rounded-xl bg-brand-100">
          <History className="h-6 w-6 text-brand-600" />
        </div>
        <div>
          <h2 className="text-lg font-semibold text-gray-900">充值记录</h2>
          <p className="text-sm text-gray-500">共 {total} 条记录</p>
        </div>
      </div>

      {loading ? (
        <div className="flex items-center justify-center py-12">
          <Loader2 className="h-6 w-6 animate-spin text-brand-500" />
        </div>
      ) : items.length === 0 ? (
        <p className="py-12 text-center text-sm text-gray-400">暂无充值记录</p>
      ) : (
        <>
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-gray-100 text-left text-xs font-medium text-gray-400 uppercase tracking-wider">
                  <th className="pb-3 pr-4">订单号</th>
                  <th className="pb-3 pr-4">金额</th>
                  <th className="pb-3 pr-4">实付</th>
                  <th className="pb-3 pr-4">支付方式</th>
                  <th className="pb-3 pr-4">状态</th>
                  <th className="pb-3 pr-4">时间</th>
                </tr>
              </thead>
              <tbody>
                {items.map((item) => (
                  <tr key={item.id} className="border-b border-gray-50 hover:bg-gray-50/50">
                    <td className="py-3 pr-4 font-mono text-xs text-gray-600">
                      {(item.trade_no || '').slice(-12) || '-'}
                    </td>
                    <td className="py-3 pr-4 font-medium">¥{item.amount}</td>
                    <td className="py-3 pr-4 text-gray-600">¥{(Number(item.money) || 0).toFixed(4)}</td>
                    <td className="py-3 pr-4 text-gray-600">
                      {payLabel(item.payment_provider) || payLabel(item.payment_method)}
                    </td>
                    <td className="py-3 pr-4">{statusBadge(item.status)}</td>
                    <td className="py-3 pr-4 text-gray-400 text-xs">
                      {item.create_time || '-'}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {/* 分页 */}
          {totalPages > 1 && (
            <div className="mt-4 flex items-center justify-center gap-2">
              <button
                type="button"
                disabled={page <= 1}
                onClick={() => setPage((p) => Math.max(1, p - 1))}
                className="rounded-lg border border-gray-200 px-3 py-1.5 text-xs font-medium text-gray-600 hover:bg-gray-50 disabled:opacity-40"
              >
                上一页
              </button>
              <span className="text-xs text-gray-500">
                {page} / {totalPages}
              </span>
              <button
                type="button"
                disabled={page >= totalPages}
                onClick={() => setPage((p) => Math.min(totalPages, p + 1))}
                className="rounded-lg border border-gray-200 px-3 py-1.5 text-xs font-medium text-gray-600 hover:bg-gray-50 disabled:opacity-40"
              >
                下一页
              </button>
            </div>
          )}
        </>
      )}
    </div>
  )
}

/* ------------------------------------------------------------------ */
/*  页面主组件                                                         */
/* ------------------------------------------------------------------ */

function WalletPage() {
  const { isAuthenticated } = useAuth()
  const [info, setInfo] = useState<TopUpInfo | null>(null)
  const [wallet, setWallet] = useState<WalletInfo | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [refreshKey, setRefreshKey] = useState(0)

  useEffect(() => {
    if (!isAuthenticated) {
      setLoading(false)
      return
    }
    setLoading(true)
    setError(null)
    Promise.all([getTopUpInfo(), getWallet()])
      .then(([infoData, walletData]) => {
        setInfo(infoData)
        setWallet(walletData)
      })
      .catch((e: unknown) => {
        setError(e instanceof Error ? e.message : '加载失败')
      })
      .finally(() => setLoading(false))
  }, [isAuthenticated, refreshKey])

  if (!isAuthenticated) {
    return (
      <div className="flex min-h-[60vh] items-center justify-center">
        <div className="text-center">
          <Wallet className="mx-auto h-12 w-12 text-gray-300 mb-4" />
          <p className="text-gray-500 mb-4">请先登录后查看钱包</p>
          <a
            href="/login"
            className="inline-flex items-center gap-2 rounded-lg bg-brand-600 px-6 py-2.5 text-sm font-semibold text-white hover:bg-brand-700 transition-colors"
          >
            前往登录
          </a>
        </div>
      </div>
    )
  }

  if (loading) {
    return (
      <div className="flex min-h-[60vh] items-center justify-center">
        <Loader2 className="h-8 w-8 animate-spin text-brand-500" />
      </div>
    )
  }

  if (error) {
    return (
      <div className="flex min-h-[60vh] items-center justify-center">
        <div className="text-center">
          <p className="text-red-500 mb-4">{error}</p>
          <button
            type="button"
            onClick={() => setRefreshKey((k) => k + 1)}
            className="text-brand-600 text-sm hover:underline"
          >
            点击重试
          </button>
        </div>
      </div>
    )
  }

  return (
    <section className="bg-gray-50/50">
      <div className="mx-auto max-w-6xl px-6 py-12 sm:py-20">
        {/* 页面标题 */}
        <div className="mb-10">
          <h1 className="text-3xl font-bold text-gray-900 sm:text-4xl">钱包</h1>
          <p className="mt-2 text-gray-500">管理你的余额、充值与消费记录</p>
        </div>

        {/* 两栏布局 */}
        <div className="grid grid-cols-1 gap-8 lg:grid-cols-5">
          {/* 左侧：余额卡片 + 充值记录 */}
          <div className="space-y-8 lg:col-span-3">
            <BalanceCard wallet={wallet} loading={false} />
            <TopUpHistory refreshKey={refreshKey} />
          </div>

          {/* 右侧：充值面板 */}
          <div className="lg:col-span-2">
            {info ? (
              <TopUpPanel
                info={info}
                onSuccess={() => setRefreshKey((k) => k + 1)}
              />
            ) : (
              <div className="rounded-2xl border border-gray-200 bg-white p-8 shadow-sm flex items-center justify-center py-16">
                <Loader2 className="h-6 w-6 animate-spin text-brand-500" />
              </div>
            )}
          </div>
        </div>
      </div>
    </section>
  )
}
