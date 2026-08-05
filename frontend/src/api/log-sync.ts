/**
 * 日志同步 API —— 启动 / 停止 / 状态查询。
 */

import { get, post } from './client'

/* ------------------------------------------------------------------ */
/*  API 方法                                                            */
/* ------------------------------------------------------------------ */

/** 启动当前用户的日志同步轮询线程。 */
export function startSync(): Promise<{ running: true; message: string }> {
  return post<{ running: true; message: string }>('/api/log/sync/start')
}

/** 停止当前用户的日志同步轮询线程。 */
export function stopSync(): Promise<{ running: false; message: string }> {
  return post<{ running: false; message: string }>('/api/log/sync/stop')
}

/** 查询日志同步状态（公开接口）。 */
export function getSyncStatus(): Promise<{ syncing: boolean }> {
  return get<{ syncing: boolean }>('/api/log/sync/status')
}
