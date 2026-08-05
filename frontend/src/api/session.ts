/**
 * 会话管理 API —— API Key 选择 / 释放 / 余额查询。
 */

import { get, post } from './client'

/* ------------------------------------------------------------------ */
/*  类型                                                                */
/* ------------------------------------------------------------------ */

/** select-key 响应。 */
export interface SelectKeyResult {
  api_key_id: number
  previous_api_key_id: number | null
  url: string
  api_key: string
}

/** release-key 响应。 */
export interface ReleaseKeyResult {
  released_api_key_id: number
}

/** balance 响应。 */
export interface SessionBalance {
  balance: number
  can_chat: boolean
  connect: boolean
  connected_key_id?: number
  connected_api_key?: string
  connected_key_status?: number
  connected_station_id?: number
  connected_station_url?: string
}

/* ------------------------------------------------------------------ */
/*  API 方法                                                            */
/* ------------------------------------------------------------------ */

/** 选择/锁定一个 API Key 用于当前会话。 */
export function selectKey(stationId: number, apiKeyId: number): Promise<SelectKeyResult> {
  return post<SelectKeyResult>('/api/session/select-key', {
    station_id: stationId,
    api_key_id: apiKeyId,
  })
}

/** 释放当前持有的 API Key。 */
export function releaseKey(): Promise<ReleaseKeyResult> {
  return post<ReleaseKeyResult>('/api/session/release-key')
}

/** 查询余额、可聊天状态及当前连接详情。 */
export function getSessionBalance(): Promise<SessionBalance> {
  return get<SessionBalance>('/api/session/balance')
}
