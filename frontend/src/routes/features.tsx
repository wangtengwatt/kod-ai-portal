import { createFileRoute, Link } from '@tanstack/react-router'
import {
  Cpu,
  FileText,
  Globe,
  Languages,
  Library,
  Monitor,
  Palette,
  Search,
  Shield,
  Smartphone,
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
    name: 'AI 模型支持',
    items: [
      {
        icon: Cpu,
        title: '多模型接入',
        desc: '支持 OpenAI (ChatGPT)、Azure OpenAI、Claude、Google Gemini、DeepSeek、Mistral、Amazon Bedrock、Perplexity 等主流 LLM 提供商，一处配置，自由切换。',
      },
      {
        icon: Zap,
        title: '增强提示词',
        desc: '提供高级提示词功能，可精炼和聚焦你的提问，获得更精准的回复。',
      },
    ],
  },
  {
    name: '用户体验',
    items: [
      {
        icon: Shield,
        title: '本地数据存储',
        desc: '数据保留在你的设备上，确保不会丢失，同时保障你的隐私安全。桌面端使用文件存储 + IndexedDB，移动端使用 SQLite。',
      },
      {
        icon: Monitor,
        title: '开箱即用安装包',
        desc: '提供各平台安装包，无需复杂配置即可快速开始使用。支持 Windows、macOS、Linux。',
      },
      {
        icon: Palette,
        title: '深色主题与舒适 UI',
        desc: '用户友好的界面设计，支持深色模式，减少长时间使用时的视觉疲劳。',
      },
      {
        icon: Zap,
        title: '键盘快捷键',
        desc: '丰富的快捷键支持，让你的工作流保持高效。',
      },
    ],
  },
  {
    name: '内容与格式',
    items: [
      {
        icon: FileText,
        title: 'Markdown、LaTeX 与代码高亮',
        desc: '消息支持 Markdown 与 LaTeX 排版，并对多种编程语言提供语法高亮，提升可读性与展示效果。',
      },
      {
        icon: Library,
        title: '提示词库与消息引用',
        desc: '保存并组织常用提示词以供复用；支持引用对话中的消息，提供上下文背景。',
      },
    ],
  },
  {
    name: '工具与集成',
    items: [
      {
        icon: Search,
        title: '网页搜索与读取',
        desc: 'AI 可在对话中调用网页搜索工具获取最新信息，或读取指定 URL 的可读正文进行深入理解。支持多种搜索引擎。',
      },
      {
        icon: Cpu,
        title: 'MCP 工具扩展',
        desc: '支持 Model Context Protocol 开放协议，通过标准化接口调用外部工具。内置 Fetch、Sequential Thinking、arXiv 论文检索等预设服务，也支持自定义 Stdio/HTTP 连接。',
      },
      {
        icon: FileText,
        title: '文件读取与分析',
        desc: '上传文档后 AI 可直接读取分析。支持 PDF、Word、Excel、Epub 等多种格式。大文件先展示摘要，按需通过工具读取完整内容。',
      },
    ],
  },
  {
    name: '协作与分享',
    items: [
      {
        icon: Users,
        title: '团队协作',
        desc: '轻松协作，在团队间共享 AI API 资源，统一管理用量。',
      },
    ],
  },
  {
    name: '平台与本地化',
    items: [
      {
        icon: Globe,
        title: '跨平台覆盖',
        desc: '覆盖桌面端（Windows / macOS / Linux）、Web 版以及移动端（iOS / Android），同一份代码库，全平台一致体验。',
      },
      {
        icon: Languages,
        title: '多语言支持',
        desc: '面向全球用户，支持 9 种语言：English、简体中文、繁體中文、日本語、한국어、Français、Deutsch、Русский、Español。',
      },
      {
        icon: Smartphone,
        title: '移动端适配',
        desc: 'iOS 与 Android 原生体验，基于 Capacitor 构建，触控优化，随时随地使用。',
      },
    ],
  },
]

function FeaturesPage() {
  return (
    <section className="bg-gray-50">
      <div className="mx-auto max-w-6xl px-6 py-20 sm:py-28">
        <div className="text-center">
          <h1 className="text-4xl font-bold text-gray-900 sm:text-5xl">产品特性</h1>
          <p className="mt-4 text-gray-600">
            整合前沿 AI 能力与精致用户体验，为你的工作流赋能。
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
            免费下载 KOD
          </Link>
        </div>
      </div>
    </section>
  )
}
