import { createFileRoute, Link } from '@tanstack/react-router'
import { ArrowRight, Cpu, Globe, Monitor, Shield, Smartphone, Zap } from 'lucide-react'

export const Route = createFileRoute('/')({
  component: HomePage,
})

const features = [
  {
    icon: Cpu,
    title: '多模型对话',
    desc: '一站式接入 OpenAI、Claude、Gemini、DeepSeek 等主流大语言模型，自由切换，畅快对话。',
  },
  {
    icon: Monitor,
    title: '全平台覆盖',
    desc: '桌面端（Windows / macOS / Linux）、Web 端与移动端一致体验，数据随身，随时随地使用。',
  },
  {
    icon: Shield,
    title: '本地优先',
    desc: '会话数据本地存储，隐私安全可控，掌握在自己手中。',
  },
  {
    icon: Zap,
    title: '高效工具',
    desc: '内置提示词库、文件问答、代码高亮、Markdown 渲染，提升日常生产力。',
  },
  {
    icon: Globe,
    title: 'Web 版即开即用',
    desc: '无需下载安装，浏览器打开即可使用，跨设备无缝衔接。',
  },
  {
    icon: Smartphone,
    title: '移动端支持',
    desc: 'iOS / Android 移动端应用，随时随地与 AI 对话。',
  },
]

const highlights = [
  { value: '10+', label: '模型提供商' },
  { value: '4', label: '平台覆盖' },
  { value: '9', label: '语言支持' },
  { value: '100%', label: '本地存储' },
]

function HomePage() {
  return (
    <>
      {/* Hero */}
      <section className="relative overflow-hidden bg-gradient-to-b from-white via-brand-50/30 to-white">
        <div className="mx-auto max-w-6xl px-6 pb-20 pt-24 text-center sm:pt-32 sm:pb-28">
          <span className="inline-flex items-center rounded-full border border-brand-200 bg-brand-50 px-4 py-1.5 text-sm font-medium text-brand-700">
            多模型、全平台的 AI 助手客户端
          </span>
          <h1 className="mt-6 text-5xl font-extrabold tracking-tight text-gray-900 sm:text-6xl lg:text-7xl">
            用 <span className="text-brand-600">KOD</span>，
            <br className="sm:hidden" />
            开启智能工作方式
          </h1>
          <p className="mx-auto mt-6 max-w-2xl text-lg leading-relaxed text-gray-600">
            支持多种大模型，适配桌面与移动端，让 AI 触手可及。免费、开放、好用。
          </p>
          <div className="mt-10 flex items-center justify-center gap-4">
            <Link
              to="/download"
              className="inline-flex items-center gap-2 rounded-lg bg-brand-600 px-6 py-3 text-base font-semibold text-white shadow-sm transition-colors hover:bg-brand-700"
            >
              免费下载
              <ArrowRight className="h-4 w-4" />
            </Link>
            <Link
              to="/features"
              className="rounded-lg border border-gray-300 bg-white px-6 py-3 text-base font-semibold text-gray-700 transition-colors hover:border-brand-600 hover:text-brand-600"
            >
              了解更多
            </Link>
          </div>
        </div>
      </section>

      {/* 数据亮点 */}
      <section className="border-y border-gray-100 bg-white">
        <div className="mx-auto max-w-6xl px-6 py-10">
          <div className="grid grid-cols-2 gap-8 sm:grid-cols-4">
            {highlights.map((h) => (
              <div key={h.label} className="text-center">
                <div className="text-3xl font-extrabold text-brand-600">{h.value}</div>
                <div className="mt-1 text-sm text-gray-500">{h.label}</div>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* 产品特性 */}
      <section className="bg-gray-50 py-20 sm:py-28">
        <div className="mx-auto max-w-6xl px-6">
          <div className="text-center">
            <h2 className="text-3xl font-bold text-gray-900 sm:text-4xl">
              为什么选择 KOD
            </h2>
            <p className="mt-4 text-gray-600">
              不只是聊天工具，更是你的全能 AI 工作站
            </p>
          </div>
          <div className="mt-14 grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-3">
            {features.map((f) => {
              const Icon = f.icon
              return (
                <div
                  key={f.title}
                  className="group rounded-2xl bg-white p-6 shadow-sm ring-1 ring-gray-100 transition-shadow hover:shadow-md"
                >
                  <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-brand-50 text-brand-600">
                    <Icon className="h-5 w-5" />
                  </div>
                  <h3 className="mt-4 text-lg font-semibold text-gray-900">{f.title}</h3>
                  <p className="mt-2 text-sm leading-relaxed text-gray-600">{f.desc}</p>
                </div>
              )
            })}
          </div>
        </div>
      </section>

      {/* CTA */}
      <section className="py-20 sm:py-28">
        <div className="mx-auto max-w-4xl px-6 text-center">
          <h2 className="text-3xl font-bold text-gray-900 sm:text-4xl">
            立即开始使用 KOD
          </h2>
          <p className="mt-4 text-gray-600">
            免费下载，几分钟即可开启你的 AI 助手之旅。
          </p>
          <div className="mt-8 flex items-center justify-center gap-4">
            <Link
              to="/download"
              className="inline-flex items-center gap-2 rounded-lg bg-brand-600 px-6 py-3 text-base font-semibold text-white shadow-sm transition-colors hover:bg-brand-700"
            >
              前往下载
              <ArrowRight className="h-4 w-4" />
            </Link>
          </div>
        </div>
      </section>
    </>
  )
}
