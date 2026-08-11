import path from 'node:path'
import { fileURLToPath } from 'node:url'

import { defineConfig, loadEnv } from '@rsbuild/core'
import { pluginReact } from '@rsbuild/plugin-react'
import { pluginTailwindcss } from '@rsbuild/plugin-tailwindcss'
import { tanstackRouter } from '@tanstack/router-plugin/rspack'

const __dirname = path.dirname(fileURLToPath(import.meta.url))

/**
 * Rsbuild 配置：
 * - React 插件
 * - TanStack Router 文件式路由（rspack 插件，构建时生成 src/routeTree.gen.ts）
 * - 开发环境将 /api 代理到后端 Spring Boot 服务
 * - TailwindCSS v4 通过 @rsbuild/plugin-tailwindcss 接入
 */
export default defineConfig(({ envMode }) => {
  const env = loadEnv({ mode: envMode, prefixes: ['VITE_'] })
  const serverUrl =
    process.env.VITE_API_BASE_URL ||
    env.rawPublicVars.VITE_API_BASE_URL ||
    'http://localhost:8080'

  const isProd = envMode === 'production'

  return {
    plugins: [pluginReact(), pluginTailwindcss({ optimize: false })],
    source: {
      entry: {
        index: './src/main.tsx',
      },
      define: {
        'import.meta.env.VITE_API_BASE_URL': JSON.stringify(serverUrl),
        'import.meta.env.VITE_KOD_WEB_URL': JSON.stringify(
          process.env.VITE_KOD_WEB_URL ||
            env.parsed?.VITE_KOD_WEB_URL ||
            ''
        ),
      },
    },
    resolve: {
      alias: {
        '@': path.resolve(__dirname, './src'),
      },
    },
    html: {
      template: './index.html',
    },
    server: {
      host: '0.0.0.0',
      strictPort: false,
      proxy: {
        '/api': { target: serverUrl, changeOrigin: true },
      },
    },
    output: {
      minify: isProd,
      target: 'web',
      distPath: {
        root: 'dist',
      },
    },
    tools: {
      rspack: {
        plugins: [
          tanstackRouter({
            target: 'react',
            autoCodeSplitting: isProd,
          }),
        ],
      },
    },
  }
})
