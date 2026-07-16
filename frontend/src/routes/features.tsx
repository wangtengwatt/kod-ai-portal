import { createFileRoute } from '@tanstack/react-router'

/** 产品特性页。 */
export const Route = createFileRoute('/features')({
  component: FeaturesPage,
})

const items = [
  {
    title: '多模型对话',
    desc: '接入主流大语言模型，支持自定义 API，一处配置多处使用。',
  },
  {
    title: '文件与图片问答',
    desc: '上传文档、图片，让 AI 帮你阅读、总结与分析。',
  },
  {
    title: '提示词管理',
    desc: '内置与自定义提示词模板，沉淀你的高效工作流。',
  },
  {
    title: '代码与 Markdown',
    desc: '代码高亮、Markdown 渲染、公式支持，开发者友好。',
  },
  {
    title: '多端同步',
    desc: '桌面端与移动端体验一致，数据随身携带。',
  },
  {
    title: '本地优先与隐私',
    desc: '会话数据本地存储，隐私安全由你掌控。',
  },
]

function FeaturesPage() {
  return (
    <section className="mx-auto max-w-6xl px-6 py-20">
      <h1 className="text-4xl font-bold text-gray-900">产品特性</h1>
      <p className="mt-4 text-gray-600">kod 为你提供全面而强大的 AI 助手能力。</p>
      <div className="mt-12 grid grid-cols-1 gap-8 md:grid-cols-2 lg:grid-cols-3">
        {items.map((item) => (
          <div
            key={item.title}
            className="rounded-2xl border border-gray-100 p-6"
          >
            <h3 className="text-lg font-semibold text-gray-900">{item.title}</h3>
            <p className="mt-2 text-sm text-gray-600">{item.desc}</p>
          </div>
        ))}
      </div>
    </section>
  )
}
