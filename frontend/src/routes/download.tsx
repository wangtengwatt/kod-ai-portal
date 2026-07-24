import { createFileRoute } from '@tanstack/react-router'
import { KOD_WEB_URL } from '@/config'

export const Route = createFileRoute('/download')({
  component: DownloadPage,
})

const desktopPlatforms = [
  { name: 'Windows', desc: 'Windows 10 及以上，.exe 安装包', icon: '🪟' },
  { name: 'macOS', desc: 'Apple Silicon / Intel 芯片', icon: '🍎' },
  { name: 'Linux', desc: 'AppImage / .deb 安装包', icon: '🐧' },
]

const mobilePlatforms = [
  { name: 'Android', desc: 'Android 8.0 及以上', icon: '🤖' },
  { name: 'iOS', desc: 'iOS 15 及以上', icon: '📱' },
]

const webPlatform = {
  name: 'Web 版',
  desc: '浏览器直接使用，无需安装',
  icon: '🌐',
  url: KOD_WEB_URL,
}

function DownloadPage() {
  return (
    <section className="bg-gray-50">
      <div className="mx-auto max-w-6xl px-6 py-20 sm:py-28">
        <div className="text-center">
          <h1 className="text-4xl font-bold text-gray-900 sm:text-5xl">下载 KOD</h1>
          <p className="mt-4 text-gray-600">选择你的平台，免费开始使用。</p>
        </div>

        {/* 桌面端 */}
        <div className="mt-14">
          <h2 className="mb-6 text-base font-semibold uppercase tracking-wider text-gray-400">桌面端</h2>
          <div className="grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-3">
            {desktopPlatforms.map((p) => (
              <div
                key={p.name}
                className="flex items-center gap-4 rounded-2xl border border-gray-100 bg-white p-6 shadow-sm transition-shadow hover:shadow-md"
              >
                <div className="text-3xl">{p.icon}</div>
                <div className="flex-1">
                  <h3 className="text-lg font-semibold text-gray-900">{p.name}</h3>
                  <p className="text-sm text-gray-500">{p.desc}</p>
                </div>
                <button
                  type="button"
                  className="rounded-lg bg-brand-600 px-4 py-2 text-sm font-semibold text-white transition-colors hover:bg-brand-700"
                >
                  下载
                </button>
              </div>
            ))}
          </div>
        </div>

        {/* 移动端 */}
        <div className="mt-14">
          <h2 className="mb-6 text-base font-semibold uppercase tracking-wider text-gray-400">移动端</h2>
          <div className="grid grid-cols-1 gap-6 sm:grid-cols-2">
            {mobilePlatforms.map((p) => (
              <div
                key={p.name}
                className="flex items-center gap-4 rounded-2xl border border-gray-100 bg-white p-6 shadow-sm transition-shadow hover:shadow-md"
              >
                <div className="text-3xl">{p.icon}</div>
                <div className="flex-1">
                  <h3 className="text-lg font-semibold text-gray-900">{p.name}</h3>
                  <p className="text-sm text-gray-500">{p.desc}</p>
                </div>
                <button
                  type="button"
                  className="rounded-lg bg-brand-600 px-4 py-2 text-sm font-semibold text-white transition-colors hover:bg-brand-700"
                >
                  下载
                </button>
              </div>
            ))}
          </div>
        </div>

        {/* Web 版 */}
        <div className="mt-14">
          <h2 className="mb-6 text-base font-semibold uppercase tracking-wider text-gray-400">Web 版</h2>
          <div className="grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-3">
            <div className="flex items-center gap-4 rounded-2xl border border-brand-200 bg-brand-50/30 p-6 shadow-sm transition-shadow hover:shadow-md">
              <div className="text-3xl">{webPlatform.icon}</div>
              <div className="flex-1">
                <h3 className="text-lg font-semibold text-gray-900">{webPlatform.name}</h3>
                <p className="text-sm text-gray-500">{webPlatform.desc}</p>
              </div>
              <a
                href={webPlatform.url}
                target="_blank"
                rel="noopener noreferrer"
                className="rounded-lg bg-brand-600 px-4 py-2 text-sm font-semibold text-white transition-colors hover:bg-brand-700"
              >
                立即使用
              </a>
            </div>
          </div>
        </div>
      </div>
    </section>
  )
}
