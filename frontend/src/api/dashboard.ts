/**
 * Dashboard 数据 API —— 用量概览 / 趋势 / 配额查询。
 */

import { get } from './client'

/* ------------------------------------------------------------------ */
/*  类型                                                                */
/* ------------------------------------------------------------------ */

/** 模型汇总（GET /api/dashboard/summary 返回元素）。 */
export interface DashboardSummaryItem {
  modelName: string
  totalRequests: number
  totalQuota: number
  totalPrompt: number
  totalCompletion: number
  totalTokens: number
  totalUseTime: number
  totalStream: number
  lastRequestAt: string | null
}

/** 小时趋势（GET /api/dashboard/hourly 返回元素）。 */
export interface HourlyTrendItem {
  hourBucket: string
  requestCount: number
  quota: number
  tokenUsed: number
  streamCount: number
}

/** 配额数据（GET /api/data/self 返回元素，后端 @JsonProperty snake_case）。 */
export interface QuotaDataItem {
  id: number
  user_id: number
  username: string
  model_name: string
  created_at: number
  token_used: number
  count: number
  quota: number
}

/** 流量配额数据（GET /api/data/flow/self 返回元素，后端 @JsonProperty snake_case）。 */
export interface FlowQuotaDataItem {
  user_id: number
  username: string
  node_name: string
  token_name: string
  use_group: string
  channel_id: number
  channel_name: string
  model_name: string
  token_used: number
  count: number
  quota: number
}

/* ------------------------------------------------------------------ */
/*  API 方法                                                            */
/* ------------------------------------------------------------------ */

/** 获取各模型累计汇总。 */
export function getDashboardSummary(): Promise<DashboardSummaryItem[]> {
  return get<DashboardSummaryItem[]>('/api/dashboard/summary')
}

/** 获取最近 N 小时趋势（默认 24，最大 720）。 */
export function getDashboardHourly(hours = 24): Promise<HourlyTrendItem[]> {
  return get<HourlyTrendItem[]>('/api/dashboard/hourly', {
    hours: String(hours),
  })
}

/** 获取指定时间范围内的配额数据（毫秒时间戳，最大跨度 30 天）。 */
export function getQuotaData(
  startTimestamp: number,
  endTimestamp: number,
): Promise<QuotaDataItem[]> {
  return get<QuotaDataItem[]>('/api/data/self', {
    start_timestamp: String(startTimestamp),
    end_timestamp: String(endTimestamp),
  })
}

/** 获取指定时间范围内的流量配额数据（按渠道分组）。 */
export function getFlowQuotaData(
  startTimestamp: number,
  endTimestamp: number,
): Promise<FlowQuotaDataItem[]> {
  return get<FlowQuotaDataItem[]>('/api/data/flow/self', {
    start_timestamp: String(startTimestamp),
    end_timestamp: String(endTimestamp),
  })
}
