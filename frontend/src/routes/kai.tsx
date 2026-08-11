import { createFileRoute, Link } from '@tanstack/react-router'
import {
  ArrowRight,
  BarChart3,
  Box,
  Building2,
  Clock,
  FileText,
  Globe,
  Handshake,
  Layers,
  RefreshCw,
  Scale,
  Server,
  Shield,
  Timer,
  TrendingUp,
  Zap,
} from 'lucide-react'

export const Route = createFileRoute('/kai')({
  component: KaiPage,
})

/* ==================== 数据定义 ==================== */

const structuralFeatures = [
  {
    icon: Clock,
    title: '不可储存',
    desc: '本小时未使用的服务能力无法留到下小时出售，需要分时价格和转让机制。',
  },
  {
    icon: Timer,
    title: '时间敏感',
    desc: '同一模型在工作日高峰与夜间的价值不同，合约须明确日期和小时。',
  },
  {
    icon: Box,
    title: '模型特定',
    desc: '企业通常不能在关键业务中随意替换模型，标的应固定模型或严格定义替代等级。',
  },
  {
    icon: Shield,
    title: '质量相关',
    desc: '相同 Token 数不代表相同业务价值，标准须固定模型版本、上下文上限和交付责任。',
  },
]

const comparisonRows = [
  { method: '按 Token 付费', pros: '简单、弹性高', cons: '不保证特定高峰时段容量，预算随负载变化' },
  { method: '实例 / GPU 租赁', pros: '对底层资源控制较强', cons: '需自行运营模型，不同硬件难比较产出' },
  { method: '长期包量合同', pros: '折扣和稳定合作关系', cons: '灵活性低，难以针对某个小时转让或调整' },
  { method: '单云容量预订', pros: '提高特定资源可获得性', cons: '供应商封闭，缺少跨平台价格发现' },
  { method: 'KAI 期算 (TPM·h)', pros: '面向业务结果定义容量，多供应商、分时、标准履约', cons: '需建立标准、质量和流动性框架', highlight: true },
]

const buyerValues = [
  { icon: Shield, title: '容量保障', desc: '关键时段模型服务从弹性资源升级为明确交付义务，大促、直播、批处理场景中容量保障比 Token 单价更重要。' },
  { icon: FileText, title: '预算确定性', desc: '未来服务成本在采购时明确化，可纳入活动预算和客户报价，减少高峰临时扩容的价格不确定性。' },
  { icon: Globe, title: '跨供应商采购', desc: '统一品种降低逐家比较成本，供应商故障时 KAI 可组织替代交付，降低单一供应商依赖。' },
  { icon: RefreshCw, title: '采购灵活性', desc: '预测变化时可在关闸前出售多余容量或补购，相比长期不可转让合同大幅提升需求调整能力。' },
]

const supplierValues = [
  { icon: TrendingUp, title: '销售前置与收入确定性', desc: '提前出售未来时段锁定收入，覆盖固定成本，减少完全依赖即时流量的经营波动。' },
  { icon: BarChart3, title: '改善扩容决策', desc: '远期价格曲线反映不同时间、区域和模型的需求预期，供应商可有依据地扩容或调整。' },
  { icon: Globe, title: '扩大客户覆盖', desc: '无需逐一建立复杂企业销售关系，中小供应商达到统一标准即可与大型云平台竞争。' },
  { icon: Layers, title: '容量与收益管理', desc: '按时段分层定价和提前销售，区分基础负载、峰值负载和备用容量，提高整体收益管理能力。' },
]

const participants = [
  { icon: Building2, name: '企业最终用户', role: '锁定未来模型容量与成本，按合同付款、合理申报需求', benefit: '业务连续性、预算确定性' },
  { icon: Server, name: '容量供应商', role: '登记真实容量、按时交付、承担违约责任', benefit: '服务收入、利用率提升' },
  { icon: Box, name: '模型发布方', role: '提供清晰版本信息，管理品牌使用', benefit: '扩大生态覆盖和需求可见性' },
  { icon: Layers, name: '聚合商', role: '采购大额容量并拆分给中小企业客户', benefit: '批零差价、服务费' },
  { icon: Handshake, name: '经纪与采购服务商', role: '代理企业进入市场，分析需求、管理订单', benefit: '经纪与顾问收入' },
  { icon: Scale, name: 'KAI 平台', role: '建立标准、组织市场、治理交割，保持中立', benefit: '交易、交割与数据收入' },
]

const lifecycleSteps = [
  { step: 1, title: '模型准入', desc: '选择适合标准化的模型和版本，明确质量与生命周期规则' },
  { step: 2, title: '供应商资格', desc: '供应商证明稳定运营模型、持续交付和承担责任的能力' },
  { step: 3, title: '容量登记', desc: '供应商申报特定日期、小时和区域的可交付数量并担保' },
  { step: 4, title: '合约挂牌', desc: '发布统一品种参数：模型版本、上下文上限、一手 1,000,000 TPM 等' },
  { step: 5, title: '初始销售', desc: '供应商发布报价，买方按有效报价直接采购' },
  { step: 6, title: '二级转让', desc: '买方需求变化时，在关闸前出售或补购' },
  { step: 7, title: '交割准备', desc: '关闸锁定持仓，绑定可调用容量至交易账户及 API 凭证' },
  { step: 8, title: '小时交付', desc: '供应商在约定小时提供符合标准的模型服务' },
  { step: 9, title: '交付确认', desc: '确认交付比例、替代采购、违约补偿和供应商评分' },
]

const contractParams = [
  { param: '指定模型及版本', value: 'GLM-5.2（以 KAI 品种规则登记的版本标识为准）' },
  { param: '统一上下文上限', value: '单次请求总上下文不超过 256K' },
  { param: '输出权重 W', value: '3' },
  { param: '一手容量', value: '1,000,000 TPM' },
  { param: '指定交割小时', value: '2026年9月18日 20:00–21:00（北京时间）' },
  { param: '交割区域', value: '东亚一区' },
  { param: '超额处理', value: '超上限拒绝，合同另有约定可排队、降速或实时补购' },
]

const exampleBuyerCost = [
  { item: '买入价格', value: '780 元/手' },
  { item: '采购数量', value: '10 手' },
  { item: '最终服务价款', value: '7,800 元' },
]

const roadmap = [
  { phase: '阶段 0', time: '0–6 个月', title: '联合设计', tasks: ['与模型方、供应商和锚定买方确定标准、合同和试点时段', '签署合作意向', '形成首个品种和交割规则'] },
  { phase: '阶段 1', time: '6–12 个月', title: '期算预订', tasks: ['B2B 买方全额预付、供应商履约担保', '期算预订、分配与有限转让', '稳定完成真实小时交割', '建立供应商评分'] },
  { phase: '阶段 2', time: '12–24 个月', title: '日前与平衡市场', tasks: ['形成公开日前价格', '建立临近交割补购机制', '建立可信价格指数', '建立替代容量网络'] },
  { phase: '阶段 3', time: '24–36 个月', title: '标准期算与多区域', tasks: ['扩展模型、区域和多小时组合', '引入更完善的信用安排', '提高重复采购和流动性', '扩大跨供应商份额'] },
]

const flywheels = [
  {
    icon: TrendingUp,
    title: '流动性飞轮',
    desc: '更多买方 → 更多供应商 → 更窄价差 → 更高采购意愿',
  },
  {
    icon: Shield,
    title: '交付飞轮',
    desc: '更多交割数据 → 更准确风控 → 更低违约成本 → 更强信任',
  },
  {
    icon: Layers,
    title: '生态飞轮',
    desc: '标准品种 → 聚合、保险、指数和融资服务 → 更多使用场景',
  },
]

const barriers = [
  { title: '标准壁垒', desc: '参与者围绕 KAI 品种定义、合同语言和价格指数建立采购流程后，切换成本不仅来自软件，更来自内部管理体系。' },
  { title: '供需网络效应', desc: '更多合格供应商 → 更可靠交付和更有竞争力价格 → 更多买方 → 供应商更愿意提前登记容量。这是 KAI 最核心的网络效应。' },
  { title: '数据网络效应', desc: '交割积累形成不同模型、时段、区域下的价格和违约记录，可用于改进品种设计和风控。数据价值来自真实交付，而非仅来自报价。' },
  { title: '信用与治理壁垒', desc: '企业信任取决于平台能否在故障时组织替代、在争议时独立判断。长期稳定的治理记录比短期补贴更难复制。' },
]

/* ==================== 子组件 ==================== */

function Section({ id, children, className = '' }: { id?: string; children: React.ReactNode; className?: string }) {
  return (
    <section id={id} className={className}>
      <div className="mx-auto max-w-6xl px-6 py-20 sm:py-28">{children}</div>
    </section>
  )
}

function SectionTitle({ overline, title, subtitle }: { overline?: string; title: string; subtitle?: string }) {
  return (
    <div className="text-center">
      {overline && (
        <span className="text-sm font-semibold uppercase tracking-wider text-brand-600">{overline}</span>
      )}
      <h2 className="mt-2 text-3xl font-bold text-gray-900 sm:text-4xl">{title}</h2>
      {subtitle && <p className="mt-4 text-gray-600">{subtitle}</p>}
    </div>
  )
}

/* ==================== 主页面 ==================== */

function KaiPage() {
  return (
    <>
      {/* ───── Hero ───── */}
      <section className="relative overflow-hidden bg-gradient-to-b from-white via-brand-50/30 to-white">
        <div className="mx-auto max-w-6xl px-6 pb-20 pt-24 text-center sm:pt-32 sm:pb-28">
          <span className="inline-flex items-center rounded-full border border-brand-200 bg-brand-50 px-4 py-1.5 text-sm font-medium text-brand-700">
            KAI 期算白皮书 v2.0
          </span>
          <h1 className="mt-6 text-5xl font-extrabold tracking-tight text-gray-900 sm:text-6xl lg:text-7xl">
            <span className="text-brand-600">KAI 期算</span>
            <br />
            模型服务容量市场
          </h1>
          <p className="mx-auto mt-6 max-w-3xl text-lg leading-relaxed text-gray-600">
            将未来指定交割小时内某个确定 AI 模型的使用权，
            变成可采购、可比较、算容存取，算容置换，算容预订和可履约管理的标准产品。
          </p>
          {/* 一手定义卡片 */}
          <div className="mx-auto mt-10 inline-flex items-center gap-3 rounded-2xl border-2 border-brand-200 bg-white px-8 py-5 shadow-sm">
            <Zap className="h-8 w-8 text-brand-600" />
            <div className="text-left">
              <div className="text-sm text-gray-500">标准交易单位 · 一手</div>
              <div className="text-3xl font-extrabold text-brand-600">1,000,000 TPM × 1h</div>
            </div>
          </div>
        </div>
      </section>

      {/* ───── 核心公式 ───── */}
      <section className="border-y border-gray-100 bg-white">
        <div className="mx-auto max-w-4xl px-6 py-14 text-center">
          <span className="text-sm font-semibold uppercase tracking-wider text-brand-600">核心计量指标</span>
          <div className="mt-6 rounded-2xl border-2 border-brand-100 bg-brand-50/50 px-6 py-10">
            <div className="text-4xl font-extrabold tracking-tight text-gray-900 sm:text-5xl">
              TPM = I <span className="text-brand-600">+</span> W <span className="text-brand-600">×</span> O
            </div>
            <div className="mt-6 grid grid-cols-1 gap-4 text-sm text-gray-600 sm:grid-cols-3">
              <div className="rounded-lg bg-white px-4 py-3 shadow-sm">
                <span className="font-bold text-brand-600">I</span> — 每分钟经平台确认计费的输入 Token 数
              </div>
              <div className="rounded-lg bg-white px-4 py-3 shadow-sm">
                <span className="font-bold text-brand-600">O</span> — 每分钟实际生成并计费的输出 Token 数
              </div>
              <div className="rounded-lg bg-white px-4 py-3 shadow-sm">
                <span className="font-bold text-brand-600">W</span> — 输出权重系数，由品种规则确定，合约期内不变
              </div>
            </div>
          </div>
          <p className="mt-4 text-sm text-gray-400">
            TPM = Tokens Per Minute，每分钟加权 Token 负载 · 合约以 TPM·h 表示容量与持续时间
          </p>
        </div>
      </section>

      {/* ───── 市场背景 ───── */}
      <Section className="bg-white">
        <SectionTitle
          overline="为什么需要 KAI"
          title="AI 采购从按量调用走向容量保障"
          subtitle="企业对模型的要求已从「平均价格多少」转向「关键时刻能否获得确定能力」"
        />
        {/* 四大结构特征 */}
        <div className="mt-12 grid grid-cols-1 gap-5 sm:grid-cols-2 lg:grid-cols-4">
          {structuralFeatures.map((f) => {
            const Icon = f.icon
            return (
              <div key={f.title} className="rounded-2xl border border-gray-100 bg-white p-6 shadow-sm transition-shadow hover:shadow-md">
                <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-brand-50 text-brand-600">
                  <Icon className="h-5 w-5" />
                </div>
                <h3 className="mt-4 text-lg font-semibold text-gray-900">{f.title}</h3>
                <p className="mt-2 text-sm leading-relaxed text-gray-600">{f.desc}</p>
              </div>
            )
          })}
        </div>
        {/* 对比表格 */}
        <div className="mt-12 overflow-hidden rounded-2xl border border-gray-200 bg-white shadow-sm">
          <div className="overflow-x-auto">
            <table className="min-w-full text-sm">
              <thead>
                <tr className="border-b border-gray-100 bg-gray-50 text-left">
                  <th className="px-6 py-4 font-semibold text-gray-900">采购方式</th>
                  <th className="px-6 py-4 font-semibold text-gray-900">主要优点</th>
                  <th className="px-6 py-4 font-semibold text-gray-900">主要不足</th>
                </tr>
              </thead>
              <tbody>
                {comparisonRows.map((row) => (
                  <tr
                    key={row.method}
                    className={`border-b border-gray-50 last:border-0 ${
                      row.highlight ? 'bg-brand-50/60 font-medium' : ''
                    }`}
                  >
                    <td className={`px-6 py-4 ${row.highlight ? 'text-brand-700' : 'text-gray-900'}`}>
                      {row.highlight && <Zap className="mr-1 inline h-4 w-4 text-brand-600" />}
                      {row.method}
                    </td>
                    <td className="px-6 py-4 text-gray-600">{row.pros}</td>
                    <td className="px-6 py-4 text-gray-600">{row.cons}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      </Section>

      {/* ───── 产品价值 ───── */}
      <Section className="bg-white">
        <SectionTitle
          overline="产品价值"
          title="把模型容量变成企业采购资产"
        />
        <div className="mt-12 grid grid-cols-1 gap-10 lg:grid-cols-2">
          {/* 买方 */}
          <div>
            <h3 className="flex items-center gap-2 text-xl font-bold text-brand-600">
              <Building2 className="h-6 w-6" />
              对企业买方
            </h3>
            <p className="mt-2 text-sm text-gray-500">从「尽力而为」转向「可计划运营」</p>
            <div className="mt-6 space-y-4">
              {buyerValues.map((v) => {
                const Icon = v.icon
                return (
                  <div key={v.title} className="flex gap-4 rounded-xl border border-gray-100 bg-gray-50/70 p-5">
                    <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-brand-50 text-brand-600">
                      <Icon className="h-5 w-5" />
                    </div>
                    <div>
                      <h4 className="font-semibold text-gray-900">{v.title}</h4>
                      <p className="mt-1 text-sm text-gray-600">{v.desc}</p>
                    </div>
                  </div>
                )
              })}
            </div>
          </div>
          {/* 供应商 */}
          <div>
            <h3 className="flex items-center gap-2 text-xl font-bold text-brand-600">
              <Server className="h-6 w-6" />
              对容量供应商
            </h3>
            <p className="mt-2 text-sm text-gray-500">把闲置风险转化为可管理收入</p>
            <div className="mt-6 space-y-4">
              {supplierValues.map((v) => {
                const Icon = v.icon
                return (
                  <div key={v.title} className="flex gap-4 rounded-xl border border-gray-100 bg-gray-50/70 p-5">
                    <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-brand-50 text-brand-600">
                      <Icon className="h-5 w-5" />
                    </div>
                    <div>
                      <h4 className="font-semibold text-gray-900">{v.title}</h4>
                      <p className="mt-1 text-sm text-gray-600">{v.desc}</p>
                    </div>
                  </div>
                )
              })}
            </div>
          </div>
        </div>
        {/* 更多价值对象 */}
        <div className="mt-12 grid grid-cols-1 gap-5 sm:grid-cols-3">
          <div className="rounded-2xl border border-gray-100 bg-gray-50 p-6 text-center">
            <Box className="mx-auto h-8 w-8 text-brand-600" />
            <h4 className="mt-3 font-semibold text-gray-900">对模型发布方</h4>
            <p className="mt-2 text-sm text-gray-600">扩大模型企业采用范围，获得更可见的市场需求信号，多供应商交付降低企业对单一渠道的依赖。</p>
          </div>
          <div className="rounded-2xl border border-gray-100 bg-gray-50 p-6 text-center">
            <Globe className="mx-auto h-8 w-8 text-brand-600" />
            <h4 className="mt-3 font-semibold text-gray-900">对云服务商与数据中心</h4>
            <p className="mt-2 text-sm text-gray-600">获得更清晰的需求信号，把模型偏好、区域需求和时间稀缺性集中表达为价格，优化资源布局。</p>
          </div>
          <div className="rounded-2xl border border-gray-100 bg-gray-50 p-6 text-center">
            <Handshake className="mx-auto h-8 w-8 text-brand-600" />
            <h4 className="mt-3 font-semibold text-gray-900">对产业中介</h4>
            <p className="mt-2 text-sm text-gray-600">为聚合商、经纪、保险、评估、指数和数据服务商创造标准化市场，形成新的专业服务生态。</p>
          </div>
        </div>
      </Section>

      {/* ───── 市场参与者 ───── */}
      <Section className="bg-white">
        <SectionTitle
          overline="市场参与者"
          title="多方协作的角色网络"
          subtitle="KAI 市场不是简单的买卖双方关系，而是模型、服务、交易、交割和治理共同构成的生态"
        />
        <div className="mt-12 grid grid-cols-1 gap-5 sm:grid-cols-2 lg:grid-cols-3">
          {participants.map((p) => {
            const Icon = p.icon
            return (
              <div key={p.name} className="group rounded-2xl border border-gray-100 bg-white p-6 shadow-sm transition-shadow hover:shadow-md">
                <div className="flex h-12 w-12 items-center justify-center rounded-xl bg-brand-50 text-brand-600 transition-colors group-hover:bg-brand-100">
                  <Icon className="h-6 w-6" />
                </div>
                <h3 className="mt-4 text-lg font-semibold text-gray-900">{p.name}</h3>
                <p className="mt-2 text-sm leading-relaxed text-gray-600">{p.role}</p>
                <div className="mt-3 inline-flex items-center rounded-full border border-brand-100 bg-brand-50/50 px-3 py-1 text-xs font-medium text-brand-700">
                  {p.benefit}
                </div>
              </div>
            )
          })}
        </div>
      </Section>

      {/* ───── 市场全生命周期 ───── */}
      <Section className="bg-white">
        <SectionTitle
          overline="市场全生命周期"
          title="从模型准入到小时交割"
          subtitle="九步完整流程，确保容量从承诺到交付的闭环"
        />
        <div className="mt-12">
          {/* 桌面端横向步骤条 */}
          <div className="hidden grid-cols-9 gap-2 lg:grid">
            {lifecycleSteps.map((s, i) => (
              <div key={s.step} className="relative text-center">
                <div className="flex h-10 w-10 items-center justify-center rounded-full bg-brand-600 text-sm font-bold text-white mx-auto">
                  {s.step}
                </div>
                {i < lifecycleSteps.length - 1 && (
                  <div className="absolute left-[calc(50%+1.25rem)] top-5 h-0.5 w-[calc(100%-2.5rem)] bg-brand-200" />
                )}
                <h4 className="mt-3 text-sm font-semibold text-gray-900">{s.title}</h4>
              </div>
            ))}
          </div>
          <div className="hidden grid-cols-9 gap-2 lg:grid mt-4">
            {lifecycleSteps.map((s) => (
              <p key={s.step} className="text-xs leading-relaxed text-gray-500 text-center">{s.desc}</p>
            ))}
          </div>
          {/* 移动端竖排 */}
          <div className="space-y-4 lg:hidden">
            {lifecycleSteps.map((s, i) => (
              <div key={s.step} className="flex gap-4 rounded-xl border border-gray-100 bg-gray-50 p-4">
                <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-brand-600 text-sm font-bold text-white">
                  {s.step}
                </div>
                <div>
                  <h4 className="font-semibold text-gray-900">{s.title}</h4>
                  <p className="mt-1 text-sm text-gray-600">{s.desc}</p>
                </div>
              </div>
            ))}
          </div>
        </div>
      </Section>

      {/* ───── GLM-5.2 示例 ───── */}
      <Section className="bg-white">
        <SectionTitle
          overline="案例演示"
          title="一笔 KAI 期算合约如何运行"
          subtitle="以下数据全部为概念示例，不代表任何实际市场报价"
        />
        <div className="mt-12 grid grid-cols-1 gap-8 lg:grid-cols-2">
          {/* 合约参数 */}
          <div className="rounded-2xl border border-gray-100 bg-white p-6 shadow-sm">
            <h3 className="flex items-center gap-2 text-lg font-bold text-gray-900">
              <FileText className="h-5 w-5 text-brand-600" />
              示例合约参数
            </h3>
            <div className="mt-4 space-y-3">
              {contractParams.map((p) => (
                <div key={p.param} className="flex justify-between gap-4 border-b border-gray-50 pb-3 last:border-0 last:pb-0">
                  <span className="text-sm text-gray-500 shrink-0">{p.param}</span>
                  <span className="text-sm font-medium text-gray-900 text-right">{p.value}</span>
                </div>
              ))}
            </div>
          </div>
          {/* 参与方与交易 */}
          <div className="space-y-6">
            <div className="rounded-2xl border border-gray-100 bg-white p-6 shadow-sm">
              <h3 className="flex items-center gap-2 text-lg font-bold text-gray-900">
                <Building2 className="h-5 w-5 text-brand-600" />
                示例参与方（虚构）
              </h3>
              <div className="mt-4 grid grid-cols-2 gap-3 text-sm">
                <div className="rounded-lg bg-gray-50 px-3 py-2">
                  <span className="text-gray-400">买方</span>
                  <div className="font-semibold text-gray-900">星河电商</div>
                </div>
                <div className="rounded-lg bg-gray-50 px-3 py-2">
                  <span className="text-gray-400">供应商 A</span>
                  <div className="font-semibold text-gray-900">东海智算 (6手)</div>
                </div>
                <div className="rounded-lg bg-gray-50 px-3 py-2">
                  <span className="text-gray-400">供应商 B</span>
                  <div className="font-semibold text-gray-900">北辰云 (4手)</div>
                </div>
                <div className="rounded-lg bg-gray-50 px-3 py-2">
                  <span className="text-gray-400">备用供应商</span>
                  <div className="font-semibold text-gray-900">南港云</div>
                </div>
              </div>
            </div>
            {/* 成本计算 */}
            <div className="rounded-2xl border-2 border-brand-100 bg-brand-50/30 p-6">
              <h4 className="font-semibold text-gray-900">买方锁定未来成本</h4>
              <p className="mt-2 text-sm text-gray-600">星河电商为直播大促采购 10 手（10,000,000 TPM），成交时全额预付，价格锁定不受市场波动影响。</p>
              <div className="mt-4 space-y-2 text-sm">
                {exampleBuyerCost.map((row) => (
                  <div key={row.item} className="flex justify-between">
                    <span className="text-gray-500">{row.item}</span>
                    <span className="font-bold text-brand-600">{row.value}</span>
                  </div>
                ))}
                <div className="border-t border-brand-200 pt-2 text-xs text-gray-400">
                  供应商 A 交付 6 手 · 供应商 B 交付 4 手 · 交割完成后方结算服务价款
                </div>
              </div>
            </div>
          </div>
        </div>
      </Section>

      {/* ───── 四阶段路线 ───── */}
      <Section className="bg-white">
        <SectionTitle
          overline="发展路线"
          title="从试点到规模化市场"
          subtitle="四阶段渐进式推进，以真实企业需求和实物交割为锚"
        />
        <div className="mt-12 grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-4">
          {roadmap.map((r, i) => (
            <div key={r.phase} className="relative rounded-2xl border border-gray-100 bg-white p-6 shadow-sm">
              <div className="flex items-center gap-3">
                <span className="flex h-10 w-10 items-center justify-center rounded-full bg-brand-600 text-sm font-bold text-white">
                  {i}
                </span>
                <div>
                  <div className="text-xs font-medium text-brand-600">{r.time}</div>
                  <div className="font-bold text-gray-900">{r.title}</div>
                </div>
              </div>
              <span className="mt-1 inline-block text-sm font-semibold text-brand-600">{r.phase}</span>
              <ul className="mt-3 space-y-2">
                {r.tasks.map((t) => (
                  <li key={t} className="flex items-start gap-2 text-sm text-gray-600">
                    <span className="mt-1.5 h-1.5 w-1.5 shrink-0 rounded-full bg-brand-300" />
                    {t}
                  </li>
                ))}
              </ul>
            </div>
          ))}
        </div>
      </Section>

      {/* ───── 竞争壁垒 ───── */}
      <Section className="bg-white">
        <SectionTitle
          overline="竞争壁垒"
          title="如何形成长期基础设施价值"
          subtitle="KAI 的护城河来自标准、网络、数据和治理，而非单一撮合软件"
        />
        {/* 三飞轮 */}
        <div className="mt-12 grid grid-cols-1 gap-6 sm:grid-cols-3">
          {flywheels.map((f) => {
            const Icon = f.icon
            return (
              <div key={f.title} className="group rounded-2xl border border-brand-100 bg-white p-6 text-center shadow-sm transition-shadow hover:shadow-md">
                <div className="mx-auto flex h-14 w-14 items-center justify-center rounded-2xl bg-brand-50 text-brand-600 transition-colors group-hover:bg-brand-100">
                  <Icon className="h-7 w-7" />
                </div>
                <h3 className="mt-5 text-lg font-bold text-gray-900">{f.title}</h3>
                <p className="mt-2 text-sm leading-relaxed text-gray-600">{f.desc}</p>
              </div>
            )
          })}
        </div>
        {/* 四大壁垒 */}
        <div className="mt-10 grid grid-cols-1 gap-5 sm:grid-cols-2">
          {barriers.map((b) => (
            <div key={b.title} className="rounded-xl border border-gray-100 bg-white p-5 shadow-sm">
              <h4 className="font-semibold text-gray-900">{b.title}</h4>
              <p className="mt-2 text-sm leading-relaxed text-gray-600">{b.desc}</p>
            </div>
          ))}
        </div>
      </Section>

      {/* ───── CTA ───── */}
      <section className="bg-white py-20 sm:py-28">
        <div className="mx-auto max-w-4xl px-6 text-center">
          <h2 className="text-3xl font-bold text-gray-900 sm:text-4xl">
            了解更多关于 KAI 期算
          </h2>
          <p className="mt-4 text-gray-600">
            AI 服务市场的下一阶段，不仅是模型能力提升，也包括容量如何被计划、承诺、转移和定价。
          </p>
          <div className="mt-8 flex items-center justify-center gap-4">
            <a
              href="mailto:watt@vn.com"
              className="inline-flex items-center gap-2 rounded-lg bg-brand-600 px-6 py-3 text-base font-semibold text-white shadow-sm transition-colors hover:bg-brand-700"
            >
              联系我们
              <ArrowRight className="h-4 w-4" />
            </a>
            <Link
              to="/feedback"
              className="rounded-lg border border-gray-300 bg-white px-6 py-3 text-base font-semibold text-gray-700 transition-colors hover:border-brand-600 hover:text-brand-600"
            >
              提交反馈
            </Link>
          </div>
        </div>
      </section>
    </>
  )
}
