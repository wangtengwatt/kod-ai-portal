/**
 * 中转站配置 API —— 中转站列表 / API Key 管理。
 */

import { get, post } from './client'

/* ------------------------------------------------------------------ */
/*  类型                                                                */
/* ------------------------------------------------------------------ */

/** 中转站条目（GET /api/relay-station/list 返回元素）。 */
export interface StationItem {
  id: number
  url: string
  createTime: string
}

/** API Key 条目（GET /api/relay-station/{stationId}/keys 返回元素，后端 @JsonProperty snake_case）。 */
export interface ApiKeyItem {
  id: number
  station_id: number
  api_key: string
  status: number // 0=idle, 1=in-use
  create_time: string
}

/** 当前用户关联的中转站配置（GET /api/relay-station/config 返回）。 */
export interface RelayConfig {
  url: string
  apiKey: string
}

/* ------------------------------------------------------------------ */
/*  API 方法                                                            */
/* ------------------------------------------------------------------ */

/** 获取所有中转站列表（公开接口）。 */
export function listStations(): Promise<StationItem[]> {
  return get<StationItem[]>('/api/relay-station/list')
}

/** 获取指定中转站的 API Key 列表（公开接口）。 */
export function getStationKeys(stationId: number): Promise<ApiKeyItem[]> {
  return get<ApiKeyItem[]>(`/api/relay-station/${stationId}/keys`)
}

/** 获取当前用户关联的中转站配置（需认证）。 */
export function getConfig(): Promise<RelayConfig> {
  return get<RelayConfig>('/api/relay-station/config')
}

/** 保存/更新当前用户关联中转站的 API Key（需认证）。 */
export function saveApiKey(apiKey: string): Promise<void> {
  return post<void>('/api/relay-station/api-key', { apiKey })
}
