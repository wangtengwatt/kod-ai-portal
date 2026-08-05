/**
 * 钱包 & 支付 API —— 余额 / 充值 / 交易记录。
 */

import { get, post } from './client'

/* ------------------------------------------------------------------ */
/*  类型                                                                */
/* ------------------------------------------------------------------ */

/** 充值配置（GET /api/user/topup/info 返回，后端 @JsonProperty snake_case）。 */
export interface TopUpInfo {
  enable_online_topup: boolean
  enable_stripe_topup: boolean
  enable_creem_topup: boolean
  enable_waffo_topup: boolean
  enable_waffo_pancake_topup: boolean
  payment_compliance_confirmed: boolean
  payment_compliance_terms_version: string | null
  min_topup: number
  stripe_min_topup: number
  waffo_min_topup: number
  waffo_pancake_min_topup: number
  amount_options: number[]
  discount: Record<string, number> | null
  pay_methods: string[]
  topup_link: string | null
  waffo_pay_methods: string[]
  creem_products: unknown[]
}

/** 钱包信息（GET /api/user/wallet 返回，后端 @JsonProperty snake_case）。 */
export interface WalletInfo {
  balance: number
  historical_consumption: number
}

/** 充值记录条目（后端 @JsonProperty snake_case）。 */
export interface TopUpItem {
  id: number
  user_id: number
  amount: number
  money: string
  trade_no: string
  payment_method: string
  payment_provider: string
  create_time: string
  complete_time: string | null
  status: string
}

/** 充值记录分页（后端 @JsonProperty snake_case）。 */
export interface TopUpPage {
  items: TopUpItem[]
  total: number
  page: number
  page_size: number
}

/** 支付响应。 */
export interface PayResponse {
  orderNo: string
  paymentUrl: string
}

/* ------------------------------------------------------------------ */
/*  API 方法                                                            */
/* ------------------------------------------------------------------ */

/** 获取充值配置信息。 */
export function getTopUpInfo(): Promise<TopUpInfo> {
  return get<TopUpInfo>('/api/user/topup/info')
}

/** 获取钱包余额。 */
export function getWallet(): Promise<WalletInfo> {
  return get<WalletInfo>('/api/user/wallet')
}

/** 计算实际支付金额（含折扣）。 */
export function calculateAmount(amount: number): Promise<string> {
  return post<string>('/api/user/amount', { amount })
}

/** 创建支付订单，返回支付跳转 URL。 */
export function createPay(amount: number, paymentMethod: string): Promise<PayResponse> {
  return post<PayResponse>('/api/user/pay', {
    amount,
    payment_method: paymentMethod,
  })
}

/** 分页查询充值记录。 */
export function getTopUpHistory(page: number, pageSize = 10): Promise<TopUpPage> {
  return get<TopUpPage>('/api/user/topup/self', {
    p: String(page),
    pageSize: String(pageSize),
  })
}
