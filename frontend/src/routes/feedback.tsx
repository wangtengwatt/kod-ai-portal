import { createFileRoute } from '@tanstack/react-router'

/** 意见反馈页（/feedback）：收集问题与建议。 */
export const Route = createFileRoute('/feedback')({
  component: FeedbackPage,
})

function FeedbackPage() {
  return (
    <section className="mx-auto max-w-2xl px-6 py-20">
      <h1 className="text-4xl font-bold text-gray-900">意见反馈</h1>
      <p className="mt-4 text-gray-600">
        遇到问题或有好的建议？欢迎告诉我们，也可发送邮件至{' '}
        <a
          href="mailto:fane@vn.com"
          className="font-medium text-brand-600 hover:underline"
        >
          fane@vn.com
        </a>
        。
      </p>

      <form
        className="mt-10 space-y-6"
        onSubmit={(e) => {
          e.preventDefault()
          // TODO: 对接后端反馈接口 POST /api/feedback
        }}
      >
        <div>
          <label className="block text-sm font-medium text-gray-700">
            反馈类型
          </label>
          <select className="mt-2 w-full rounded-lg border border-gray-300 px-3 py-2 text-sm">
            <option>问题反馈（Bug）</option>
            <option>功能建议</option>
            <option>其他</option>
          </select>
        </div>
        <div>
          <label className="block text-sm font-medium text-gray-700">
            详细描述
          </label>
          <textarea
            rows={5}
            className="mt-2 w-full rounded-lg border border-gray-300 px-3 py-2 text-sm"
            placeholder="请描述你遇到的问题或建议…"
          />
        </div>
        <div>
          <label className="block text-sm font-medium text-gray-700">
            联系邮箱（选填）
          </label>
          <input
            type="email"
            className="mt-2 w-full rounded-lg border border-gray-300 px-3 py-2 text-sm"
            placeholder="you@example.com"
          />
        </div>
        <button
          type="submit"
          className="rounded-lg bg-brand-600 px-6 py-2.5 text-sm font-semibold text-white transition-colors hover:bg-brand-700"
        >
          提交反馈
        </button>
      </form>
    </section>
  )
}
