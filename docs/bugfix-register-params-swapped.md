# Bug 修复：注册模块邀请码与验证码参数错位

## 问题概述

`kod-ai-portal-2.0` 注册页面在调用 `registerApi` 时，第 3、第 4 个参数传入顺序与函数签名不匹配，导致**邀请码（inviteCode）和邮箱验证码（emailCode）互换**，注册功能完全不可用。

## 影响范围

- **文件**: `frontend/src/routes/register.tsx`
- **版本**: `kod-ai-portal-2.0`
- **影响**: 所有通过注册页面提交的注册请求均会失败

## 根因分析

| | 1.0（正确） | 2.0（错误） |
|---|---|---|
| **调用行** | `register.tsx:175` | `register.tsx:110` |
| **实际传参** | `registerApi(email, password, inviteCode, code)` | `registerApi(email, password, code, inviteCode)` |
| **第3参数** | `inviteCode` → 传给 `inviteCode` ✓ | `code` → 传给 `inviteCode` ✗ |
| **第4参数** | `code` → 传给 `emailCode` ✓ | `inviteCode` → 传给 `emailCode` ✗ |

`registerApi` 的函数签名在两个版本中完全一致：

```typescript
export async function registerApi(
  email: string,       // 第1参数
  password: string,    // 第2参数
  inviteCode: string,  // 第3参数 — 2.0 错误地传入了 验证码
  emailCode: string,   // 第4参数 — 2.0 错误地传入了 邀请码
): Promise<AuthPayload>
```

### 后端影响

由于参数错位，后端 `POST /api/auth/login` 收到的 JSON 为：

```json
{
  "email": "user@example.com",
  "password": "12345678",
  "inviteCode": "483920",        // ← 实际是 6位验证码
  "emailCode": "KOD-INVITE-XXX"  // ← 实际是 邀请码
}
```

导致两个校验全部失败：

1. **邀请码校验失败** — 后端拿 `"483920"` 去 `relay_station` 表查 `invite_code`，匹配不到，返回 `"邀请码无效"`
2. **邮箱验证码校验失败** — 后端拿 `"KOD-INVITE-XXX"` 去 Redis 查 `kod:code:{email}` 对应的验证码，永远不匹配

## 修复方案

**文件**: `frontend/src/routes/register.tsx`  
**行号**: 110

```diff
- const { token, relayMessage } = await registerApi(email.trim(), password, code.trim(), inviteCode.trim())
+ const { token, relayMessage } = await registerApi(email.trim(), password, inviteCode.trim(), code.trim())
```

将第 3、第 4 参数顺序对调，使 `inviteCode` 对应邀请码、`code` 对应验证码，与函数签名保持一致。

## 验证方式

1. 打开注册页面 `/register`
2. 填写邮箱 → 点击发送验证码 → 输入收到的 6 位验证码
3. 填写密码和有效邀请码
4. 点击注册，应成功跳转至首页（或 `redirect` 指定路径）
5. 确认数据库中 `sys_user` 表新增对应用户记录

## 预防措施

建议在 `registerApi` 调用处使用**对象参数**模式替代位置参数，从源头消除参数顺序风险：

```typescript
// 推荐：对象参数 — 顺序无关，不会错位
export async function registerApi(params: {
  email: string
  password: string
  inviteCode: string
  emailCode: string
}): Promise<AuthPayload> { ... }

// 调用
await registerApi({ email, password, inviteCode, emailCode: code })
```

## 变更记录

| 日期 | 版本 | 说明 |
|---|---|---|
| 2026-08-05 | 2.0 | 修复邀请码与验证码参数错位问题 |
