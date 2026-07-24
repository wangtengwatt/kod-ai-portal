import { createContext, useCallback, useContext, useState, type ReactNode } from 'react'

/* ------------------------------------------------------------------ */
/*  Auth 状态                                                          */
/* ------------------------------------------------------------------ */

const TOKEN_KEY = 'kod_token'

export interface AuthState {
  token: string | null
  isAuthenticated: boolean
}

/**
 * 模块级可变状态对象。
 *
 * Router 的 context.auth 指向这个对象，beforeLoad 可直接读取最新属性值，
 * 无需 invalidate router。
 *
 * React 层通过 <AuthProvider> 内部的 useState 驱动 UI 重渲染。
 */
export const authStore: AuthState = {
  token: typeof window !== 'undefined' ? localStorage.getItem(TOKEN_KEY) : null,
  isAuthenticated: typeof window !== 'undefined' ? !!localStorage.getItem(TOKEN_KEY) : false,
}

/* ------------------------------------------------------------------ */
/*  React Context                                                      */
/* ------------------------------------------------------------------ */

interface AuthContextValue extends AuthState {
  login: (token: string) => void
  logout: () => void
}

const AuthContext = createContext<AuthContextValue | null>(null)

/** 获取当前认证状态与操作方法。 */
export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth() 必须在 <AuthProvider> 内部使用')
  return ctx
}

/* ------------------------------------------------------------------ */
/*  Provider                                                           */
/* ------------------------------------------------------------------ */

export function AuthProvider({ children }: { children: ReactNode }) {
  const [token, setToken] = useState<string | null>(authStore.token)

  const login = useCallback((t: string) => {
    localStorage.setItem(TOKEN_KEY, t)
    authStore.token = t
    authStore.isAuthenticated = true
    setToken(t)
  }, [])

  const logout = useCallback(() => {
    localStorage.removeItem(TOKEN_KEY)
    authStore.token = null
    authStore.isAuthenticated = false
    setToken(null)
  }, [])

  return (
    <AuthContext.Provider
      value={{ token, isAuthenticated: !!token, login, logout }}
    >
      {children}
    </AuthContext.Provider>
  )
}
