import { createFileRoute, Link } from '@tanstack/react-router'

/** 常见问题页（/kod-ai-services-faqs）：FAQ 折叠列表。 */
export const Route = createFileRoute('/kod-ai-services-faqs')({
  component: FaqPage,
})

const faqs = [
  {
    q: 'KOD蒜粒 是什么？',
    a: 'KOD蒜粒 是一款面向蒜粒算力存取市场的专业 AI 助手客户端。它不仅支持多模型对话与全平台使用，更深度集成 KAI 期算标准——帮助企业将模型服务容量从不确定的弹性调用升级为可计划、可交易、可保障的资产。',
  },
  {
    q: 'KAI 期算是什么？',
    a: (
      <>
        KAI 期算是将 AI 模型服务容量进行标准化、合约化、市场化的开放标准。它将未来指定小时内某个确定模型的使用权，变成以 TPM·h 为单位的标准产品，可在合格供应商间竞价采购，并支持二级转让。详见{' '}
        <Link to="/kai" className="text-brand-600 underline hover:text-brand-700">
          KAI 期算白皮书
        </Link>
        。
      </>
    ),
  },
  {
    q: 'KOD蒜粒 和 KAI 期算是什么关系？',
    a: 'KOD蒜粒 是承载 KAI 期算标准的前端客户端与工作平台。KAI 期算定义了市场的规则、合约品种和交割标准；KOD蒜粒 提供用户使用这些市场能力的界面——包括查看行情、管理持仓、参与竞价、监控交割状态，同时保留完整的 AI 对话与工具功能。简言之：KAI 制定标准，KOD蒜粒 交付体验。',
  },
  {
    q: 'KOD蒜粒 收费吗？',
    a: '提供永久免费版，满足基础对话需求；专业版与团队版提供更多高级能力，详见定价页。KAI 期算市场交易费用独立计算，包括合约挂牌、交易服务、交割结算与二级转让等费用（以平台正式规则为准）。',
  },
  {
    q: '谁适合使用 KOD蒜粒 + KAI 期算？',
    a: '核心用户是需要在关键时段获得确定模型容量的企业——电商大促期间的智能客服、金融机构的批量报告生成、内容平台的定时内容审核等。对模型容量有明确时间要求的企业，KAI 期算将容量从"尽力而为"升级为合同义务。同时，KOD蒜粒 也适合希望用 AI 提升日常生产力的个人用户。',
  },
  {
    q: '如何开始使用 KAI 期算？',
    a: '目前 KAI 期算处于白皮书与联合设计阶段（阶段 0），欢迎企业用户、模型供应商和行业合作伙伴通过意见反馈页面或邮件联系团队。KOD蒜粒 客户端已于 2026 年 7 月上线，下载即可体验 AI 助手核心功能，后续版本将逐步开放市场交易能力。',
  },
  {
    q: '支持哪些平台？',
    a: '支持 Windows、macOS、Linux、Android、iOS，以及浏览器 Web 版。',
  },
  {
    q: '我的数据安全吗？',
    a: 'KOD蒜粒 采用本地优先策略，会话数据默认存储在本地设备，隐私安全由你掌控。容量交易数据通过后端服务管理，与本地对话数据分离，确保企业信息安全。',
  },
  {
    q: '如何反馈问题？',
    a: '可通过「意见反馈」页面提交，或发送邮件至 fane@vn.com。如需咨询 KAI 期算合作事宜，欢迎联系 watt@vn.com。',
  },
]

function FaqPage() {
  return (
    <section className="mx-auto max-w-3xl px-6 py-20">
      <h1 className="text-4xl font-bold text-gray-900">常见问题</h1>
      <p className="mt-4 text-gray-600">关于 KOD蒜粒 与 KAI 期算的常见疑问解答。</p>

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
