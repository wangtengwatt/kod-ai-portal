import { createFileRoute } from '@tanstack/react-router'

/** 下载页：展示各平台发行计划和安装说明。 */
export const Route = createFileRoute('/download')({
  component: DownloadPage,
})

type PlatformRelease = {
  name: string
  description: string
  requirements: string
  icon: string
  version: string
  status: 'available' | 'coming-soon'
  downloadUrl?: string
}

const releases: PlatformRelease[] = [
  {
    name: 'Windows',
    description: '适用于 Windows 桌面端',
    requirements: 'Windows 10 及以上（64 位）',
    icon: 'Win',
    version: 'v0.1.0',
    status: 'coming-soon',
  },
  {
    name: 'macOS',
    description: '适用于 Apple Silicon 和 Intel Mac',
    requirements: 'macOS 12 及以上',
    icon: 'Mac',
    version: 'v0.1.0',
    status: 'coming-soon',
  },
  {
    name: 'Linux',
    description: '提供 AppImage 和 deb 安装包',
    requirements: 'Ubuntu 20.04 及兼容发行版',
    icon: 'Linux',
    version: 'v0.1.0',
    status: 'coming-soon',
  },
  {
    name: 'Android',
    description: '适用于 Android 手机和平板',
    requirements: 'Android 8.0 及以上',
    icon: 'Android',
    version: '规划中',
    status: 'coming-soon',
  },
  {
    name: 'iOS',
    description: '适用于 iPhone 和 iPad',
    requirements: 'iOS 15 及以上',
    icon: 'iOS',
    version: '规划中',
    status: 'coming-soon',
  },
  {
    name: 'Web',
    description: '通过现代浏览器使用 KOD',
    requirements: 'Chrome、Edge、Safari 最新稳定版',
    icon: 'Web',
    version: '规划中',
    status: 'coming-soon',
  },
]

function DownloadPage() {
  return (
    <section className="mx-auto max-w-6xl px-6 py-16 sm:py-20">
      <div className="max-w-2xl">
        <p className="text-sm font-semibold uppercase tracking-[0.2em] text-brand-600">
          KOD 蒜粒客户端
        </p>
        <h1 className="mt-4 text-4xl font-bold tracking-tight text-gray-900 sm:text-5xl">
          下载 kod，开始使用 AI
        </h1>
        <p className="mt-5 text-lg leading-8 text-gray-600">
          选择你的平台。正式版本发布后，我们会在这里提供版本号、系统要求和安全的下载入口。
        </p>
      </div>

      <div className="mt-12 grid grid-cols-1 gap-6 md:grid-cols-3">
        {releases.map((release) => (
          <article
            key={release.name}
            className="flex min-h-64 flex-col rounded-2xl border border-gray-200 bg-white p-6 shadow-sm"
          >
            <div className="flex items-start justify-between gap-4">
              <div className="flex h-12 w-12 items-center justify-center rounded-xl bg-brand-50 text-sm font-bold text-brand-700">
                <span aria-hidden="true">{release.icon}</span>
              </div>
              <span className="rounded-full bg-gray-100 px-3 py-1 text-xs font-medium text-gray-600">
                {release.version}
              </span>
            </div>

            <h2 className="mt-6 text-xl font-semibold text-gray-900">{release.name}</h2>
            <p className="mt-2 text-sm text-gray-600">{release.description}</p>
            <p className="mt-2 text-sm text-gray-500">系统要求：{release.requirements}</p>

            <div className="mt-auto pt-6">
              {release.status === 'available' && release.downloadUrl ? (
                <a
                  href={release.downloadUrl}
                  className="inline-flex w-full items-center justify-center rounded-lg bg-brand-600 px-4 py-2.5 text-sm font-semibold text-white transition-colors hover:bg-brand-700 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-600"
                  download
                >
                  下载 {release.name} 版本
                </a>
              ) : (
                <button
                  type="button"
                  disabled
                  className="w-full cursor-not-allowed rounded-lg bg-gray-100 px-4 py-2.5 text-sm font-semibold text-gray-400"
                >
                  即将上线
                </button>
              )}
            </div>
          </article>
        ))}
      </div>

      <div className="mt-16 grid gap-8 rounded-2xl bg-gray-50 p-6 sm:grid-cols-2 sm:p-8">
        <div>
          <h2 className="text-lg font-semibold text-gray-900">安装说明</h2>
          <ol className="mt-4 list-decimal space-y-2 pl-5 text-sm leading-6 text-gray-600">
            <li>下载与你的操作系统匹配的安装包。</li>
            <li>运行安装程序，并按照向导完成安装。</li>
            <li>启动 kod，使用邀请码登录并开始对话。</li>
          </ol>
        </div>
        <div>
          <h2 className="text-lg font-semibold text-gray-900">安全提示</h2>
          <p className="mt-4 text-sm leading-6 text-gray-600">
            请仅从本页面获取正式安装包。发布下载链接后，我们会补充发布日期、文件校验信息和对应的版本更新记录。
          </p>
        </div>
      </div>
    </section>
  )
}
