import { Link, createFileRoute } from '@tanstack/react-router'

/** 常见问题页（/kod-ai-services-faqs）：KOD 服务使用说明。 */
export const Route = createFileRoute('/kod-ai-services-faqs')({
  component: FaqPage,
})

type FaqItem = {
  question: string
  answer: string
  status?: 'available' | 'in-development'
}

const faqGroups: { title: string; items: FaqItem[] }[] = [
  {
    title: '注册与登录',
    items: [
      {
        question: '如何注册 KOD 账号？',
        answer:
          '打开 KOD 客户端，在登录页面填写邮箱、密码和有效邀请码。首次登录会同时创建账号，之后可以直接使用邮箱和密码登录。',
      },
      {
        question: '如何获取邀请码？',
        answer:
          '邀请码由 KOD 官方或已接入的零售站提供。请通过官方通知、合作零售站或意见反馈页面公布的支持渠道获取，不要购买来源不明的邀请码。',
      },
    ],
  },
  {
    title: '模型与使用方式',
    items: [
      {
        question: 'KOD 支持哪些模型？',
        answer:
          '可用模型由当前零售站和账号权限决定，客户端会自动读取实时模型列表。团队正在扩展 GPT、Claude、Gemini、DeepSeek、GLM 等模型，最终请以客户端模型选择器显示的内容为准。',
      },
      {
        question: 'KOD AI 与 BYOK 有什么区别？',
        answer:
          'KOD AI 使用账号关联的零售站服务，由系统统一提供模型、额度和故障切换能力；BYOK（Bring Your Own Key，自带密钥）则由你配置自己在模型供应商处获得的 API Key，费用和可用性由对应供应商负责。两种方式相互独立。',
      },
      {
        question: '为什么不同用户看到的模型可能不同？',
        answer:
          '模型列表会根据零售站渠道、账号权限、API Token 限制和模型当前可用状态动态生成。因此，不同零售站或不同账号看到的模型可能不完全相同。',
      },
    ],
  },
  {
    title: '钱包与 API Token',
    items: [
      {
        question: '如何充值？',
        answer:
          '钱包与充值功能正在开发中。上线后，你可以在 Portal 用户中心查看余额、发起充值并查询充值和消费记录；具体支付方式和规则以正式页面为准。',
        status: 'in-development',
      },
      {
        question: '如何创建 API Token？',
        answer:
          'API Token 管理功能正在开发中。上线后，你可以在 Portal 用户中心创建、复制、停用或删除 Token。完整 Token 只会在创建时展示一次，请妥善保存，不要发送给他人。',
        status: 'in-development',
      },
    ],
  },
  {
    title: '平台与数据',
    items: [
      {
        question: 'KOD 支持哪些平台？',
        answer:
          '下载页保留 Windows、macOS、Linux、Android、iOS 和 Web 的发行入口。目前尚未发布的平台会显示“即将上线”，正式可用范围以下载页提供的安装包和版本说明为准。',
      },
      {
        question: '我的会话数据保存在哪里？',
        answer:
          'KOD 客户端采用本地优先设计，会话数据默认保存在你的设备中。模型请求仍需发送到你选择的模型服务或零售站处理，因此不要在对话中提交密码、密钥等敏感信息。',
      },
    ],
  },
]

function FaqPage() {
  return (
    <section className="mx-auto max-w-4xl px-6 py-16 sm:py-20">
      <div className="max-w-2xl">
        <p className="text-sm font-semibold uppercase tracking-[0.2em] text-brand-600">
          帮助中心
        </p>
        <h1 className="mt-4 text-4xl font-bold tracking-tight text-gray-900 sm:text-5xl">
          KOD 服务常见问题
        </h1>
        <p className="mt-5 text-lg leading-8 text-gray-600">
          了解账号注册、模型使用、BYOK、钱包和 API Token。尚在开发的功能会明确标注，不会影响现有客户端使用。
        </p>
      </div>

      <div className="mt-12 space-y-10">
        {faqGroups.map((group) => (
          <section key={group.title} aria-labelledby={`faq-${group.title}`}>
            <h2 id={`faq-${group.title}`} className="text-xl font-semibold text-gray-900">
              {group.title}
            </h2>
            <div className="mt-4 space-y-3">
              {group.items.map((item) => (
                <details
                  key={item.question}
                  className="group rounded-2xl border border-gray-200 bg-white p-5 open:shadow-sm"
                >
                  <summary className="flex cursor-pointer list-none items-center justify-between gap-4 font-semibold text-gray-900 focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-brand-600">
                    <span>{item.question}</span>
                    <span className="flex shrink-0 items-center gap-2">
                      {item.status === 'in-development' ? (
                        <span className="rounded-full bg-gray-100 px-2.5 py-1 text-xs font-medium text-gray-500">
                          开发中
                        </span>
                      ) : null}
                      <span aria-hidden="true" className="text-brand-600 transition-transform group-open:rotate-45">
                        +
                      </span>
                    </span>
                  </summary>
                  <p className="mt-4 max-w-3xl text-sm leading-7 text-gray-600">{item.answer}</p>
                </details>
              ))}
            </div>
          </section>
        ))}
      </div>

      <div className="mt-14 rounded-2xl bg-brand-50 p-6 sm:flex sm:items-center sm:justify-between sm:gap-8">
        <div>
          <h2 className="font-semibold text-gray-900">没有找到需要的答案？</h2>
          <p className="mt-2 text-sm leading-6 text-gray-600">
            前往意见反馈页面查看当前可用的支持渠道。
          </p>
        </div>
        <Link
          to="/feedback"
          className="mt-5 inline-flex rounded-lg bg-brand-600 px-4 py-2.5 text-sm font-semibold text-white transition-colors hover:bg-brand-700 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-600 sm:mt-0"
        >
          意见反馈
        </Link>
      </div>
    </section>
  )
}
