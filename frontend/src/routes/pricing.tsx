import { createFileRoute, useNavigate } from '@tanstack/react-router'
import { Check, Loader2 } from 'lucide-react'
import { useCallback, useState } from 'react'
import { useAuth } from '@/stores/auth'

export const Route = createFileRoute('/pricing')({
  component: PricingPage,
})

const plans = [
  {
    name: '免费版',
    desc: '适合个人日常使用',
    price: '¥0',
    period: '永久免费',
    features: ['基础模型对话', '本地数据存储', 'Markdown / 代码高亮', '社区支持'],
    highlight: false,
  },
  {
    name: '专业版',
    desc: '适合重度用户与开发者',
    price: '¥39',
    period: '每月',
    features: ['全部模型接入', '文件与图片问答', '提示词云同步', '网页搜索与读取', 'MCP 工具扩展', '优先技术支持'],
    highlight: true,
  },
  {
    name: '团队版',
    desc: '适合团队协作',
    price: '联系我们',
    period: '按席位计费',
    features: ['团队协作空间', '统一计费与管理', 'API 用量监控', 'SSO / SAML 登录', '专属客户经理'],
    highlight: false,
  },
]

interface RelayConfig {
  url: string
  apiKey: string
}

function PricingPage() {
  const { token, isAuthenticated } = useAuth()
  const navigate = useNavigate()
  const [loadingPlan, setLoadingPlan] = useState<string | null>(null)

  const handleGetStarted = useCallback(
    async (planName: string) => {
      if (!isAuthenticated || !token) {
        // 未登录 → 跳转到登录页
        navigate({ to: '/login' })
        return
      }

      setLoadingPlan(planName)
      try {
        const resp = await fetch('/api/relay-station/config', {
          headers: { Authorization: `Bearer ${token}` },
        })

        if (!resp.ok) {
          // 获取中转站配置失败，回退到 /download
          navigate({ to: '/download' })
          return
        }

        const result: { code: number; data: RelayConfig } = await resp.json()
        if (result.code !== 0 || !result.data?.url) {
          navigate({ to: '/download' })
          return
        }

        // 去除 /v1 后缀，拼接 /dashboard/overview 后跳转
        const baseUrl = result.data.url.replace(/\/v1\/?$/, '')
        window.location.href = `${baseUrl}/dashboard/overview`
      } catch {
        // 网络异常，回退到 /download
        navigate({ to: '/download' })
      } finally {
        setLoadingPlan(null)
      }
    },
    [token, isAuthenticated, navigate],
  )

  return (
    <section className="bg-gray-50">
      <div className="mx-auto max-w-6xl px-6 py-20 sm:py-28">
        <div className="text-center">
          <h1 className="text-4xl font-bold text-gray-900 sm:text-5xl">定价方案</h1>
          <p className="mt-4 text-gray-600">从免费开始，按需升级，随时可取消。</p>
        </div>

        <div className="mt-14 grid grid-cols-1 gap-8 md:grid-cols-3">
          {plans.map((plan) => (
            <div
              key={plan.name}
              className={`relative flex flex-col rounded-2xl bg-white p-8 shadow-sm ${
                plan.highlight
                  ? 'border-2 border-brand-500 ring-1 ring-brand-500'
                  : 'border border-gray-200'
              }`}
            >
              {plan.highlight && (
                <span className="absolute -top-3 left-1/2 -translate-x-1/2 rounded-full bg-brand-600 px-4 py-1 text-xs font-semibold text-white">
                  最受欢迎
                </span>
              )}

              <h3 className="text-xl font-semibold text-gray-900">{plan.name}</h3>
              <p className="mt-1 text-sm text-gray-500">{plan.desc}</p>

              <div className="mt-6 flex items-baseline gap-2">
                <span className="text-4xl font-extrabold text-gray-900">{plan.price}</span>
                {plan.period && <span className="text-sm text-gray-500">{plan.period}</span>}
              </div>

              <ul className="mt-8 flex-1 space-y-3.5">
                {plan.features.map((f) => (
                  <li key={f} className="flex items-center gap-3 text-sm text-gray-600">
                    <Check className="h-4 w-4 shrink-0 text-brand-600" />
                    {f}
                  </li>
                ))}
              </ul>

              <button
                type="button"
                disabled={loadingPlan === plan.name}
                onClick={() => handleGetStarted(plan.name)}
                className={`mt-8 flex items-center justify-center gap-2 rounded-lg px-5 py-3 text-center text-sm font-semibold transition-colors disabled:opacity-60 ${
                  plan.highlight
                    ? 'bg-brand-600 text-white hover:bg-brand-700'
                    : 'border border-gray-300 text-gray-700 hover:border-brand-600 hover:text-brand-600'
                }`}
              >
                {loadingPlan === plan.name && <Loader2 className="h-4 w-4 animate-spin" />}
                开始使用
              </button>
            </div>
          ))}
        </div>
      </div>
    </section>
  )
}
