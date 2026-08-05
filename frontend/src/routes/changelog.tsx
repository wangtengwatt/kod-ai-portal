import { createFileRoute } from '@tanstack/react-router'

export const Route = createFileRoute('/changelog')({
  component: ChangelogPage,
})

const releases = [
  {
    version: 'v0.3.0',
    date: '2026-08-04',
    items: [
      '控制台全面重构：新增深色侧边栏布局，概览/钱包/操作日志三合一导航，移除独立 API Key 配置页',
      '概览看板接入实时 API：模型汇总统计卡片（余额/调用/配额/Token）、最近 24 小时用量趋势列表、快捷操作入口',
      '钱包功能完善：渐变余额卡片展示可用余额与累计消费，在线充值面板支持支付宝/微信支付并跳转收银台，交易记录支持分页与筛选',
      '操作日志上线：日志同步启停控制面板（启动/停止按钮 + 实时状态轮询），72 小时小时级用量明细表格，支持时间搜索与分页',
      '官网蒜宝智能助手（SSE 流式对话）：基于 DeepSeek V4 Pro 模型，上下文感知当前浏览页面，支持自动推荐相关功能链接，每分钟/每天/每设备三级速率限制',
      '后端新增 Assistant 模块：AssistantController + AssistantService + AssistantProperties，代理上游 LLM 实现 SSE 流式响应，Redis 速率限制',
      '修复 dotenv 加载路径：KodPortalApplication 自动从项目根目录加载 .env，确保部署时环境变量正确注入',
      '前端 API 层重构：提取共享 HTTP 客户端（自动附加 JWT 认证头、统一错误处理），Dashboard/Wallet/Session/RelayStation/LogSync 五大模块',
      '路由架构优化：控制台路由添加 beforeLoad 鉴权守卫，未登录自动重定向登录页，修复蒜宝路由类型错误',
    ],
  },
  {
    version: 'v0.2.0',
    date: '2026-07-24',
    items: [
      '品牌升级为「蒜粒期货 AI 助手」，首页重写，突出 KAI 期算标准合约与分时交付',
      '新增 KAI 期算白皮书独立页面，功能页重组为五大体系',
      'Web 版上线，新增邮箱注册登录与 JWT 鉴权，FAQ 扩充至 9 条',
      '前端 UI 全面升级，导航页脚重构，Docker Compose 一键部署',
    ],
  },
  {
    version: 'v0.1.0',
    date: '2026-07-16',
    items: [
      '官网上线：首页、产品特性、下载、FAQ、更新日志、意见反馈',
      '登录即注册，邀请码关联中转站',
      '后端 Spring Boot + MyBatis-Plus，JWT 认证',
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
