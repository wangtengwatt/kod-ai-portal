/**
 * Auth API 层：调用后端 /api/auth/* 接口。
 *
 * 后端将登录与注册合并为一个接口：
 * - 用户已存在 → 校验密码，返回 token（inviteCode 不生效）
 * - 用户不存在 → 校验邀请码，注册并返回 token
 */

import { post } from './client'

/* ------------------------------------------------------------------ */
/*  类型                                                                */
/* ------------------------------------------------------------------ */

/** 登录/注册成功后返回的 data 载荷。 */
export interface AuthPayload {
  token: string
  newUser: boolean
  /** 中转站同步注册提示：null 表示正常；非空表示 kod 注册成功但中转站同步失败。 */
  relayMessage?: string | null
}

/* ------------------------------------------------------------------ */
/*  API 方法                                                            */
/* ------------------------------------------------------------------ */

/** 登录：只传邮箱和密码（不传邀请码）。 */
export async function loginApi(email: string, password: string): Promise<AuthPayload> {
  return post<AuthPayload>('/api/auth/login', { email, password })
}

/** 注册：传递邮箱、密码、邀请码和邮箱验证码。 */
export async function registerApi(
  email: string,
  password: string,
  inviteCode: string,
  emailCode: string,
): Promise<AuthPayload> {
  return post<AuthPayload>('/api/auth/login', {
    email,
    password,
    inviteCode,
    emailCode,
  })
}

/** 发送邮箱验证码。 */
export async function sendCodeApi(email: string): Promise<void> {
  return post<void>('/api/auth/send-code', { email })
}
