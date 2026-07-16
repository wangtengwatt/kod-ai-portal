import { createFileRoute } from '@tanstack/react-router'

/** 问卷调查页（/jsjsubmit）：收集用户对 kod 的使用反馈。 */
export const Route = createFileRoute('/jsjsubmit')({
  component: SurveyPage,
})

function SurveyPage() {
  return (
    <section className="mx-auto max-w-2xl px-6 py-20">
      <h1 className="text-4xl font-bold text-gray-900">问卷调查</h1>
      <p className="mt-4 text-gray-600">
        感谢参与 kod 用户调研，你的反馈将帮助我们做得更好。
      </p>

      <form
        className="mt-10 space-y-6"
        onSubmit={(e) => {
          e.preventDefault()
          // TODO: 对接后端问卷提交接口 POST /api/survey
        }}
      >
        <div>
          <label className="block text-sm font-medium text-gray-700">
            你如何评价 kod？
          </label>
          <select className="mt-2 w-full rounded-lg border border-gray-300 px-3 py-2 text-sm">
            <option>非常满意</option>
            <option>满意</option>
            <option>一般</option>
            <option>不满意</option>
          </select>
        </div>
        <div>
          <label className="block text-sm font-medium text-gray-700">
            你最希望 kod 增加的功能
          </label>
          <textarea
            rows={4}
            className="mt-2 w-full rounded-lg border border-gray-300 px-3 py-2 text-sm"
            placeholder="请输入你的建议…"
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
          提交问卷
        </button>
      </form>
    </section>
  )
}
