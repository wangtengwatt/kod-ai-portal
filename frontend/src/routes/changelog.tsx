import { createFileRoute } from '@tanstack/react-router'

/** 更新日志页（/changelog）：按版本展示更新内容。 */
export const Route = createFileRoute('/changelog')({
  component: ChangelogPage,
})

const releases = [
  {
    version: 'v0.1.0',
    date: '2026-07-16',
    items: ['官网上线：首页、产品特性、下载、定价', '项目骨架初始化'],
  },
]

function ChangelogPage() {
  return (
    <section className="mx-auto max-w-3xl px-6 py-20">
      <h1 className="text-4xl font-bold text-gray-900">更新日志</h1>
      <p className="mt-4 text-gray-600">记录 kod 的每一次迭代与改进。</p>

      <div className="mt-12 space-y-10">
        {releases.map((r) => (
          <div key={r.version} className="border-l-2 border-brand-100 pl-6">
            <div className="flex items-baseline gap-3">
              <h2 className="text-xl font-semibold text-brand-600">
                {r.version}
              </h2>
              <span className="text-sm text-gray-400">{r.date}</span>
            </div>
            <ul className="mt-3 list-disc space-y-1 pl-5 text-sm text-gray-600">
              {r.items.map((item) => (
                <li key={item}>{item}</li>
              ))}
            </ul>
          </div>
        ))}
      </div>
    </section>
  )
}
