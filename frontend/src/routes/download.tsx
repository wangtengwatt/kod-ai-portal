import { createFileRoute } from '@tanstack/react-router'
import { Apple, Bot, Globe2, MonitorDown, Smartphone, Terminal } from 'lucide-react'
import type { ComponentType, ReactNode } from 'react'
import { KOD_WEB_URL } from '@/config'

export const Route = createFileRoute('/download')({ component: DownloadPage })

const ANDROID_APK_URL =
  'https://github.com/wangtengwatt/KOD/releases/download/android-v0.1.0/KOD-Android-v0.1.0-universal.apk'

const desktopPlatforms = [
  { name: 'Windows', desc: 'Windows 10 及以上，.exe 安装包', icon: MonitorDown },
  { name: 'macOS', desc: 'Apple Silicon / Intel 芯片', icon: Apple },
  { name: 'Linux', desc: 'AppImage / .deb 安装包', icon: Terminal },
]

const mobilePlatforms = [
  { name: 'Android', desc: 'Android 8.0 及以上 · KOD 0.1.0', icon: Bot, url: ANDROID_APK_URL },
  { name: 'iOS', desc: 'iOS 版本准备中', icon: Smartphone },
]

function DownloadPage() {
  return (
    <section className="bg-white">
      <div className="mx-auto max-w-6xl px-6 py-20 sm:py-28">
        <div className="text-center">
          <h1 className="text-4xl font-bold text-gray-900 sm:text-5xl">下载 KOD 蒜粒</h1>
          <p className="mt-4 text-gray-600">选择你的平台，免费开始使用。Android 正式版已开放下载。</p>
        </div>

        <PlatformSection title="桌面端" columns="lg:grid-cols-3">
          {desktopPlatforms.map((platform) => (
            <PlatformCard key={platform.name} {...platform} />
          ))}
        </PlatformSection>

        <PlatformSection title="移动端" columns="sm:grid-cols-2">
          {mobilePlatforms.map((platform) => (
            <PlatformCard key={platform.name} {...platform} />
          ))}
        </PlatformSection>

        <div className="mt-14">
          <h2 className="mb-6 text-base font-semibold uppercase tracking-wider text-gray-400">Web 版</h2>
          <div className="grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-3">
            <div className="flex items-center gap-4 rounded-2xl border border-brand-200 bg-brand-50/30 p-6 shadow-sm">
              <Globe2 className="size-8 text-brand-600" aria-hidden="true" />
              <div className="min-w-0 flex-1">
                <h3 className="text-lg font-semibold text-gray-900">Web 版</h3>
                <p className="text-sm text-gray-500">浏览器直接使用，无需安装</p>
              </div>
              <a
                href={KOD_WEB_URL || '/console'}
                className="rounded-lg bg-brand-600 px-4 py-2 text-sm font-semibold text-white transition-colors hover:bg-brand-700"
              >
                立即使用
              </a>
            </div>
          </div>
        </div>

        <div className="mt-10 rounded-xl border border-gray-100 bg-gray-50 p-4 text-sm text-gray-600">
          Android 安装包 SHA-256：
          <code className="ml-1 break-all font-mono text-xs text-gray-800">
            DBFF7BC8D319555CCD71034AE494D127E2C519462F4FCFF72C38962F35F122A0
          </code>
        </div>
      </div>
    </section>
  )
}

function PlatformSection({
  title,
  columns,
  children,
}: {
  title: string
  columns: string
  children: ReactNode
}) {
  return (
    <div className="mt-14">
      <h2 className="mb-6 text-base font-semibold uppercase tracking-wider text-gray-400">{title}</h2>
      <div className={`grid grid-cols-1 gap-6 ${columns}`}>{children}</div>
    </div>
  )
}

function PlatformCard({
  name,
  desc,
  icon: Icon,
  url,
}: {
  name: string
  desc: string
  icon: ComponentType<{ className?: string; 'aria-hidden'?: boolean }>
  url?: string
}) {
  return (
    <div className="flex items-center gap-4 rounded-2xl border border-gray-100 bg-white p-6 shadow-sm transition-shadow hover:shadow-md">
      <Icon className="size-8 shrink-0 text-brand-600" aria-hidden />
      <div className="min-w-0 flex-1">
        <h3 className="text-lg font-semibold text-gray-900">{name}</h3>
        <p className="text-sm text-gray-500">{desc}</p>
      </div>
      {url ? (
        <a
          href={url}
          className="rounded-lg bg-brand-600 px-4 py-2 text-sm font-semibold text-white transition-colors hover:bg-brand-700"
        >
          下载
        </a>
      ) : (
        <span className="rounded-lg bg-gray-100 px-4 py-2 text-sm font-semibold text-gray-400">敬请期待</span>
      )}
    </div>
  )
}
