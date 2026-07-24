/**
 * Auth API 层：调用后端 /api/auth/login 接口。
 *
 * 后端将登录与注册合并为一个接口：
 * - 用户已存在 → 校验密码，返回 token（inviteCode 不生效）
 * - 用户不存在 → 校验邀请码，注册并返回 token
 */

/** 后端统一响应结构（Result<T>）。 */
interface ApiResult<T> {
  code: number
  message: string
  data: T
}

/** 登录/注册成功后返回的 data 载荷。 */
export interface AuthPayload {
  token: string
  newUser: boolean
}

/** 自定义错误，携带后端返回的 code 与 message。 */
export class AuthError extends Error {
  code: number
  constructor(code: number, message: string) {
    super(message)
    this.code = code
    this.name = 'AuthError'
  }
}

/**
 * 通用请求封装：调用后端统一 Result<T> 格式接口。
 * code === 0 视为成功，否则抛出 AuthError。
 */
async function request<T>(url: string, body: Record<string, unknown>): Promise<T> {
  let resp: Response
  try {
    resp = await fetch(url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    })
  } catch {
    throw new AuthError(0, '网络异常，请检查网络连接')
  }

  if (!resp.ok) {
    // HTTP 状态错误（如 500）但可能仍返回 Result JSON
    try {
      const err: ApiResult<null> = await resp.json()
      throw new AuthError(err.code || resp.status, err.message || '服务器错误')
    } catch (e) {
      if (e instanceof AuthError) throw e
      throw new AuthError(resp.status, `请求失败 (${resp.status})`)
    }
  }

  const result: ApiResult<T> = await resp.json()
  if (result.code !== 0) {
    throw new AuthError(result.code, result.message || '未知错误')
  }

  return result.data
}

/**
 * 登录：只传邮箱和密码（不传邀请码）。
 * 适用于已有账号的用户。
 */
export async function loginApi(email: string, password: string): Promise<AuthPayload> {
  return request<AuthPayload>('/api/auth/login', { email, password })
}

/**
 * 注册：同时传递邮箱、密码、邀请码和邮箱验证码。
 * 邀请码由后端校验并关联中转站，验证码由后端校验邮箱所有权。
 */
export async function registerApi(
  email: string,
  password: string,
  inviteCode: string,
  emailCode: string,
): Promise<AuthPayload> {
  return request<AuthPayload>('/api/auth/login', {
    email,
    password,
    inviteCode,
    emailCode,
  })
}

/**
 * 发送邮箱验证码。
 * 后端限制同一邮箱 60 秒内只能发送一次，验证码 5 分钟有效。
 */
export async function sendCodeApi(email: string): Promise<void> {
  return request<void>('/api/auth/send-code', { email })
}
