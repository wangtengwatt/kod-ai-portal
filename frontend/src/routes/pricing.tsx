import { createFileRoute, Link } from '@tanstack/react-router'

/** 定价页：套餐对比（占位）。 */
export const Route = createFileRoute('/pricing')({
  component: PricingPage,
})

const plans = [
  {
    name: '免费版',
    price: '¥0',
    period: '永久免费',
    features: ['基础模型对话', '本地数据存储', '社区支持'],
    highlight: false,
  },
  {
    name: '专业版',
    price: '¥39',
    period: '每月',
    features: ['全部模型接入', '文件与图片问答', '提示词云同步', '优先支持'],
    highlight: true,
  },
  {
    name: '团队版',
    price: '联系我们',
    period: '按席位计费',
    features: ['团队协作空间', '统一计费与管理', '企业级支持'],
    highlight: false,
  },
]

function PricingPage() {
  return (
    <section className="mx-auto max-w-6xl px-6 py-20">
      <h1 className="text-center text-4xl font-bold text-gray-900">定价方案</h1>
      <p className="mt-4 text-center text-gray-600">
        从免费开始，按需升级，随时可取消。
      </p>
      <div className="mt-12 grid grid-cols-1 gap-8 md:grid-cols-3">
        {plans.map((plan) => (
          <div
            key={plan.name}
            className={`rounded-2xl border p-8 ${
              plan.highlight
                ? 'border-brand-600 shadow-lg ring-1 ring-brand-600'
                : 'border-gray-100'
            }`}
          >
            <h3 className="text-lg font-semibold text-gray-900">{plan.name}</h3>
            <div className="mt-4 flex items-baseline gap-2">
              <span className="text-4xl font-extrabold text-gray-900">
                {plan.price}
              </span>
              <span className="text-sm text-gray-500">{plan.period}</span>
            </div>
            <ul className="mt-6 space-y-3 text-sm text-gray-600">
              {plan.features.map((f) => (
                <li key={f} className="flex items-center gap-2">
                  <span className="text-brand-600">✓</span>
                  {f}
                </li>
              ))}
            </ul>
            <Link
              to="/download"
              className={`mt-8 block rounded-lg px-4 py-2.5 text-center text-sm font-semibold transition-colors ${
                plan.highlight
                  ? 'bg-brand-600 text-white hover:bg-brand-700'
                  : 'border border-gray-300 text-gray-700 hover:border-brand-600 hover:text-brand-600'
              }`}
            >
              开始使用
            </Link>
          </div>
        ))}
      </div>
    </section>
  )
}
