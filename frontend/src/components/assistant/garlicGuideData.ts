export type GuideRoute =
  | '/'
  | '/kai'
  | '/features'
  | '/download'
  | '/changelog'
  | '/kod-ai-services-faqs'
  | '/feedback'
  | '/login'
  | '/register'
  | '/console'
  | '/console/wallet'
  | '/console/logs'

export interface GuideAction {
  label: string
  to: GuideRoute
}

export interface GuideReply {
  content: string
  actions?: GuideAction[]
}

interface GuideKnowledge extends GuideReply {
  keywords: string[]
  routes?: string[]
}

interface RouteGuide {
  intro: string
  suggestions: string[]
}

const defaultGuide: RouteGuide = {
  intro: '你好，我是蒜宝。我可以带你了解 KOD蒜粒、KAI 期算、产品特性、下载方式和 API Key。',
  suggestions: ['KOD蒜粒是什么？', 'KAI 期算是什么？', '怎么开始使用？'],
}

const routeGuides: Record<string, RouteGuide> = {
  '/': {
    intro: '欢迎来到 KOD蒜粒！我是蒜宝，可以帮你快速找到产品、下载和 KAI 期算相关信息。',
    suggestions: ['KOD蒜粒是什么？', 'KAI 期算是什么？', '怎么免费下载？'],
  },
  '/kai': {
    intro: '正在了解 KAI 期算吗？我可以解释标准合约、蒜粒交割和它能解决的问题。',
    suggestions: ['KAI 期算是什么？', '什么是 TPM·h？', '适合企业采购吗？'],
  },
  '/features': {
    intro: '这里是产品特性页。我可以帮你梳理 KOD蒜粒的主要能力和适用场景。',
    suggestions: ['有哪些核心功能？', '适合企业采购吗？', '怎么开始使用？'],
  },
  '/download': {
    intro: '准备开始使用 KOD蒜粒？我可以帮你选择下载方式或进入 Web 端。',
    suggestions: ['支持哪些平台？', '怎么免费下载？', '可以直接用 Web 版吗？'],
  },
  '/changelog': {
    intro: '这里记录 KOD蒜粒的版本变化。你也可以问我产品入口和常见问题。',
    suggestions: ['最近更新了什么？', '有哪些核心功能？', '去哪里提建议？'],
  },
  '/kod-ai-services-faqs': {
    intro: '这里是常见问题页。如果条目比较多，可以直接告诉我你想找什么。',
    suggestions: ['怎么开始使用？', 'API Key 在哪里？', '去哪里提建议？'],
  },
  '/feedback': {
    intro: '感谢你愿意提供反馈。我可以帮你确认反馈入口，也可以继续解答产品问题。',
    suggestions: ['怎么提交反馈？', '遇到问题怎么办？', '返回常见问题'],
  },
  '/login': {
    intro: '登录后可以进入控制台并管理 API Key。遇到账号问题也可以问我。',
    suggestions: ['登录后能做什么？', 'API Key 在哪里？', '还没有账号怎么办？'],
  },
  '/register': {
    intro: '正在创建账号？完成注册后就能进入控制台并配置服务。',
    suggestions: ['注册需要什么？', '登录后能做什么？', '遇到问题怎么办？'],
  },
}

const knowledge: GuideKnowledge[] = [
  {
    keywords: ['kod', '蒜粒', '是什么', '介绍'],
    content: 'KOD蒜粒是深度集成 KAI 期算标准的 AI 工作台，把模型容量的采购、交付、管理与实际使用连接在一起。',
    actions: [
      { label: '查看产品特性', to: '/features' },
      { label: '免费下载', to: '/download' },
    ],
  },
  {
    keywords: ['kai', '期算', '标准合约', '交割'],
    routes: ['/kai'],
    content: 'KAI 期算把模型版本、容量、交割时段与区域定义成可比较的标准，让企业能够提前锁定 AI 模型容量和成本。',
    actions: [{ label: '阅读 KAI 白皮书', to: '/kai' }],
  },
  {
    keywords: ['tpm', 'tpm·h', '容量单位', '每手'],
    content: 'TPM 表示每分钟可处理的 Token 数，TPM·h 用来描述某一小时内可交付的模型吞吐容量。KAI 标准以它作为容量合约的重要计量方式。',
    actions: [{ label: '了解 KAI 期算', to: '/kai' }],
  },
  {
    keywords: ['下载', '免费', '安装', '平台', 'windows', 'mac', '安卓', 'ios'],
    routes: ['/download'],
    content: 'KOD蒜粒提供多平台使用入口。你可以前往下载页查看桌面端、移动端和 Web 版的最新可用方式。',
    actions: [{ label: '前往下载页', to: '/download' }],
  },
  {
    keywords: ['web', '网页版', '浏览器', '直接用'],
    content: '可以。前往下载页查看 Web 版入口；如果尚未登录，系统会先引导你完成登录。',
    actions: [
      { label: '查看使用方式', to: '/download' },
      { label: '登录', to: '/login' },
    ],
  },
  {
    keywords: ['api', 'key', '密钥', '令牌', 'token'],
    content: '登录后可进入控制台查看和管理接入信息。请不要在公开页面或聊天中分享你的密钥。',
    actions: [
      { label: '进入控制台', to: '/console' },
      { label: '先登录', to: '/login' },
    ],
  },
  {
    keywords: ['企业', '团队', '采购', '供应商', '适合'],
    content: 'KOD蒜粒适合需要稳定模型容量、明确预算与多供应商保障的企业，也为容量供应商提供标准化交付和需求对接能力。',
    actions: [
      { label: '了解产品能力', to: '/features' },
    ],
  },
  {
    keywords: ['功能', '能力', '能做什么', '核心'],
    routes: ['/features'],
    content: '核心能力包括蒜粒期货交易、分时确定性交付、多供应商竞价，以及连接桌面端、移动端和 Web 端的 AI 智能工作台。',
    actions: [{ label: '查看全部特性', to: '/features' }],
  },
  {
    keywords: ['开始', '使用', '入门', '登录后'],
    content: '最快的开始方式是先下载 KOD蒜粒或登录 Web 端；登录后还可以进入控制台管理服务和 API Key。',
    actions: [
      { label: '免费下载', to: '/download' },
      { label: '立即登录', to: '/login' },
    ],
  },
  {
    keywords: ['注册', '账号', '没有账号', '验证码', '邀请码'],
    content: '新用户可从注册页创建账号。请按页面提示准备邮箱验证码及所需注册信息；已有账号可以直接登录。',
    actions: [
      { label: '创建账号', to: '/register' },
      { label: '已有账号登录', to: '/login' },
    ],
  },
  {
    keywords: ['反馈', '建议', '问题', '故障', '联系', '怎么办'],
    routes: ['/feedback'],
    content: '你可以通过意见反馈页描述问题或建议，也可以先查看常见问题，通常能更快找到答案。',
    actions: [
      { label: '提交反馈', to: '/feedback' },
      { label: '查看常见问题', to: '/kod-ai-services-faqs' },
    ],
  },
  {
    keywords: ['更新', '版本', '最近', '日志'],
    routes: ['/changelog'],
    content: '更新日志会记录 KOD蒜粒的功能迭代、品牌升级与体验改进，你可以前往版本页查看完整记录。',
    actions: [{ label: '查看更新日志', to: '/changelog' }],
  },
]

function normalize(value: string) {
  return value.toLowerCase().replace(/[\s，。？！、,.?!]/g, '')
}

export function getRouteGuide(pathname: string): RouteGuide {
  return routeGuides[pathname] ?? defaultGuide
}

export function resolveGuideReply(input: string, pathname: string): GuideReply {
  const normalizedInput = normalize(input)
  let bestMatch: GuideKnowledge | undefined
  let bestScore = 0

  for (const item of knowledge) {
    const keywordScore = item.keywords.reduce(
      (score, keyword) => score + (normalizedInput.includes(normalize(keyword)) ? 1 : 0),
      0,
    )
    const routeBonus = item.routes?.some((route) => pathname.startsWith(route)) ? 0.35 : 0
    const score = keywordScore + routeBonus

    if (keywordScore > 0 && score > bestScore) {
      bestScore = score
      bestMatch = item
    }
  }

  if (bestMatch) {
    return { content: bestMatch.content, actions: bestMatch.actions }
  }

  return {
    content: '这个问题我暂时还不能准确回答。你可以换个说法，或直接从常见问题和反馈入口继续查找。',
    actions: [
      { label: '查看常见问题', to: '/kod-ai-services-faqs' },
      { label: '提交反馈', to: '/feedback' },
    ],
  }
}
