/**
 * 钱包/支付 API 层。
 *
 * 对标 new-api 钱包接口，字段名与 new-api 保持一致。
 */

/** 后端统一响应结构。 */
interface ApiResult<T> {
  code: number
  message: string
  data: T
}

/* ------------------------------------------------------------------ */
/*  接口错误                                                            */
/* ------------------------------------------------------------------ */

export class ApiError extends Error {
  code: number
  constructor(code: number, message: string) {
    super(message)
    this.code = code
    this.name = 'ApiError'
  }
}

/* ------------------------------------------------------------------ */
/*  类型定义                                                            */
/* ------------------------------------------------------------------ */

export interface PayMethod {
  name: string
  type: string
  color: string
  min_topup: string
}

export interface TopUpInfo {
  enable_online_topup: boolean
  enable_stripe_topup: boolean
  enable_creem_topup: boolean
  enable_waffo_topup: boolean
  enable_waffo_pancake_topup: boolean
  payment_compliance_confirmed: boolean
  payment_compliance_terms_version: string
  min_topup: number
  stripe_min_topup: number
  waffo_min_topup: number
  waffo_pancake_min_topup: number
  amount_options: number[]
  discount: Record<string, number>
  pay_methods: PayMethod[]
  topup_link: string
  waffo_pay_methods: PayMethod[] | null
  creem_products: string
}

export interface PayResponse {
  orderNo: string
  paymentUrl: string
}

export interface TopUpItem {
  id: number
  user_id: number
  amount: number
  money: number
  trade_no: string
  payment_method: string
  payment_provider: string
  create_time: number
  complete_time: number
  status: string
}

export interface TopUpPage {
  items: TopUpItem[]
  total: number
  page: number
  page_size: number
}

export interface WalletInfo {
  balance: number
  historical_consumption: number
}

/* ------------------------------------------------------------------ */
/*  请求封装                                                            */
/* ------------------------------------------------------------------ */

async function get<T>(path: string, token: string, params?: Record<string, string>): Promise<T> {
  const url = path + (params ? '?' + new URLSearchParams(params).toString() : '')

  let resp: Response
  try {
    resp = await fetch(url, {
      method: 'GET',
      headers: { Authorization: `Bearer ${token}` },
      cache: 'no-store',
    })
  } catch {
    throw new ApiError(0, '网络异常，请检查网络连接')
  }

  if (!resp.ok) {
    let message = `请求失败 (${resp.status})`
    try {
      const err = await resp.json()
      if (err.message) message = err.message
    } catch { /* ignore */ }
    throw new ApiError(resp.status, message)
  }

  const result: ApiResult<T> = await resp.json()
  if (result.code !== 0) {
    throw new ApiError(result.code, result.message || '请求失败')
  }
  return result.data
}

async function post<T>(path: string, token: string, body?: Record<string, unknown>): Promise<T> {
  let resp: Response
  try {
    resp = await fetch(path, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${token}`,
      },
      body: body ? JSON.stringify(body) : undefined,
    })
  } catch {
    throw new ApiError(0, '网络异常，请检查网络连接')
  }

  const result: ApiResult<T> = await resp.json()
  if (result.code !== 0) {
    throw new ApiError(result.code, result.message || '请求失败')
  }
  return result.data
}

/* ------------------------------------------------------------------ */
/*  API 方法                                                            */
/* ------------------------------------------------------------------ */

/** 获取充值配置信息。 */
export async function getTopUpInfo(token: string): Promise<TopUpInfo> {
  return get<TopUpInfo>('/api/user/topup/info', token)
}

/** 计算充值金额。 */
export async function calculateAmount(token: string, amount: number): Promise<string> {
  return post<string>('/api/user/amount', token, { amount })
}

/** 发起支付。 */
export async function createPay(token: string, amount: number, paymentMethod: string): Promise<PayResponse> {
  return post<PayResponse>('/api/user/pay', token, { amount, payment_method: paymentMethod })
}

/** 获取充值记录。 */
export async function getTopUpHistory(
  token: string,
  page: number,
  pageSize: number,
): Promise<TopUpPage> {
  return get<TopUpPage>('/api/user/topup/self', token, {
    p: String(page),
    page_size: String(pageSize),
  })
}

/** 获取钱包余额。 */
export async function getWallet(token: string): Promise<WalletInfo> {
  return get<WalletInfo>('/api/user/wallet', token)
}
