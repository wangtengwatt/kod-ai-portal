/**
 * 官网全局配置。
 *
 * 所有环境相关的值通过 VITE_* 环境变量注入，
 * 构建时由 Rsbuild 的 loadEnv 读取（见 rsbuild.config.ts）。
 */

/** KOD Web 端地址（用于导航栏"立即使用"、下载页 Web 版链接等）。 */
export const KOD_WEB_URL = import.meta.env.VITE_KOD_WEB_URL || ''

/** 后端 API 基础地址。 */
export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'
