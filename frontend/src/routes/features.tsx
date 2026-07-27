import { createFileRoute, Link } from '@tanstack/react-router'
import {
  BarChart3,
  Clock,
  Cpu,
  FileText,
  Globe,
  Monitor,
  RefreshCw,
  Scale,
  Search,
  Shield,
  TrendingUp,
  Users,
  Zap,
  type LucideIcon,
} from 'lucide-react'

export const Route = createFileRoute('/features')({
  component: FeaturesPage,
})

interface Feature {
  icon: LucideIcon
  title: string
  desc: string
}

const groups: { name: string; items: Feature[] }[] = [
  {
    name: 'KAI 期算交易能力',
    items: [
      {
        icon: FileText,
        title: '标准合约',
        desc: '基于 KAI 期算品种规则的统一合约模板：固定模型版本、上下文上限 256K、一手 1,000,000 TPM、指定交割小时与区域。',
      },
      {
        icon: Clock,
        title: '分时交付',
        desc: '合约精确到小时，供应商在约定时段提供符合标准的模型服务。KOD 监控并记录交付质量，支持违约追偿。',
      },
      {
        icon: TrendingUp,
        title: '跨供应商竞价',
        desc: '同一品种合约可面向多家合格供应商询价，KOD 聚合报价并展示历史成交价，辅助企业获取最优条件。',
      },
      {
        icon: RefreshCw,
        title: '二级转让',
        desc: '关闸前可出售多余容量或补购缺口。KOD 内置转让记录与持仓管理，让合约从固定承诺变为灵活资产。',
      },
      {
        icon: BarChart3,
        title: '行情与价格发现',
        desc: '远期价格曲线、历史成交数据、供应商评分——为采购决策提供数据支撑，帮助识别价格异常和套利机会。',
      },
      {
        icon: Scale,
        title: '交割与风控',
        desc: '自动记录交付比例，触发替代采购流程，计算供应商违约补偿。KAI 平台中立治理，争议有据可查。',
      },
    ],
  },
  {
    name: 'AI 模型支持',
    items: [
      {
        icon: Cpu,
        title: '多模型接入',
        desc: '接入 OpenAI、Claude、Gemini、DeepSeek、GLM、Mistral 等主流大模型。在 KAI 期算市场中，模型以品种形式标准化登记，版本与上下文透明可查。',
      },
      {
        icon: Zap,
        title: '增强提示词',
        desc: '高级提示词功能帮助精炼提问，获得更精准回复。企业可沉淀业务专用提示词库，配合合约模型实现稳定产出。',
      },
    ],
  },
  {
    name: 'KOD 智能工作台',
    items: [
      {
        icon: Shield,
        title: '本地数据存储',
        desc: '会话数据本地存储，隐私安全可控。企业级数据隔离，确保容量交易数据与对话数据分离。',
      },
      {
        icon: Monitor,
        title: '全平台一致体验',
        desc: '桌面端（Windows / macOS / Linux）、Web 端与移动端（iOS / Android）统一界面，蒜粒持仓与交易记录跨设备同步。',
      },
      {
        icon: FileText,
        title: 'Markdown、LaTeX 与代码高亮',
        desc: '消息支持 Markdown 与 LaTeX 排版，对多种编程语言提供语法高亮。合约文档与交割报告以结构化格式展示。',
      },
    ],
  },
  {
    name: '工具与集成',
    items: [
      {
        icon: Search,
        title: '网页搜索与读取',
        desc: 'AI 可调用网页搜索获取最新信息或读取 URL 正文。用于市场调研、供应商背景核查与行业动态追踪。',
      },
      {
        icon: Cpu,
        title: 'MCP 工具扩展',
        desc: '支持 Model Context Protocol 开放协议，可接入企业内部 API。未来将开放 KAI 期算交易与持仓查询接口。',
      },
    ],
  },
  {
    name: '协作与多语言',
    items: [
      {
        icon: Users,
        title: '团队协作',
        desc: '团队间共享 AI API 资源，统一管理用量。采购团队可协同管理 KAI 期算持仓与审批流程。',
      },
      {
        icon: Globe,
        title: '多语言支持',
        desc: '面向全球用户，支持 9 种语言：English、简体中文、繁體中文、日本語、한국어、Français、Deutsch、Русский、Español。KAI 期算合约支持多区域交割。',
      },
    ],
  },
]

function FeaturesPage() {
  return (
    <section className="bg-white">
      <div className="mx-auto max-w-6xl px-6 py-20 sm:py-28">
        <div className="text-center">
          <h1 className="text-4xl font-bold text-gray-900 sm:text-5xl">KOD 功能体系</h1>
          <p className="mt-4 text-gray-600">
            以 KAI 期算市场能力为核心，整合 AI 对话、工具链与跨平台体验，打造完整的蒜粒期货 AI 工作台。
          </p>
        </div>

        <div className="mt-14 space-y-16">
          {groups.map((group) => (
            <div key={group.name}>
              <h2 className="mb-6 text-base font-semibold uppercase tracking-wider text-gray-400">
                {group.name}
              </h2>
              <div className="grid grid-cols-1 gap-5 sm:grid-cols-2 lg:grid-cols-3">
                {group.items.map((item) => {
                  const Icon = item.icon
                  return (
                    <div
                      key={item.title}
                      className="group rounded-2xl border border-gray-100 bg-white p-6 shadow-sm transition-shadow hover:shadow-md"
                    >
                      <div className="flex h-12 w-12 items-center justify-center rounded-xl bg-brand-50 text-brand-600 transition-colors group-hover:bg-brand-100">
                        <Icon className="h-6 w-6" />
                      </div>
                      <h3 className="mt-4 text-lg font-semibold text-gray-900">{item.title}</h3>
                      <p className="mt-2 text-sm leading-relaxed text-gray-600">{item.desc}</p>
                    </div>
                  )
                })}
              </div>
            </div>
          ))}
        </div>

        <div className="mt-20 text-center">
          <Link
            to="/download"
            className="inline-flex items-center gap-2 rounded-lg bg-brand-600 px-6 py-3 text-base font-semibold text-white shadow-sm transition-colors hover:bg-brand-700"
          >
            下载 KOD，接入 KAI 期算
          </Link>
        </div>
      </div>
    </section>
  )
}
