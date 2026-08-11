import { createFileRoute, redirect, Link } from '@tanstack/react-router'
import { useQuery } from '@tanstack/react-query'
import { useAuth, authStore } from '@/stores/auth'
import { getDashboardSummary } from '@/api/dashboard'
import { getWallet } from '@/api/wallet'
import { getSessionBalance } from '@/api/session'
import { Coins, Activity, Plug, BarChart3, Wallet, ScrollText, ArrowRight, MessageCircle } from 'lucide-react'
import { KOD_WEB_URL } from '@/config'

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

  const sessionQuery = useQuery({
    queryKey: ['session-balance'],
    queryFn: getSessionBalance,
    staleTime: 15_000,
  })

  const isLoading = summaryQuery.isLoading || walletQuery.isLoading
  const totalRequests = summaryQuery.data?.reduce((s, m) => s + m.totalRequests, 0) ?? 0
  const modelCount = summaryQuery.data?.length ?? 0
  const balance = walletQuery.data?.balance ?? 0
  const isConnected = sessionQuery.data?.connect ?? false

  type NavCard = {
    to: string
    icon: typeof MessageCircle
    title: string
    desc: string
    color: string
    highlight?: boolean
    external?: boolean
  }

  const navCards: NavCard[] = [
    {
      to: KOD_WEB_URL,
      external: true,
      icon: MessageCircle,
      title: 'KOD 蒜宝',
      desc: '多模型 AI 对话助手 — 即刻开始与 GPT、Claude、DeepSeek 等模型对话',
      color: 'bg-brand-50 text-brand-600',
      highlight: true,
    },
    {
      to: '/console/dashboard',
      icon: BarChart3,
      title: '数据看板',
      desc: '配额消耗、调用趋势、模型排行 — 多维度可视化分析',
      color: 'bg-blue-50 text-blue-600',
    },
    {
      to: '/console/wallet',
      icon: Wallet,
      title: '钱包充值',
      desc: '余额管理、在线充值、交易记录查询',
      color: 'bg-green-50 text-green-600',
    },
    {
      to: '/console/logs',
      icon: ScrollText,
      title: '操作日志',
      desc: 'API 用量明细、日志同步状态、历史记录',
      color: 'bg-purple-50 text-purple-600',
    },
  ]

  return (
    <div className="animate-fade-in space-y-6 p-6">
      {/* Header */}
      <div>
        <h1 className="text-2xl font-bold text-gray-900">欢迎回来，{email}</h1>
        <p className="mt-1 text-sm text-gray-500">以下是你的账户概览</p>
      </div>

      {/* 3 关键指标卡 — 与看板互补，不重复 */}
      <div className="grid gap-4 sm:grid-cols-3">
        <div className="rounded-2xl border border-gray-100 bg-white p-5 shadow-sm">
          <div className="flex items-center justify-between">
            <span className="text-sm text-gray-500">可用余额</span>
            <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-green-50 text-green-600">
              <Coins className="h-5 w-5" />
            </div>
          </div>
          <p className="mt-2 text-2xl font-bold text-gray-900 tabular-nums">
            {isLoading ? '...' : `¥${balance.toFixed(2)}`}
          </p>
          <p className="mt-1 text-xs text-gray-400">余额可用于 API 调用消费</p>
        </div>

        <div className="rounded-2xl border border-gray-100 bg-white p-5 shadow-sm">
          <div className="flex items-center justify-between">
            <span className="text-sm text-gray-500">累计调用</span>
            <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-blue-50 text-blue-600">
              <Activity className="h-5 w-5" />
            </div>
          </div>
          <p className="mt-2 text-2xl font-bold text-gray-900 tabular-nums">
            {isLoading ? '...' : totalRequests.toLocaleString()}
          </p>
          <p className="mt-1 text-xs text-gray-400">覆盖 {modelCount} 个模型</p>
        </div>

        <div className="rounded-2xl border border-gray-100 bg-white p-5 shadow-sm">
          <div className="flex items-center justify-between">
            <span className="text-sm text-gray-500">连接状态</span>
            <div className={`flex h-10 w-10 items-center justify-center rounded-xl ${isConnected ? 'bg-brand-50 text-brand-600' : 'bg-gray-100 text-gray-400'}`}>
              <Plug className="h-5 w-5" />
            </div>
          </div>
          <p className={`mt-2 text-2xl font-bold tabular-nums ${isConnected ? 'text-brand-600' : 'text-gray-400'}`}>
            {sessionQuery.isLoading ? '...' : isConnected ? '已连接' : '未连接'}
          </p>
          <p className="mt-1 text-xs text-gray-400">
            {isConnected ? 'API Key 正常工作' : '请选择 API Key 开始使用'}
          </p>
        </div>
      </div>

      {/* 快捷导航卡片 */}
      <div>
        <h2 className="mb-4 text-lg font-semibold text-gray-900">快捷入口</h2>
        <div className="grid gap-4 sm:grid-cols-3">
          {navCards.map((card) => {
            const Icon = card.icon
            const cardContent = (
              <>
                <div className={`flex h-10 w-10 shrink-0 items-center justify-center rounded-xl ${card.highlight ? 'bg-brand-600 text-white shadow-sm shadow-brand-600/30' : card.color}`}>
                  <Icon className="h-5 w-5" />
                </div>
                <div className="min-w-0">
                  <h3 className="font-semibold text-gray-900 group-hover:text-brand-600 transition-colors">
                    {card.title}
                  </h3>
                  <p className="mt-1 text-xs text-gray-500 line-clamp-2">{card.desc}</p>
                </div>
                <ArrowRight className="mt-1 h-4 w-4 shrink-0 text-gray-300 group-hover:text-brand-500 transition-colors" />
              </>
            )

            if (card.external) {
              return (
                <a
                  key={card.to}
                  href={card.to}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="group flex items-start gap-4 rounded-2xl border border-brand-200 bg-brand-50/40 p-5 shadow-sm transition-all hover:shadow-md hover:border-brand-300"
                >
                  {cardContent}
                </a>
              )
            }
            return (
              <Link
                key={card.to}
                to={card.to}
                className="group flex items-start gap-4 rounded-2xl border border-gray-100 bg-white p-5 shadow-sm transition-all hover:shadow-md hover:border-gray-200"
              >
                {cardContent}
              </Link>
            )
          })}
        </div>
      </div>
    </div>
  )
}
