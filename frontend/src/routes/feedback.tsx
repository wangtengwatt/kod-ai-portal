import { createFileRoute } from '@tanstack/react-router'

/** 意见反馈页（/feedback）：展示反馈字段和当前可用的邮件渠道。 */
export const Route = createFileRoute('/feedback')({
  component: FeedbackPage,
})

const fieldClassName =
  'mt-2 w-full rounded-lg border border-gray-300 px-3 py-2 text-sm text-gray-900 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-600'

function FeedbackPage() {
  return (
    <section className="mx-auto max-w-3xl px-6 py-16 sm:py-20">
      <div className="max-w-2xl">
        <p className="text-sm font-semibold uppercase tracking-[0.2em] text-brand-600">
          Support
        </p>
        <h1 className="mt-4 text-4xl font-bold tracking-tight text-gray-900 sm:text-5xl">
          意见反馈
        </h1>
        <p className="mt-5 text-lg leading-8 text-gray-600">
          遇到问题或有产品建议？在线提交功能正在准备中，现在可以通过邮件与我们联系。
        </p>
      </div>

      <div className="mt-10 rounded-2xl bg-brand-50 p-6">
        <h2 className="font-semibold text-gray-900">当前可用：邮件反馈</h2>
        <p className="mt-2 text-sm leading-6 text-gray-600">
          请发送邮件至{' '}
          <a
            href="mailto:fane@vn.com"
            className="font-semibold text-brand-600 underline-offset-4 hover:underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-600"
          >
            fane@vn.com
          </a>
          。建议同时说明操作系统、客户端版本、问题现象和复现步骤，请勿在邮件中发送密码或 API Key。
        </p>
      </div>

      <form className="mt-10 space-y-6" aria-describedby="feedback-form-status">
        <div>
          <label htmlFor="feedback-category" className="block text-sm font-medium text-gray-700">
            反馈类型
          </label>
          <select id="feedback-category" name="category" className={fieldClassName} defaultValue="bug">
            <option value="bug">问题反馈（Bug）</option>
            <option value="feature">功能建议</option>
            <option value="experience">体验优化</option>
            <option value="other">其他</option>
          </select>
        </div>
        <div>
          <label htmlFor="feedback-content" className="block text-sm font-medium text-gray-700">
            详细描述
          </label>
          <textarea
            id="feedback-content"
            name="content"
            rows={6}
            maxLength={2000}
            className={fieldClassName}
            placeholder="请描述你遇到的问题、复现步骤或产品建议…"
          />
          <p className="mt-2 text-xs leading-5 text-gray-500">最多 2000 字；当前填写内容不会保存或发送。</p>
        </div>
        <div>
          <label htmlFor="feedback-email" className="block text-sm font-medium text-gray-700">
            联系邮箱（选填）
          </label>
          <input
            id="feedback-email"
            name="email"
            type="email"
            maxLength={254}
            autoComplete="email"
            className={fieldClassName}
            placeholder="you@example.com"
          />
        </div>
        <div>
          <button
            type="button"
            disabled
            className="w-full cursor-not-allowed rounded-lg bg-gray-100 px-6 py-2.5 text-sm font-semibold text-gray-400 sm:w-auto"
          >
            在线提交暂未开放
          </button>
          <p id="feedback-form-status" className="mt-3 text-sm leading-6 text-gray-500">
            表单用于预览未来的在线反馈内容，目前不会保存或提交数据。如需立即反馈，请使用上方邮件渠道。
          </p>
        </div>
      </form>
    </section>
  )
}
