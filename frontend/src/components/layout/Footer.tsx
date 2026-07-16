/** 官网页脚：品牌信息与栏目链接。 */
export function Footer() {
  return (
    <footer className="border-t border-gray-100 bg-gray-50">
      <div className="mx-auto max-w-6xl px-6 py-12">
        <div className="flex flex-col justify-between gap-8 md:flex-row">
          <div className="max-w-xs">
            <div className="flex items-center gap-2">
              <span className="flex h-8 w-8 items-center justify-center rounded-lg bg-brand-600 font-bold text-white">
                k
              </span>
              <span className="text-xl font-bold text-gray-900">kod</span>
            </div>
            <p className="mt-4 text-sm text-gray-500">
              一款好用的 AI 助手客户端，让 AI 触手可及。
            </p>
          </div>

          <div className="grid grid-cols-2 gap-12 text-sm sm:grid-cols-3">
            <div>
              <h4 className="font-semibold text-gray-900">产品</h4>
              <ul className="mt-3 space-y-2 text-gray-500">
                <li>产品特性</li>
                <li>下载</li>
                <li>定价</li>
              </ul>
            </div>
            <div>
              <h4 className="font-semibold text-gray-900">资源</h4>
              <ul className="mt-3 space-y-2 text-gray-500">
                <li>使用文档</li>
                <li>更新日志</li>
                <li>常见问题</li>
              </ul>
            </div>
            <div>
              <h4 className="font-semibold text-gray-900">关于</h4>
              <ul className="mt-3 space-y-2 text-gray-500">
                <li>关于我们</li>
                <li>联系我们</li>
                <li>隐私政策</li>
              </ul>
            </div>
          </div>
        </div>

        <div className="mt-10 border-t border-gray-200 pt-6 text-center text-xs text-gray-400">
          © {new Date().getFullYear()} kod. 基于开源项目 chatbox 二次研发。
        </div>
      </div>
    </footer>
  )
}
