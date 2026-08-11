/**
 * 钱包 & 支付 API —— 余额 / 充值 / 交易记录。
 *
 * 与后端 PaymentController / PaymentCallbackController 对齐。
 */

import { get, post } from './client'

/* ------------------------------------------------------------------ */
/*  类型（与后端 DTO @JsonProperty snake_case 严格对齐）                  */
/* ------------------------------------------------------------------ */

/** 支付方式（后端 TopUpInfoResponse.PayMethod）。 */
export interface PayMethod {
  name: string
  type: string
  color: string
  min_topup: string
}

/** 充值配置（GET /api/user/topup/info）。 */
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
  pay_methods: PayMethod[]
  topup_link: string | null
  waffo_pay_methods: PayMethod[] | null
  creem_products: string
}

/** 钱包余额（GET /api/user/wallet）。 */
export interface WalletInfo {
  balance: number
  historical_consumption: number
}

/** 充值记录条目（后端 TopUpItemResponse）。 */
export interface TopUpItem {
  id: number
  user_id: number
  amount: number
  money: number
  trade_no: string
  payment_method: string
  payment_provider: string
  create_time: number   // unix epoch seconds
  complete_time: number  // 0 = 未完成
  status: string
}

/** 订单详情（后端 OrderStatusResponse，对应 orders 表全部字段）。 */
export interface OrderDetail {
  id: number
  user_id: number
  product_name: string
  payment_method: string
  payment_provider: string
  amount: number
  money: number
  order_no: string
  status: string
  coupon_id: number | null
  create_time: number
  update_time: number
  complete_time: number
}

/** 充值记录分页（后端 TopUpPageResponse）。 */
export interface TopUpPage {
  items: TopUpItem[]
  total: number
  page: number
  page_size: number
}

/** 支付响应（POST /api/user/pay）。 */
export interface PayResponse {
  orderNo: string
  paymentUrl: string
  qrcode: string | null
}

/* ------------------------------------------------------------------ */
/*  API 方法                                                            */
/* ------------------------------------------------------------------ */

/** 获取充值配置信息（无需认证）。 */
export function getTopUpInfo(): Promise<TopUpInfo> {
  return get<TopUpInfo>('/api/user/topup/info')
}

/** 获取当前用户钱包余额。 */
export function getWallet(): Promise<WalletInfo> {
  return get<WalletInfo>('/api/user/wallet')
}

/** 计算实际支付金额（含折扣），返回保留两位小数的字符串。 */
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

/** 分页查询当前用户的充值记录。 */
export function getTopUpHistory(page: number, pageSize = 10): Promise<TopUpPage> {
  return get<TopUpPage>('/api/user/topup/self', {
    p: String(page),
    pageSize: String(pageSize),
  })
}

/** 查询单笔订单状态（用于支付后轮询）。 */
export function getOrderStatus(orderNo: string): Promise<OrderDetail> {
  return get<OrderDetail>(`/api/user/order/${orderNo}`)
}
