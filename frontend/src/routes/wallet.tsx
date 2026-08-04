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
  type PayMethod,
} from '@/api/wallet'
import { Loader2, Wallet, History, ExternalLink, TrendingUp, Clock, CheckCircle2, XCircle } from 'lucide-react'

export const Route = createFileRoute('/wallet')({
  component: WalletPage,
})

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
  token,
  onSuccess,
}: {
  info: TopUpInfo
  token: string
  onSuccess: () => void
}) {
  const [selectedAmount, setSelectedAmount] = useState<number | null>(null)
  const [selectedMethod, setSelectedMethod] = useState<PayMethod | null>(null)
  const [calcMoney, setCalcMoney] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const handleAmountSelect = useCallback(
    async (amount: number) => {
      setSelectedAmount(amount)
      setError(null)
      try {
        const money = await calculateAmount(token, amount)
        setCalcMoney(money)
      } catch {
        setCalcMoney(String(amount))
      }
    },
    [token],
  )

  const handlePay = useCallback(async () => {
    if (!selectedAmount || !selectedMethod) return
    setLoading(true)
    setError(null)
    try {
      const result = await createPay(token, selectedAmount, selectedMethod.type)
      // 新窗口打开支付链接
      window.open(result.paymentUrl, '_blank', 'noopener,noreferrer')
      onSuccess()
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : '支付失败，请重试')
    } finally {
      setLoading(false)
    }
  }, [selectedAmount, selectedMethod, token, onSuccess])

  return (
    <div className="rounded-2xl border border-gray-200 bg-white p-8 shadow-sm">
      <h2 className="text-lg font-semibold text-gray-900 mb-6">账户充值</h2>

      {error && (
        <div className="mb-4 rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
          {error}
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
                selectedAmount === amount
                  ? 'border-brand-500 bg-brand-50 text-brand-700 shadow-sm'
                  : 'border-gray-200 text-gray-700 hover:border-brand-300 hover:bg-brand-50/50'
              }`}
            >
              ¥{amount}
            </button>
          ))}
        </div>
        {calcMoney && selectedAmount && (
          <p className="mt-3 text-sm text-gray-500">
            实付金额：<span className="font-semibold text-gray-900">¥{calcMoney}</span>
          </p>
        )}
      </div>

      {/* 支付方式选择 */}
      <div className="mb-6">
        <p className="text-sm font-medium text-gray-700 mb-3">选择支付方式</p>
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-3">
          {info.pay_methods.map((method) => (
            <button
              key={method.type}
              type="button"
              onClick={() => setSelectedMethod(method)}
              className={`rounded-lg border px-4 py-3 text-sm font-medium transition-all ${
                selectedMethod?.type === method.type
                  ? 'border-brand-500 bg-brand-50 text-brand-700 shadow-sm'
                  : 'border-gray-200 text-gray-600 hover:border-brand-300 hover:bg-brand-50/50'
              }`}
            >
              {method.name}
            </button>
          ))}
        </div>
      </div>

      {/* 充值按钮 */}
      <button
        type="button"
        disabled={!selectedAmount || !selectedMethod || loading}
        onClick={handlePay}
        className="flex w-full items-center justify-center gap-2 rounded-xl bg-brand-600 py-3 text-sm font-semibold text-white shadow-lg shadow-brand-200 transition-all hover:bg-brand-700 active:scale-[0.98] disabled:cursor-not-allowed disabled:opacity-50"
      >
        {loading ? (
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
  token,
  refreshKey,
}: {
  token: string
  refreshKey: number
}) {
  const [items, setItems] = useState<TopUpItem[]>([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(1)
  const [loading, setLoading] = useState(false)
  const pageSize = 10

  useEffect(() => {
    setLoading(true)
    getTopUpHistory(token, page, pageSize)
      .then((data) => {
        setItems(data.items)
        setTotal(data.total)
      })
      .catch(() => {})
      .finally(() => setLoading(false))
  }, [token, page, refreshKey])

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
                      {(item.trade_no || '').slice(-12)}
                    </td>
                    <td className="py-3 pr-4 font-medium">¥{item.amount}</td>
                    <td className="py-3 pr-4 text-gray-600">¥{(Number(item.money) || 0).toFixed(4)}</td>
                    <td className="py-3 pr-4 text-gray-600">
                      {item.payment_method}
                    </td>
                    <td className="py-3 pr-4">{statusBadge(item.status)}</td>
                    <td className="py-3 pr-4 text-gray-400 text-xs">
                      {item.create_time > 0
                        ? new Date(item.create_time * 1000).toLocaleString()
                        : '-'}
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
  const { token, isAuthenticated } = useAuth()
  const [info, setInfo] = useState<TopUpInfo | null>(null)
  const [wallet, setWallet] = useState<WalletInfo | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [refreshKey, setRefreshKey] = useState(0)

  useEffect(() => {
    if (!token) {
      setLoading(false)
      return
    }
    setLoading(true)
    setError(null)
    Promise.all([getTopUpInfo(token), getWallet(token)])
      .then(([infoData, walletData]) => {
        setInfo(infoData)
        setWallet(walletData)
      })
      .catch((e: unknown) => {
        setError(e instanceof Error ? e.message : '加载失败')
      })
      .finally(() => setLoading(false))
  }, [token, refreshKey])

  if (!isAuthenticated || !token) {
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
            <TopUpHistory token={token} refreshKey={refreshKey} />
          </div>

          {/* 右侧：充值面板 */}
          <div className="lg:col-span-2">
            {info ? (
              <TopUpPanel
                info={info}
                token={token}
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
