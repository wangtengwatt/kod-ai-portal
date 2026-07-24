import { Link } from '@tanstack/react-router'

export function Footer() {
  return (
    <footer className="border-t border-gray-100 bg-white">
      <div className="mx-auto max-w-6xl px-6 py-12">
        <div className="flex flex-col justify-between gap-8 md:flex-row">
          <div className="max-w-xs">
            <Link to="/" className="flex items-center gap-2">
              <span className="flex h-8 w-8 items-center justify-center rounded-lg bg-brand-600 font-bold text-white">
                k
              </span>
              <span className="text-xl font-bold text-gray-900">KOD</span>
            </Link>
            <p className="mt-4 text-sm leading-relaxed text-gray-500">
              一款好用的 AI 助手客户端，让 AI 触手可及。
            </p>
          </div>

          <div className="grid grid-cols-2 gap-12 text-sm sm:grid-cols-3">
            <div>
              <h4 className="font-semibold text-gray-900">产品</h4>
              <ul className="mt-3 space-y-2 text-gray-500">
                <li><Link to="/features" className="hover:text-brand-600">产品特性</Link></li>
                <li><Link to="/download" className="hover:text-brand-600">下载</Link></li>
                <li><Link to="/pricing" className="hover:text-brand-600">定价</Link></li>
              </ul>
            </div>
            <div>
              <h4 className="font-semibold text-gray-900">资源</h4>
              <ul className="mt-3 space-y-2 text-gray-500">
                <li><Link to="/changelog" className="hover:text-brand-600">更新日志</Link></li>
                <li><Link to="/kod-ai-services-faqs" className="hover:text-brand-600">常见问题</Link></li>
                <li><Link to="/jsjsubmit" className="hover:text-brand-600">问卷调查</Link></li>
              </ul>
            </div>
            <div>
              <h4 className="font-semibold text-gray-900">加入我们</h4>
              <ul className="mt-3 space-y-2 text-gray-500">
                <li><Link to="/feedback" className="hover:text-brand-600">意见反馈</Link></li>
                <li><a href="mailto:watt@vn.com" className="hover:text-brand-600">watt@vn.com</a></li>
              </ul>
            </div>
          </div>
        </div>

        <div className="mt-10 border-t border-gray-100 pt-6 text-center text-xs text-gray-400">
          © {new Date().getFullYear()} KOD. 保留所有权利。
        </div>
      </div>
    </footer>
  )
}
