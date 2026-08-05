/**
 * 共享 HTTP 客户端。
 *
 * 所有 API 模块通过此客户端与后端通信。
 * - 自动从 authStore 读取 token 并附加 Authorization 头
 * - 统一解析后端 Result<T> 信封：code === 0 视为成功，否则抛出 ApiError
 * - 网络异常统一包装为 ApiError(0, ...)
 */

import { authStore } from '../stores/auth'

/* ------------------------------------------------------------------ */
/*  类型                                                                */
/* ------------------------------------------------------------------ */

/** 后端统一响应结构。 */
interface ApiResult<T> {
  code: number
  message: string
  data: T
}

/** API 错误：携带后端返回的 code 与 message。 */
export class ApiError extends Error {
  code: number
  constructor(code: number, message: string) {
    super(message)
    this.code = code
    this.name = 'ApiError'
  }
}

/* ------------------------------------------------------------------ */
/*  核心请求方法                                                         */
/* ------------------------------------------------------------------ */

async function request<T>(
  method: 'GET' | 'POST',
  path: string,
  body?: Record<string, unknown>,
  params?: Record<string, string>,
): Promise<T> {
  // 构建 URL（追加查询参数）
  let url = path
  if (params) {
    const qs = new URLSearchParams(params).toString()
    if (qs) url += `?${qs}`
  }

  // 构建请求头
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
  }
  if (authStore.token) {
    headers['Authorization'] = `Bearer ${authStore.token}`
  }

  let resp: Response
  try {
    resp = await fetch(url, {
      method,
      headers,
      body: body ? JSON.stringify(body) : undefined,
      cache: method === 'GET' ? 'no-store' : undefined,
    })
  } catch {
    throw new ApiError(0, '网络异常，请检查网络连接')
  }

  // 非 2xx 响应
  if (!resp.ok) {
    try {
      const err: ApiResult<null> = await resp.json()
      throw new ApiError(err.code || resp.status, err.message || '服务器错误')
    } catch (e) {
      if (e instanceof ApiError) throw e
      throw new ApiError(resp.status, `请求失败 (${resp.status})`)
    }
  }

  // 解析 Result<T> 信封
  const result: ApiResult<T> = await resp.json()
  if (result.code !== 0) {
    throw new ApiError(result.code, result.message || '未知错误')
  }

  return result.data
}

/* ------------------------------------------------------------------ */
/*  便捷方法                                                             */
/* ------------------------------------------------------------------ */

/** GET 请求。 */
export async function get<T>(path: string, params?: Record<string, string>): Promise<T> {
  return request<T>('GET', path, undefined, params)
}

/** POST 请求。 */
export async function post<T>(path: string, body?: Record<string, unknown>): Promise<T> {
  return request<T>('POST', path, body)
}
