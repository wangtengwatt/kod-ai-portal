import { createFileRoute } from '@tanstack/react-router'

/** 常见问题页（/kod-ai-services-faqs）：FAQ 折叠列表。 */
export const Route = createFileRoute('/kod-ai-services-faqs')({
  component: FaqPage,
})

const faqs = [
  {
    q: 'kod 是什么？',
    a: 'kod 是一款基于开源项目 chatbox 二次研发的 AI 助手客户端，支持多种大模型，覆盖桌面与移动端。',
  },
  {
    q: 'kod 收费吗？',
    a: '提供永久免费版，满足基础对话需求；专业版与团队版提供更多高级能力，详见定价页。',
  },
  {
    q: '支持哪些平台？',
    a: '支持 Windows、macOS、Linux、Android、iOS，以及浏览器 Web 版。',
  },
  {
    q: '我的数据安全吗？',
    a: 'kod 采用本地优先策略，会话数据默认存储在本地设备，隐私安全由你掌控。',
  },
  {
    q: '如何反馈问题？',
    a: '可通过「意见反馈」页面提交，或发送邮件至 fane@vn.com。',
  },
]

function FaqPage() {
  return (
    <section className="mx-auto max-w-3xl px-6 py-20">
      <h1 className="text-4xl font-bold text-gray-900">常见问题</h1>
      <p className="mt-4 text-gray-600">关于 kod 使用的常见疑问解答。</p>

      <div className="mt-12 space-y-4">
        {faqs.map((f) => (
          <details
            key={f.q}
            className="group rounded-2xl border border-gray-100 p-5"
          >
            <summary className="cursor-pointer list-none text-base font-semibold text-gray-900">
              {f.q}
            </summary>
            <p className="mt-3 text-sm text-gray-600">{f.a}</p>
          </details>
        ))}
      </div>
    </section>
  )
}
