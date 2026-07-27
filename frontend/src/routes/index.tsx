import { createFileRoute, Link } from '@tanstack/react-router'
import { ArrowRight, BarChart3, Building2, Clock, Cpu, FileText, Layers, Server, TrendingUp, Zap } from 'lucide-react'

export const Route = createFileRoute('/')({
  component: HomePage,
})

/* ==================== 能力数据 ==================== */

const capabilities = [
  {
    step: '01',
    icon: FileText,
    title: '标准合约，统一品种',
    desc: '基于 KAI 期算规则，定义模型版本、上下文上限、每手 1,000,000 TPM、交割小时与区域——所有供应商在同一标准下交付，买方直接比较、直接采购。',
    reverse: false,
  },
  {
    step: '02',
    icon: Clock,
    title: '分时交付，精确到小时',
    desc: '不是模糊的「尽量保证」，而是合同约定的具体交割小时。供应商承担明确交付义务，大促、直播、批处理场景中，容量从弹性资源升级为确定保障。',
    reverse: true,
  },
  {
    step: '03',
    icon: BarChart3,
    title: '跨供应商竞价，最优成交',
    desc: '同一品种面向多家合格供应商询价，KOD 聚合报价与历史成交数据，帮助企业快速锁定最优条件。供应商故障时，平台协调备用容量替代交付。',
    reverse: false,
  },
]

/* ==================== 角色卡片 ==================== */

const audiences = [
  {
    icon: Building2,
    title: '企业买方',
    desc: '为关键业务提前锁定模型容量与成本',
    items: ['容量保障：高峰期不排队、不降级', '预算确定性：采购时锁定价格', '灵活转让：关闸前可出售或补购', '多供应商：降低单一依赖风险'],
  },
  {
    icon: Server,
    title: '容量供应商',
    desc: '把闲置蒜粒转化为可管理收入',
    items: ['提前销售：锁定未来收入和利用率', '扩大覆盖：接触标准化企业需求', '分层定价：区分基础、峰值和备用容量', '数据驱动：远期价格辅助扩容决策'],
  },
  {
    icon: Layers,
    title: '模型方与生态',
    desc: '将模型影响力扩展为可计量服务生态',
    items: ['版本价值可观察：价格反映市场评价', '多运营商交付：降低单渠道依赖', '透明退市：新版本挂牌，旧版本有序退出', '聚合、保险、指数等衍生服务'],
  },
]

/* ==================== 主页面 ==================== */

function HomePage() {
  return (
    <>
      {/* ═══════════ Hero — 白底全宽 ═══════════ */}
      <section className="relative overflow-hidden bg-white">
        <div className="relative mx-auto max-w-6xl px-6 pb-24 pt-24 text-center sm:pb-36 sm:pt-36">
          {/* 标签 */}
          <span className="inline-flex items-center rounded-full border border-brand-200 bg-brand-50 px-4 py-1.5 text-sm font-medium text-brand-700">
            <Zap className="mr-1.5 h-3.5 w-3.5" />
            正在锐意开发中
          </span>

          {/* 标题 */}
          <h1 className="mt-8 text-5xl font-extrabold tracking-tight text-gray-900 sm:text-6xl lg:text-7xl">
            KOD
            <span className="text-brand-600"> — 蒜粒期货 AI 助手</span>
          </h1>

          {/* 副标题 */}
          <p className="mx-auto mt-6 max-w-2xl text-lg leading-relaxed text-gray-600">
            首个深度集成 KAI 期算标准的 AI 助手。不只是对话工具——
            更是你的蒜粒期货交易、交付与管理平台。
          </p>

          {/* CTA */}
          <div className="mt-10 flex items-center justify-center gap-4">
            <Link
              to="/download"
              className="inline-flex items-center gap-2 rounded-lg bg-brand-600 px-6 py-3 text-base font-semibold text-white shadow-sm transition-colors hover:bg-brand-700"
            >
              免费下载 KOD
              <ArrowRight className="h-4 w-4" />
            </Link>
            <Link
              to="/kai"
              className="inline-flex items-center gap-2 rounded-lg border border-brand-300 px-6 py-3 text-base font-semibold text-brand-600 transition-colors hover:border-brand-600 hover:bg-brand-50"
            >
              了解 KAI 期算
            </Link>
          </div>
        </div>
      </section>

      {/* ═══════════ KOD 是什么 — 三列聚焦 ═══════════ */}
      <section className="bg-white pb-20 sm:pb-28">
        <div className="mx-auto max-w-6xl px-6">
          <div className="mb-14 text-center">
            <h2 className="text-3xl font-bold text-gray-900 sm:text-4xl">
              KOD 是什么
            </h2>
            <p className="mt-4 text-lg font-semibold text-brand-600">
              蒜粒期货 AI 助手 —— 让模型容量可采购、可交付、可转让
            </p>
          </div>
          <div className="grid grid-cols-1 gap-8 md:grid-cols-3">
            {/* 蒜粒期货交易 */}
            <div className="group rounded-2xl bg-brand-50/50 p-8 transition-colors hover:bg-brand-50">
              <div className="flex h-14 w-14 items-center justify-center rounded-2xl bg-brand-600 text-white shadow-lg shadow-brand-200/50">
                <TrendingUp className="h-7 w-7" />
              </div>
              <h3 className="mt-6 text-xl font-bold text-gray-900">蒜粒期货交易</h3>
              <p className="mt-3 text-sm leading-relaxed text-gray-600">
                内置 KAI 期算市场，以 TPM·h 为标准合约单位。浏览行情、参与竞价、
                采购未来容量、管理持仓——从下单到交割，完整闭环。
              </p>
            </div>

            {/* 确定性交付 */}
            <div className="group rounded-2xl bg-brand-50/50 p-8 transition-colors hover:bg-brand-50">
              <div className="flex h-14 w-14 items-center justify-center rounded-2xl bg-brand-600 text-white shadow-lg shadow-brand-200/50">
                <Clock className="h-7 w-7" />
              </div>
              <h3 className="mt-6 text-xl font-bold text-gray-900">确定性交付</h3>
              <p className="mt-3 text-sm leading-relaxed text-gray-600">
                不是「尽力而为」的弹性调用，而是合同约定的分时交割。
                供应商承担明确交付义务，未达标由平台协调替代履约和违约赔偿。
              </p>
            </div>

            {/* AI 智能工作台 */}
            <div className="group rounded-2xl bg-brand-50/50 p-8 transition-colors hover:bg-brand-50">
              <div className="flex h-14 w-14 items-center justify-center rounded-2xl bg-brand-600 text-white shadow-lg shadow-brand-200/50">
                <Cpu className="h-7 w-7" />
              </div>
              <h3 className="mt-6 text-xl font-bold text-gray-900">AI 智能工作台</h3>
              <p className="mt-3 text-sm leading-relaxed text-gray-600">
                桌面端、移动端、Web 端一致体验。用采购的蒜粒直接对话，
                追踪交割状态，接收行情通知——从交易到使用，一个平台完成。
              </p>
            </div>
          </div>
        </div>
      </section>

      {/* ═══════════ 产品能力 — 左右交替 ═══════════ */}
      <section className="bg-white pb-20 sm:pb-28">
        <div className="mx-auto max-w-6xl px-6">
          <div className="mb-16 text-center">
            <h2 className="text-3xl font-bold text-gray-900 sm:text-4xl">
              作为蒜粒期货 AI 助手，KOD 改变了什么
            </h2>
          </div>

          <div className="space-y-20 sm:space-y-28">
            {capabilities.map((cap) => {
              const Icon = cap.icon
              return (
                <div
                  key={cap.step}
                  className={`flex flex-col items-center gap-10 ${
                    cap.reverse ? 'lg:flex-row-reverse' : 'lg:flex-row'
                  }`}
                >
                  {/* 视觉侧 */}
                  <div className="flex shrink-0 items-center justify-center lg:w-1/2">
                    <div className="flex h-36 w-36 items-center justify-center rounded-3xl bg-brand-100 shadow-inner">
                      <Icon className="h-16 w-16 text-brand-600" />
                    </div>
                  </div>

                  {/* 文字侧 */}
                  <div className="lg:w-1/2">
                    <span className="text-sm font-bold tracking-widest text-brand-400">{cap.step}</span>
                    <h3 className="mt-2 text-2xl font-bold text-gray-900 sm:text-3xl">{cap.title}</h3>
                    <p className="mt-4 text-base leading-relaxed text-gray-600">{cap.desc}</p>
                  </div>
                </div>
              )
            })}
          </div>
        </div>
      </section>

      {/* ═══════════ 为谁服务 — 顶部色条卡片 ═══════════ */}
      <section className="bg-brand-50/30 py-20 sm:py-28">
        <div className="mx-auto max-w-6xl px-6">
          <div className="mb-14 text-center">
            <h2 className="text-3xl font-bold text-gray-900 sm:text-4xl">
              为蒜粒生态的每一方创造价值
            </h2>
            <p className="mt-4 text-gray-600">
              不论你是买方、供应商还是模型方，KOD 蒜粒期货 AI 助手都为你的角色提供确定价值
            </p>
          </div>

          <div className="grid grid-cols-1 gap-8 md:grid-cols-3">
            {audiences.map((a) => {
              const Icon = a.icon
              return (
                <div
                  key={a.title}
                  className="group flex flex-col overflow-hidden rounded-2xl bg-white shadow-sm transition-shadow hover:shadow-md"
                >
                  {/* 顶部色条 */}
                  <div className="h-2 bg-brand-600 transition-colors group-hover:bg-brand-500" />

                  <div className="flex flex-1 flex-col p-7">
                    <div className="flex h-12 w-12 items-center justify-center rounded-xl bg-brand-50 text-brand-600">
                      <Icon className="h-6 w-6" />
                    </div>
                    <h3 className="mt-5 text-xl font-bold text-gray-900">{a.title}</h3>
                    <p className="mt-2 text-sm text-gray-500">{a.desc}</p>

                    <ul className="mt-6 flex-1 space-y-3">
                      {a.items.map((item) => (
                        <li key={item} className="flex items-start gap-3 text-sm text-gray-600">
                          <span className="mt-1.5 h-1.5 w-1.5 shrink-0 rounded-full bg-brand-400" />
                          {item}
                        </li>
                      ))}
                    </ul>

                    <Link
                      to="/kai"
                      className="mt-6 inline-flex items-center gap-1 text-sm font-semibold text-brand-600 transition-colors hover:text-brand-700"
                    >
                      了解更多
                      <ArrowRight className="h-3.5 w-3.5" />
                    </Link>
                  </div>
                </div>
              )
            })}
          </div>
        </div>
      </section>

      {/* ═══════════ CTA — 简洁收尾 ═══════════ */}
      <section className="bg-white py-20 sm:py-28">
        <div className="mx-auto max-w-3xl px-6 text-center">
          <h2 className="text-3xl font-bold text-gray-900 sm:text-4xl">
            开始使用 KOD 蒜粒期货 AI 助手
          </h2>
          <p className="mt-4 text-gray-600">
            下载 KOD，接入 KAI 期算市场，让每一单位模型容量都可计划、可交易、可保障。
          </p>
          <div className="mt-8 flex items-center justify-center gap-4">
            <Link
              to="/download"
              className="inline-flex items-center gap-2 rounded-lg bg-brand-600 px-6 py-3 text-base font-semibold text-white shadow-sm transition-colors hover:bg-brand-700"
            >
              免费下载 KOD
              <ArrowRight className="h-4 w-4" />
            </Link>
            <Link
              to="/kai"
              className="rounded-lg border border-gray-300 bg-white px-6 py-3 text-base font-semibold text-gray-700 transition-colors hover:border-brand-600 hover:text-brand-600"
            >
              阅读白皮书
            </Link>
          </div>
        </div>
      </section>
    </>
  )
}
