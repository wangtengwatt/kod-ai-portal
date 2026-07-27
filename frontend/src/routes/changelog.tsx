import { createFileRoute } from '@tanstack/react-router'

export const Route = createFileRoute('/changelog')({
  component: ChangelogPage,
})

const releases = [
  {
    version: 'v0.2.0',
    date: '2026-07-24',
    items: [
      '品牌升级：KOD 重新定位为「蒜粒期货 AI 助手」，以 KAI 期算为核心标识',
      '首页重写：突出 KAI 期算标准合约、分时交付、跨供应商竞价与二级转让能力',
      '新增「KAI 期算白皮书」独立页面，完整呈现模型服务容量市场概念框架',
      '功能页重组：新增「KAI 期算交易能力」版块，整合市场核心功能',
      '定价页升级：新增 KAI 期算市场交易费用区块',
      'FAQ 扩充：新增期算概念、KOD 与 KAI 关系、适用场景等问答',
      '导航与页脚重构：KAI 期算提升至核心位置，添加品牌标语',
      'Web 版正式上线，浏览器即开即用',
      '官网改版：全新首页、数据亮点区、下载页桌面端/移动端/Web版分类展示',
      '新增登录 / 注册页面：邮箱 + 密码 + 验证码体系，JWT Token 鉴权',
      '新增邮箱验证码服务，60 秒防刷，5 分钟有效',
      '前端 UI 全面升级：Lucide 图标库、品牌色板扩展、页面视觉统一',
      '导航栏新增"立即使用"入口，直达 KOD Web 版',
      '首页移除开源项目引用，替换为 KOD 品牌文案',
      'Docker Compose 部署方案 + 运维文档',
      '后端注册流程合并入登录接口，邀请码关联中转站',
    ],
  },
  {
    version: 'v0.1.0',
    date: '2026-07-16',
    items: [
      '官网上线：首页、产品特性、下载、定价、FAQ、更新日志、意见反馈',
      '登录 / 注册页面：首次登录即注册，邀请码关联中转站',
      '后端服务上线：Spring Boot + MyBatis-Plus，JWT 认证',
    ],
  },
]

function ChangelogPage() {
  return (
    <section className="bg-white">
      <div className="mx-auto max-w-3xl px-6 py-20 sm:py-28">
        <h1 className="text-4xl font-bold text-gray-900 sm:text-5xl">更新日志</h1>
        <p className="mt-4 text-gray-600">记录 KOD蒜粒 的每一次迭代与改进。</p>

        <div className="mt-12 space-y-10">
          {releases.map((r, idx) => (
            <div key={r.version} className="border-l-2 border-brand-200 pl-6">
              <div className="flex items-baseline gap-3">
                <h2 className="text-xl font-semibold text-brand-600">{r.version}</h2>
                <span className="text-sm text-gray-400">{r.date}</span>
                {idx === 0 && (
                  <span className="rounded-full bg-brand-50 px-2.5 py-0.5 text-xs font-medium text-brand-700">
                    最新
                  </span>
                )}
              </div>
              <ul className="mt-3 list-disc space-y-1.5 pl-5 text-sm text-gray-600">
                {r.items.map((item) => (
                  <li key={item}>{item}</li>
                ))}
              </ul>
            </div>
          ))}
        </div>
      </div>
    </section>
  )
}
