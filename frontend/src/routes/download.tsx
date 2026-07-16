import { createFileRoute } from '@tanstack/react-router'

/** 下载页：各平台下载入口（占位）。 */
export const Route = createFileRoute('/download')({
  component: DownloadPage,
})

const platforms = [
  { name: 'Windows', desc: 'Windows 10 及以上', icon: '🪟' },
  { name: 'macOS', desc: 'Apple Silicon / Intel', icon: '🍎' },
  { name: 'Linux', desc: 'AppImage / deb', icon: '🐧' },
  { name: 'Android', desc: 'Android 8.0 及以上', icon: '🤖' },
  { name: 'iOS', desc: 'iOS 15 及以上', icon: '📱' },
  { name: 'Web', desc: '浏览器直接使用', icon: '🌐' },
]

function DownloadPage() {
  return (
    <section className="mx-auto max-w-6xl px-6 py-20">
      <h1 className="text-4xl font-bold text-gray-900">下载 kod</h1>
      <p className="mt-4 text-gray-600">选择你的平台，免费开始使用。</p>
      <div className="mt-12 grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-3">
        {platforms.map((p) => (
          <div
            key={p.name}
            className="flex items-center gap-4 rounded-2xl border border-gray-100 p-6 transition-shadow hover:shadow-md"
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
    </section>
  )
}
