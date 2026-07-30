import { Link, createFileRoute } from '@tanstack/react-router'

/** 更新日志页（/changelog）：按版本展示 KOD 的发布计划与变更。 */
export const Route = createFileRoute('/changelog')({
  component: ChangelogPage,
})

type ReleaseSection = {
  title: string
  items: string[]
}

type Release = {
  version: string
  status: 'planned' | 'released'
  date?: string
  summary: string
  sections: ReleaseSection[]
}

const releases: Release[] = [
  {
    version: 'v0.1.0',
    status: 'planned',
    summary: 'KOD 蒜粒首个产品化版本，聚焦品牌收口、官网入口和 AI 服务全链路。',
    sections: [
      {
        title: '新增',
        items: [
          'KOD 官网首页、功能介绍、下载、FAQ、更新日志、反馈和问卷入口。',
          'Windows、macOS、Linux、Android、iOS 和 Web 多平台发行入口。',
          '客户端登录后获取零售站配置、模型列表并调用 AI 模型的基础链路。',
        ],
      },
      {
        title: '改进',
        items: [
          '统一 KOD 蒜粒品牌文案与官网信息结构。',
          '模型能力标签和多模型接入正在持续扩展。',
          '未发布功能采用明确的灰度状态，避免无反馈的入口。',
        ],
      },
      {
        title: '已知限制',
        items: [
          '正式客户端安装包、签名、下载地址和文件校验信息尚未发布。',
          '钱包充值、API Token 管理、零售站价格对比和自动故障切换仍在开发中。',
          '实际可用模型会因零售站渠道、账号权限和服务状态而不同。',
        ],
      },
    ],
  },
]

function ChangelogPage() {
  return (
    <section className="mx-auto max-w-4xl px-6 py-16 sm:py-20">
      <div className="max-w-2xl">
        <p className="text-sm font-semibold uppercase tracking-[0.2em] text-brand-600">
          Release notes
        </p>
        <h1 className="mt-4 text-4xl font-bold tracking-tight text-gray-900 sm:text-5xl">
          KOD 更新日志
        </h1>
        <p className="mt-5 text-lg leading-8 text-gray-600">
          记录 KOD 蒜粒的版本计划、新增能力和已知限制。标记为“计划发布”的内容仍可能在正式发布前调整。
        </p>
      </div>

      <div className="mt-12 space-y-10">
        {releases.map((release) => (
          <article key={release.version} className="rounded-2xl border border-gray-200 bg-white p-6 sm:p-8">
            <header className="flex flex-col gap-4 border-b border-gray-100 pb-6 sm:flex-row sm:items-start sm:justify-between">
              <div>
                <div className="flex flex-wrap items-center gap-3">
                  <h2 className="text-2xl font-semibold text-gray-900">{release.version}</h2>
                  <span
                    className={
                      release.status === 'released'
                        ? 'rounded-full bg-green-50 px-3 py-1 text-xs font-semibold text-green-700'
                        : 'rounded-full bg-gray-100 px-3 py-1 text-xs font-semibold text-gray-600'
                    }
                  >
                    {release.status === 'released' ? '已发布' : '计划发布'}
                  </span>
                </div>
                <p className="mt-3 max-w-2xl text-sm leading-6 text-gray-600">{release.summary}</p>
              </div>
              <time className="shrink-0 text-sm text-gray-500">{release.date ?? '发布日期待定'}</time>
            </header>

            <div className="mt-6 grid gap-7 sm:grid-cols-3">
              {release.sections.map((section) => (
                <section key={section.title}>
                  <h3 className="font-semibold text-gray-900">{section.title}</h3>
                  <ul className="mt-3 space-y-2 text-sm leading-6 text-gray-600">
                    {section.items.map((item) => (
                      <li key={item} className="flex gap-2">
                        <span aria-hidden="true" className="mt-2 h-1.5 w-1.5 shrink-0 rounded-full bg-brand-500" />
                        <span>{item}</span>
                      </li>
                    ))}
                  </ul>
                </section>
              ))}
            </div>
          </article>
        ))}
      </div>

      <div className="mt-12 rounded-2xl bg-gray-50 p-6 sm:flex sm:items-center sm:justify-between sm:gap-8">
        <div>
          <h2 className="font-semibold text-gray-900">准备体验 KOD？</h2>
          <p className="mt-2 text-sm leading-6 text-gray-600">
            前往下载页查看各平台的当前发布状态和系统要求。
          </p>
        </div>
        <Link
          to="/download"
          className="mt-5 inline-flex rounded-lg bg-brand-600 px-4 py-2.5 text-sm font-semibold text-white transition-colors hover:bg-brand-700 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-600 sm:mt-0"
        >
          查看下载页面
        </Link>
      </div>
    </section>
  )
}
