import { createFileRoute } from '@tanstack/react-router'

/** 问卷调查页（/jsjsubmit）：预览 KOD 用户调研内容和开放状态。 */
export const Route = createFileRoute('/jsjsubmit')({
  component: SurveyPage,
})

const fieldClassName =
  'mt-2 w-full rounded-lg border border-gray-300 px-3 py-2 text-sm text-gray-900 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-600'

function SurveyPage() {
  return (
    <section className="mx-auto max-w-3xl px-6 py-16 sm:py-20">
      <div className="max-w-2xl">
        <div className="flex flex-wrap items-center gap-3">
          <p className="text-sm font-semibold uppercase tracking-[0.2em] text-brand-600">
            User research
          </p>
          <span className="rounded-full bg-gray-100 px-3 py-1 text-xs font-semibold text-gray-500">
            即将开放
          </span>
        </div>
        <h1 className="mt-4 text-4xl font-bold tracking-tight text-gray-900 sm:text-5xl">
          KOD 用户问卷
        </h1>
        <p className="mt-5 text-lg leading-8 text-gray-600">
          我们希望了解你对 KOD 的使用体验和未来功能期待。问卷预计需要 2–3 分钟，在线提交功能开放后可在本页面完成。
        </p>
      </div>

      <div className="mt-10 rounded-2xl border border-gray-200 bg-gray-50 p-6">
        <h2 className="font-semibold text-gray-900">当前为问卷内容预览</h2>
        <p className="mt-2 text-sm leading-6 text-gray-600">
          你可以提前查看调研项目，但当前填写内容不会被保存或发送。正式开放时，我们会在页面上启用提交入口并补充数据用途说明。
        </p>
      </div>

      <form className="mt-10 space-y-6" aria-describedby="survey-form-status">
        <div>
          <label htmlFor="survey-satisfaction" className="block text-sm font-medium text-gray-700">
            你如何评价 KOD？
          </label>
          <select id="survey-satisfaction" name="satisfaction" className={fieldClassName} defaultValue="very-satisfied">
            <option value="very-satisfied">非常满意</option>
            <option value="satisfied">满意</option>
            <option value="neutral">一般</option>
            <option value="dissatisfied">不满意</option>
            <option value="not-used">尚未正式使用</option>
          </select>
        </div>
        <div>
          <label htmlFor="survey-feature-request" className="block text-sm font-medium text-gray-700">
            你最希望 KOD 增加或完善什么功能？
          </label>
          <textarea
            id="survey-feature-request"
            name="featureRequest"
            rows={5}
            maxLength={1500}
            className={fieldClassName}
            placeholder="例如模型支持、MCP、Skills、文件处理或零售站体验…"
          />
          <p className="mt-2 text-xs leading-5 text-gray-500">最多 1500 字。</p>
        </div>
        <div>
          <label htmlFor="survey-other-comments" className="block text-sm font-medium text-gray-700">
            其他意见（选填）
          </label>
          <textarea
            id="survey-other-comments"
            name="otherComments"
            rows={4}
            maxLength={1000}
            className={fieldClassName}
            placeholder="还有哪些体验、问题或建议希望告诉我们？"
          />
        </div>
        <div>
          <button
            type="button"
            disabled
            className="w-full cursor-not-allowed rounded-lg bg-gray-100 px-6 py-2.5 text-sm font-semibold text-gray-400 sm:w-auto"
          >
            在线问卷即将开放
          </button>
          <p id="survey-form-status" className="mt-3 text-sm leading-6 text-gray-500">
            当前不会保存或提交任何填写内容。请勿在问卷中填写密码、API Key 或其他敏感信息。
          </p>
        </div>
      </form>
    </section>
  )
}
