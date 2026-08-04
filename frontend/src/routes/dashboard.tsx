import { createFileRoute } from '@tanstack/react-router'
import { useAuth } from '@/stores/auth'
import { useState, useEffect, useCallback, useMemo } from 'react'
import { BarChart3, TrendingUp, Zap, Clock, Activity, Coins } from 'lucide-react'
import { VChart } from '@visactor/react-vchart'
import {
  getUserQuotaDates,
  type QuotaDataItem,
  ApiError,
} from '@/api/dashboard'
import { getWallet, type WalletInfo } from '@/api/wallet'

export const Route = createFileRoute('/dashboard')({
  component: DashboardPage,
})

/* ==================================================================== */
/*  格式化工具                                                           */
/* ==================================================================== */

function fmtTokens(n: number): string {
  if (n >= 1_000_000_000) return (n / 1_000_000_000).toFixed(1) + 'B'
  if (n >= 1_000_000) return (n / 1_000_000).toFixed(1) + 'M'
  if (n >= 1_000) return (n / 1_000).toFixed(1) + 'K'
  return String(Math.floor(n))
}

function fmtCount(n: number): string {
  if (n >= 1_000_000) return (n / 1_000_000).toFixed(1) + 'M'
  if (n >= 1_000) return (n / 1_000).toFixed(1) + 'K'
  return String(Math.floor(n))
}

function fmtQuota(q: number): string {
  const yuan = q / 500_000
  if (yuan >= 1) return '¥' + yuan.toFixed(2)
  return '¥' + yuan.toFixed(4)
}

function fmtMoney(n: number): string {
  if (n >= 1) return '¥' + n.toFixed(2)
  return '¥' + n.toFixed(4)
}

function fmtHourLabel(ts: number): string {
  const d = new Date(ts * 1000)
  return `${d.getMonth() + 1}/${d.getDate()} ${String(d.getHours()).padStart(2, '0')}:00`
}

function fmtDayLabel(ts: number): string {
  const d = new Date(ts * 1000)
  return `${d.getMonth() + 1}/${d.getDate()}`
}

/* ==================================================================== */
/*  SVG Sparkline                                                       */
/* ==================================================================== */

function Sparkline({ data, height = 48, variant = 'line' }: {
  data: number[]
  height?: number
  variant?: 'line' | 'bar'
}) {
  if (data.length < 2) return <div style={{ height }} />

  const min = Math.min(...data)
  const max = Math.max(...data)
  const range = max - min || 1
  const w = Math.max(data.length * 6, 60)

  if (variant === 'bar') {
    const barW = Math.max(1, (w / data.length) - 1)
    return (
      <svg viewBox={`0 0 ${w} ${height}`} style={{ width: w, height }} aria-hidden="true">
        {data.map((v, i) => {
          const barH = Math.max(1, ((v - min) / range) * (height - 2))
          return (
            <rect
              key={i}
              x={i * (barW + 1)}
              y={height - barH}
              width={barW}
              height={barH}
              rx="1"
              fill="currentColor"
              className="text-brand-300/60"
            />
          )
        })}
      </svg>
    )
  }

  const points = data.map((v, i) => {
    const x = (i / (data.length - 1)) * w
    const y = height - ((v - min) / range) * (height - 4) - 2
    return `${x.toFixed(1)},${y.toFixed(1)}`
  }).join(' ')

  return (
    <svg viewBox={`0 0 ${w} ${height}`} style={{ width: w, height }} aria-hidden="true">
      <polygon
        points={`0,${height} ${points} ${w},${height}`}
        fill="url(#sparkGrad)"
      />
      <polyline
        points={points}
        fill="none"
        stroke="currentColor"
        strokeWidth="1.5"
        strokeLinecap="round"
        strokeLinejoin="round"
        className="text-brand-400"
      />
      <defs>
        <linearGradient id="sparkGrad" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stopColor="var(--color-brand-400)" stopOpacity="0.25" />
          <stop offset="100%" stopColor="var(--color-brand-400)" stopOpacity="0" />
        </linearGradient>
      </defs>
    </svg>
  )
}

/* ==================================================================== */
/*  StatCard（对标 new-api stat-card.tsx）                                 */
/* ==================================================================== */

function StatCard({ icon: Icon, label, value, desc, sparklineData, sparklineVariant = 'line' }: {
  icon: React.ComponentType<{ className?: string }>
  label: string
  value: string
  desc?: string
  sparklineData?: number[]
  sparklineVariant?: 'line' | 'bar'
}) {
  return (
    <div className="flex flex-col gap-1.5 rounded-xl border border-gray-100 bg-white p-4 shadow-sm transition-shadow hover:shadow-md">
      <div className="flex items-center gap-2">
        <Icon className="h-4 w-4 text-gray-400 shrink-0" />
        <span className="text-xs font-medium text-gray-500 truncate">{label}</span>
      </div>
      <div className="text-xl font-bold text-gray-900 tabular-nums">{value}</div>
      {desc && <div className="text-[11px] text-gray-400 -mt-0.5">{desc}</div>}
      {sparklineData && sparklineData.length >= 2 && (
        <div className="mt-1">
          <Sparkline data={sparklineData} height={36} variant={sparklineVariant} />
        </div>
      )}
    </div>
  )
}

/* ==================================================================== */
/*  VChart 图表配置构建                                                   */
/* ==================================================================== */

/** 构建消费分布图 spec（bar/area）。 */
function buildDistributionSpec(
  data: QuotaDataItem[],
  chartType: 'bar' | 'area',
  timeLabels: string[],
) {
  // 聚合：按 model_name 分组，每个时间桶的 quota
  const modelSet = new Set<string>()
  const timeSet = new Set<number>()
  const modelTimeMap = new Map<string, Map<number, number>>()

  for (const item of data) {
    const m = item.model_name || 'Unknown'
    const t = item.created_at
    const q = item.quota || 0
    modelSet.add(m)
    timeSet.add(t)
    if (!modelTimeMap.has(m)) modelTimeMap.set(m, new Map())
    const tm = modelTimeMap.get(m)!
    tm.set(t, (tm.get(t) || 0) + q)
  }

  const sortedTimes = [...timeSet].sort((a, b) => a - b)
  const models = [...modelSet]
  const values = sortedTimes.flatMap(t =>
    models.map(m => ({
      Time: fmtHourLabel(t),
      Usage: ((modelTimeMap.get(m)?.get(t) || 0) / 500_000),
      Model: m,
    }))
  )

  if (chartType === 'bar') {
    return {
      type: 'bar',
      data: { values },
      xField: 'Time',
      yField: 'Usage',
      seriesField: 'Model',
      stack: true,
      legends: { visible: true, orient: 'bottom' },
      bar: { width: '60%' as unknown as number },
      axes: [
        { orient: 'bottom', label: { fontSize: 10, rotate: 45 } },
        { orient: 'left', title: { text: '¥' } },
      ],
      tooltip: { visible: true },
    }
  }

  // area
  return {
    type: 'area',
    data: { values },
    xField: 'Time',
    yField: 'Usage',
    seriesField: 'Model',
    stack: false,
    legends: { visible: true, orient: 'bottom' },
    point: { visible: false },
    line: { visible: true },
    area: { visible: true, opacity: 0.15 },
    axes: [
      { orient: 'bottom', label: { fontSize: 10, rotate: 45 } },
      { orient: 'left', title: { text: '¥' } },
    ],
    tooltip: { visible: true },
    crosshair: {
      xField: { visible: true },
    },
  }
}

/** 构建模型排行柱状图 spec。 */
function buildRankBarSpec(data: QuotaDataItem[]) {
  const modelQuota = new Map<string, number>()
  for (const item of data) {
    const m = item.model_name || 'Unknown'
    modelQuota.set(m, (modelQuota.get(m) || 0) + (item.quota || 0))
  }
  const sorted = [...modelQuota.entries()]
    .sort((a, b) => b[1] - a[1])
    .slice(0, 15)
    .map(([m, q]) => ({ Model: m, '预估费用': q / 500_000 }))

  return {
    type: 'bar' as const,
    data: { values: sorted },
    xField: '预估费用',
    yField: 'Model',
    direction: 'horizontal' as const,
    bar: { width: '60%' as unknown as number },
    axes: [
      { orient: 'bottom', title: { text: '¥' } },
    ],
    tooltip: { visible: true },
  }
}

/** 构建模型占比饼图 spec。 */
function buildPieSpec(data: QuotaDataItem[]) {
  const modelCount = new Map<string, number>()
  for (const item of data) {
    const m = item.model_name || 'Unknown'
    modelCount.set(m, (modelCount.get(m) || 0) + (item.count || 0))
  }
  const values = [...modelCount.entries()]
    .sort((a, b) => b[1] - a[1])
    .slice(0, 10)
    .map(([m, c]) => ({ type: m, value: c }))

  return {
    type: 'pie' as const,
    data: { values },
    categoryField: 'type',
    valueField: 'value',
    radius: 0.8,
    label: { visible: true, formatMethod: (_t: string, _d: string, item: { type: string; value: number }) => item.type },
    legends: { visible: true, orient: 'bottom' },
    tooltip: { visible: true },
  }
}

/** 构建调用趋势图 spec。 */
function buildTrendSpec(data: QuotaDataItem[]) {
  const modelSet = new Set<string>()
  const timeSet = new Set<number>()
  const modelTimeMap = new Map<string, Map<number, number>>()

  for (const item of data) {
    const m = item.model_name || 'Unknown'
    const t = item.created_at
    const c = item.count || 0
    modelSet.add(m)
    timeSet.add(t)
    if (!modelTimeMap.has(m)) modelTimeMap.set(m, new Map())
    const tm = modelTimeMap.get(m)!
    tm.set(t, (tm.get(t) || 0) + c)
  }

  const sortedTimes = [...timeSet].sort((a, b) => a - b)
  const models = [...modelSet]
  const values = sortedTimes.flatMap(t =>
    models.map(m => ({
      Time: fmtHourLabel(t),
      Count: modelTimeMap.get(m)?.get(t) || 0,
      Model: m,
    }))
  )

  return {
    type: 'area' as const,
    data: { values },
    xField: 'Time',
    yField: 'Count',
    seriesField: 'Model',
    stack: false,
    legends: { visible: true, orient: 'bottom' },
    point: { visible: false },
    line: { visible: true },
    area: { visible: true, opacity: 0.15 },
    axes: [
      { orient: 'bottom', label: { fontSize: 10, rotate: 45 } },
      { orient: 'left', title: { text: '请求数' } },
    ],
    tooltip: { visible: true },
    crosshair: {
      xField: { visible: true },
    },
  }
}

/* ==================================================================== */
/*  主页面                                                               */
/* ==================================================================== */

type ChartTab = 'trend' | 'proportion' | 'top'
type DistChartType = 'bar' | 'area'

function DashboardPage() {
  const { token } = useAuth()

  const [quotaData, setQuotaData] = useState<QuotaDataItem[]>([])
  const [wallet, setWallet] = useState<WalletInfo | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  // 图表状态
  const [distChartType, setDistChartType] = useState<DistChartType>('bar')
  const [chartTab, setChartTab] = useState<ChartTab>('trend')

  // 时间范围：默认1天(24h)
  const [rangeDays, setRangeDays] = useState(1)

  const timeRange = useMemo(() => {
    const now = Math.floor(Date.now() / 1000)
    return {
      start: now - rangeDays * 86400,
      end: now,
    }
  }, [rangeDays])

  const fetchData = useCallback(async () => {
    if (!token) return
    setLoading(true)
    setError(null)
    try {
      const [data, w] = await Promise.all([
        getUserQuotaDates(token, timeRange.start, timeRange.end),
        getWallet(token),
      ])
      setQuotaData(data)
      setWallet(w)
    } catch (e) {
      if (e instanceof ApiError) {
        setError(e.code === 401 ? '请先登录' : e.message)
      } else {
        setError('加载失败，请稍后重试')
      }
    } finally {
      setLoading(false)
    }
  }, [token, timeRange.start, timeRange.end])

  useEffect(() => {
    fetchData()
  }, [fetchData])

  /* ---- 聚合统计 ---- */
  const stats = useMemo(() => {
    const totalCount = quotaData.reduce((a, d) => a + (d.count || 0), 0)
    const totalQuota = quotaData.reduce((a, d) => a + (d.quota || 0), 0)
    const totalTokens = quotaData.reduce((a, d) => a + (d.token_used || 0), 0)
    const timeMinutes = Math.max(1, (timeRange.end - timeRange.start) / 60)
    const avgRpm = totalCount / timeMinutes
    const avgTpm = totalTokens / timeMinutes
    return { totalCount, totalQuota, totalTokens, avgRpm, avgTpm }
  }, [quotaData, timeRange])

  /* ---- Sparkline 数据 ---- */
  const sparklines = useMemo(() => {
    const buckets = 24
    const bucketSec = Math.max(1, (timeRange.end - timeRange.start) / buckets)
    const countBuckets = new Array(buckets).fill(0)
    const quotaBuckets = new Array(buckets).fill(0)
    const tokenBuckets = new Array(buckets).fill(0)
    for (const item of quotaData) {
      const idx = Math.min(buckets - 1, Math.floor((item.created_at - timeRange.start) / bucketSec))
      countBuckets[idx] += item.count || 0
      quotaBuckets[idx] += item.quota || 0
      tokenBuckets[idx] += item.token_used || 0
    }
    return { count: countBuckets, quota: quotaBuckets, token: tokenBuckets }
  }, [quotaData, timeRange])

  /* ---- VChart spec ---- */
  const distSpec = useMemo(() => {
    if (quotaData.length === 0) return null
    return buildDistributionSpec(quotaData, distChartType, [])
  }, [quotaData, distChartType])

  const trendSpec = useMemo(() => {
    if (quotaData.length === 0) return null
    return buildTrendSpec(quotaData)
  }, [quotaData])

  const pieSpec = useMemo(() => {
    if (quotaData.length === 0) return null
    return buildPieSpec(quotaData)
  }, [quotaData])

  const rankSpec = useMemo(() => {
    if (quotaData.length === 0) return null
    return buildRankBarSpec(quotaData)
  }, [quotaData])

  // 分布图总配额
  const distTotal = useMemo(() =>
    quotaData.reduce((a, d) => a + (d.quota || 0), 0),
    [quotaData])

  /* ---- 时间范围选项 ---- */
  const rangeOptions = [1, 7, 14, 29]
  const rangeLabels: Record<number, string> = { 1: '1天', 7: '7天', 14: '14天', 29: '29天' }

  /* ================================================================ */
  /*  Render                                                           */
  /* ================================================================ */

  if (loading) {
    return (
      <section className="bg-gray-50/50">
        <div className="mx-auto max-w-6xl px-6 py-12 sm:py-20">
          <div className="mb-10">
            <div className="h-8 w-48 bg-gray-200 rounded animate-pulse" />
            <div className="h-4 w-32 bg-gray-200 rounded mt-2 animate-pulse" />
          </div>
          <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-5 gap-3 mb-8">
            {Array.from({ length: 5 }).map((_, i) => (
              <div key={i} className="rounded-xl border border-gray-100 bg-white p-4 animate-pulse">
                <div className="h-3 w-16 bg-gray-200 rounded mb-2" />
                <div className="h-6 w-20 bg-gray-200 rounded" />
              </div>
            ))}
          </div>
          <div className="rounded-xl border border-gray-100 bg-white p-6 animate-pulse">
            <div className="h-5 w-32 bg-gray-200 rounded mb-4" />
            <div className="h-64 bg-gray-100 rounded" />
          </div>
        </div>
      </section>
    )
  }

  if (error) {
    return (
      <section className="bg-gray-50/50">
        <div className="mx-auto max-w-6xl px-6 py-12 sm:py-20">
          <div className="mb-10">
            <h1 className="text-3xl font-bold text-gray-900 sm:text-4xl">数据看板</h1>
          </div>
          <div className="rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-700">
            {error}
            <button type="button" onClick={fetchData}
              className="ml-3 font-medium underline hover:text-red-800">重试</button>
          </div>
        </div>
      </section>
    )
  }

  return (
    <section className="bg-gray-50/50">
      <div className="mx-auto max-w-6xl px-6 py-12 sm:py-20">
        {/* ---- 标题 ---- */}
        <div className="mb-6">
          <h1 className="text-3xl font-bold text-gray-900 sm:text-4xl">数据看板</h1>
          <p className="mt-2 text-gray-500">模型调用分析</p>
        </div>

        <div className="space-y-4">
          {/* ---- 工具栏 ---- */}
          <div className="flex flex-wrap items-center justify-between gap-3">
            {/* 时间范围 */}
            <div className="flex items-center gap-1 rounded-lg border border-gray-200 bg-white p-1">
              {rangeOptions.map((d) => (
                <button
                  key={d}
                  type="button"
                  onClick={() => setRangeDays(d)}
                  className={`px-3 py-1.5 text-xs font-medium rounded-md transition-colors ${
                    rangeDays === d
                      ? 'bg-brand-600 text-white shadow-sm'
                      : 'text-gray-600 hover:text-gray-900'
                  }`}
                >
                  {rangeLabels[d]}
                </button>
              ))}
            </div>
          </div>

          {/* ---- 统计卡片（对标 LogStatCards） ---- */}
          <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-5 gap-3">
            <StatCard
              icon={Activity}
              label="总请求数"
              value={fmtCount(stats.totalCount)}
              desc="统计计数"
              sparklineData={sparklines.count}
              sparklineVariant="bar"
            />
            <StatCard
              icon={Coins}
              label="总配额消耗"
              value={fmtQuota(stats.totalQuota)}
              desc="统计配额"
              sparklineData={sparklines.quota}
            />
            <StatCard
              icon={Zap}
              label="总 Token 用量"
              value={fmtTokens(stats.totalTokens)}
              desc="统计 Token"
              sparklineData={sparklines.token}
            />
            <StatCard
              icon={TrendingUp}
              label="平均 RPM"
              value={stats.avgRpm < 1 ? stats.avgRpm.toFixed(2) : fmtCount(Math.round(stats.avgRpm))}
              desc="每分钟请求数"
            />
            <StatCard
              icon={Clock}
              label="平均 TPM"
              value={stats.avgTpm < 1 ? stats.avgTpm.toFixed(1) : fmtTokens(Math.round(stats.avgTpm))}
              desc="每分钟 Token 数"
            />
          </div>

          {/* ---- 钱包信息 ---- */}
          {wallet && (
            <div className="rounded-xl border border-gray-100 bg-white p-4 shadow-sm">
              <div className="flex flex-wrap items-center gap-6 text-sm">
                <div>
                  <span className="text-gray-500">当前余额：</span>
                  <span className="font-semibold text-gray-900 tabular-nums">{fmtMoney(wallet.balance)}</span>
                </div>
                <div>
                  <span className="text-gray-500">累计消费：</span>
                  <span className="font-semibold text-gray-900 tabular-nums">{fmtMoney(wallet.historical_consumption)}</span>
                </div>
              </div>
            </div>
          )}

          {/* ---- 消费分布图 ---- */}
          {quotaData.length > 0 && (
            <div className="rounded-xl border border-gray-100 bg-white shadow-sm overflow-hidden">
              <div className="flex items-center justify-between border-b border-gray-100 px-5 py-3.5">
                <div className="flex items-center gap-3">
                  <h3 className="text-sm font-semibold text-gray-900">配额分布</h3>
                  <span className="text-xs text-gray-400">合计：{fmtQuota(distTotal)}</span>
                </div>
                <div className="flex items-center gap-1 rounded-lg border border-gray-200 p-0.5">
                  <button
                    type="button"
                    onClick={() => setDistChartType('bar')}
                    className={`px-2.5 py-1 text-xs rounded-md transition-colors ${
                      distChartType === 'bar' ? 'bg-gray-200 text-gray-900 font-medium' : 'text-gray-500'
                    }`}
                  >柱状图</button>
                  <button
                    type="button"
                    onClick={() => setDistChartType('area')}
                    className={`px-2.5 py-1 text-xs rounded-md transition-colors ${
                      distChartType === 'area' ? 'bg-gray-200 text-gray-900 font-medium' : 'text-gray-500'
                    }`}
                  >面积图</button>
                </div>
              </div>
              <div className="p-2">
                <div className="h-72 sm:h-80">
                  {distSpec && <VChart spec={distSpec} options={{ mode: 'desktop-browser' }} />}
                </div>
              </div>
            </div>
          )}

          {/* ---- 模型分析图 ---- */}
          {quotaData.length > 0 && (
            <div className="rounded-xl border border-gray-100 bg-white shadow-sm overflow-hidden">
              <div className="flex items-center border-b border-gray-100 px-5 py-3.5">
                <div className="flex items-center gap-1 rounded-lg border border-gray-200 p-0.5">
                  {([
                    ['trend', '调用趋势'],
                    ['proportion', '调用分布'],
                    ['top', '调用排行'],
                  ] as const).map(([key, label]) => (
                    <button
                      key={key}
                      type="button"
                      onClick={() => setChartTab(key)}
                      className={`px-3 py-1 text-xs rounded-md transition-colors ${
                        chartTab === key ? 'bg-gray-200 text-gray-900 font-medium' : 'text-gray-500'
                      }`}
                    >{label}</button>
                  ))}
                </div>
              </div>
              <div className="p-2">
                <div className="h-72 sm:h-80">
                  {chartTab === 'trend' && trendSpec && (
                    <VChart spec={trendSpec} options={{ mode: 'desktop-browser' }} />
                  )}
                  {chartTab === 'proportion' && pieSpec && (
                    <VChart spec={pieSpec} options={{ mode: 'desktop-browser' }} />
                  )}
                  {chartTab === 'top' && rankSpec && (
                    <VChart spec={rankSpec} options={{ mode: 'desktop-browser' }} />
                  )}
                </div>
              </div>
            </div>
          )}

          {/* ---- 空状态 ---- */}
          {quotaData.length === 0 && (
            <div className="flex min-h-[40vh] items-center justify-center rounded-2xl border border-dashed border-gray-300 bg-white">
              <div className="text-center px-6">
                <BarChart3 className="mx-auto h-12 w-12 text-gray-300 mb-4" />
                <p className="text-gray-500 text-lg font-medium">暂无用量数据</p>
                <p className="mt-1 text-sm text-gray-400">使用 API Key 发送请求后，数据将在此展示</p>
              </div>
            </div>
          )}
        </div>
      </div>
    </section>
  )
}
