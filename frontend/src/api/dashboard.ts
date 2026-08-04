/**
 * 看板 API 层。
 *
 * 对标 new-api 看板接口，提供 /api/data/self、/api/data/flow/self
 * 以及 /api/dashboard/summary、/api/dashboard/hourly 等端点调用。
 */

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
/*  类型定义 — 对标 new-api                                             */
/* ------------------------------------------------------------------ */

/** 对标 new-api QuotaDataItem。 */
export interface QuotaDataItem {
  id?: number
  user_id?: number
  username?: string
  model_name?: string
  created_at: number
  token_used?: number
  count?: number
  quota?: number
}

/** 对标 new-api FlowQuotaDataItem。 */
export interface FlowQuotaDataItem {
  user_id?: number
  username?: string
  node_name?: string
  token_id?: number
  token_name?: string
  use_group?: string
  channel_id?: number
  channel_name?: string
  model_name?: string
  token_used?: number
  count?: number
  quota?: number
}

/** 模型汇总条目。 */
export interface DashboardSummaryItem {
  modelName: string
  totalRequests: number
  totalQuota: number
  totalPrompt: number
  totalCompletion: number
  totalTokens: number
  totalUseTime: number
  totalStream: number
  lastRequestAt: number
}

/** 小时趋势条目。 */
export interface HourlyTrendItem {
  hourBucket: number
  requestCount: number
  quota: number
  tokenUsed: number
  streamCount: number
}

/* ------------------------------------------------------------------ */
/*  请求封装                                                            */
/* ------------------------------------------------------------------ */

interface ApiResult<T> {
  code: number
  message: string
  data: T
}

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

/* ------------------------------------------------------------------ */
/*  API 方法                                                            */
/* ------------------------------------------------------------------ */

/** 对标 new-api GET /api/data/self — 按时间范围查询用量数据。 */
export async function getUserQuotaDates(
  token: string,
  startTimestamp: number,
  endTimestamp: number,
): Promise<QuotaDataItem[]> {
  return get<QuotaDataItem[]>('/api/data/self', token, {
    start_timestamp: String(startTimestamp),
    end_timestamp: String(endTimestamp),
  })
}

/** 对标 new-api GET /api/data/flow/self — 按时间范围查询流量数据。 */
export async function getFlowQuotaDates(
  token: string,
  startTimestamp: number,
  endTimestamp: number,
): Promise<FlowQuotaDataItem[]> {
  return get<FlowQuotaDataItem[]>('/api/data/flow/self', token, {
    start_timestamp: String(startTimestamp),
    end_timestamp: String(endTimestamp),
  })
}

/** 获取模型用量汇总。 */
export async function getDashboardSummary(token: string): Promise<DashboardSummaryItem[]> {
  return get<DashboardSummaryItem[]>('/api/dashboard/summary', token)
}

/** 获取小时用量趋势。 */
export async function getDashboardHourly(token: string, hours = 24): Promise<HourlyTrendItem[]> {
  return get<HourlyTrendItem[]>('/api/dashboard/hourly', token, { hours: String(hours) })
}
