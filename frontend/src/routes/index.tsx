import { createFileRoute, Link } from '@tanstack/react-router'

/** 首页：Hero + 产品特性 + 下载/定价入口，对标 chatboxai.app。 */
export const Route = createFileRoute('/')({
  component: HomePage,
})

const features = [
  {
    icon: '💬',
    title: '多模型对话',
    desc: '一站式接入主流大语言模型，自由切换，畅快对话。',
  },
  {
    icon: '🖥️',
    title: '全平台覆盖',
    desc: '桌面端与移动端一致体验，数据随身，随时随地使用。',
  },
  {
    icon: '🔒',
    title: '本地优先',
    desc: '会话数据本地存储，隐私安全可控，掌握在自己手中。',
  },
  {
    icon: '⚡',
    title: '高效工具',
    desc: '内置提示词、文件问答、代码高亮，提升日常生产力。',
  },
]

function HomePage() {
  return (
    <>
      {/* Hero 区 */}
      <section className="relative overflow-hidden">
        <div className="mx-auto max-w-6xl px-6 py-24 text-center">
          <h1 className="text-5xl font-extrabold tracking-tight text-gray-900 sm:text-6xl">
            kod —— 你的
            <span className="text-brand-600"> AI 助手</span>
            客户端
          </h1>
          <p className="mx-auto mt-6 max-w-2xl text-lg text-gray-600">
            支持多种大模型，适配桌面与移动端，让 AI 触手可及。免费、开放、好用。
          </p>
          <div className="mt-10 flex items-center justify-center gap-4">
            <Link
              to="/download"
              className="rounded-lg bg-brand-600 px-6 py-3 text-base font-semibold text-white transition-colors hover:bg-brand-700"
            >
              免费下载
            </Link>
            <Link
              to="/features"
              className="rounded-lg border border-gray-300 px-6 py-3 text-base font-semibold text-gray-700 transition-colors hover:border-brand-600 hover:text-brand-600"
            >
              了解更多
            </Link>
          </div>
        </div>
      </section>

      {/* 产品特性区 */}
      <section className="bg-gray-50 py-20">
        <div className="mx-auto max-w-6xl px-6">
          <h2 className="text-center text-3xl font-bold text-gray-900">
            为什么选择 kod
          </h2>
          <div className="mt-12 grid grid-cols-1 gap-8 sm:grid-cols-2 lg:grid-cols-4">
            {features.map((f) => (
              <div
                key={f.title}
                className="rounded-2xl bg-white p-6 shadow-sm ring-1 ring-gray-100"
              >
                <div className="text-3xl">{f.icon}</div>
                <h3 className="mt-4 text-lg font-semibold text-gray-900">
                  {f.title}
                </h3>
                <p className="mt-2 text-sm text-gray-600">{f.desc}</p>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* CTA 区 */}
      <section className="py-20">
        <div className="mx-auto max-w-4xl px-6 text-center">
          <h2 className="text-3xl font-bold text-gray-900">立即开始使用 kod</h2>
          <p className="mt-4 text-gray-600">
            免费下载，几分钟即可开启你的 AI 助手之旅。
          </p>
          <Link
            to="/download"
            className="mt-8 inline-block rounded-lg bg-brand-600 px-6 py-3 text-base font-semibold text-white transition-colors hover:bg-brand-700"
          >
            前往下载
          </Link>
        </div>
      </section>
    </>
  )
}
