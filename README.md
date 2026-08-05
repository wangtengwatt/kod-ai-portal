# kod-ai-portal

KOD 官网项目 —— 前后端分离的单仓多项目仓库。

## 目录结构

```
kod-ai-portal/
├── frontend/          # 官网前端（React 19 + Rsbuild + TailwindCSS + TanStack）
├── backend/           # 官网后端（Java 17 + Spring Boot 3 + MyBatis-Plus）
├── document/          # 部署文档
├── docker-compose.yml # Docker Compose 编排
├── .env.example       # 环境变量示例
├── README.md
├── .gitignore
└── .editorconfig
```

## 技术栈

### 前端（`frontend/`）
- **框架**：React 19 + TypeScript
- **构建**：Rsbuild（Rspack）
- **样式**：TailwindCSS v4
- **图标**：Lucide React
- **路由 / 数据**：TanStack Router（文件式路由）+ TanStack Query
- **鉴权**：JWT Token（React Context + localStorage）
- **代码检查**：oxlint

### 后端（`backend/`）
- **语言 / 框架**：Java 17 + Spring Boot 3.3
- **ORM / 连接池**：MyBatis-Plus（Spring Boot 3 starter）+ Druid（懒加载）
- **数据库**：MySQL 8
- **缓存**：Redis（Lettuce）
- **鉴权**：JWT（jjwt）+ BCrypt
- **邮件服务**：邮箱验证码发送与校验
- **构建**：Maven
- **包结构**：`com.kod` 下 `controller` / `service` / `mapper` / `entity` / `config` / `common` / `dto` / `util` 分层

## 环境要求

- **Node.js** ≥ 20.19（推荐 22.12.x），npm ≥ 10
- **JDK** ≥ 17（已在 JDK 21 验证）
- **Maven** ≥ 3.9
- **MySQL** 8（外部实例，默认 `18.139.134.29:3306`）
- **Redis** 7（外部实例，默认 `18.139.134.29:6379`）

## 本地启动

### 前置准备

```bash
# 确保使用项目自带的 Node.js（端口 3000 要求 Node ≥ 20.19）
export PATH="/d/kod/node-v22.12.0-win-x64:$PATH"

# 确认 MySQL 和 Redis 可访问
mysql -h18.139.134.29 -P3306 -uaiex_dev -p kod
redis-cli -h 18.139.134.29 -p 6379 -a <password>
```

### 前端

```bash
cd frontend
npm install
npm run dev          # 启动开发服务，默认 http://localhost:3000
npm run build        # 生产构建，产物在 dist/
```

环境变量（`frontend/.env`，参考 `.env.example`）：

| 变量 | 说明 | 默认值 |
|------|------|--------|
| `VITE_API_BASE_URL` | 后端 API 地址 | `http://localhost:8080` |
| `VITE_KOD_WEB_URL` | KOD Web 端地址（导航栏和下载页链接） | 空 |

### 后端

```bash
cd backend
mvn spring-boot:run                              # 启动（dev 环境）
java -jar target/kod-portal-backend-0.1.0.jar    # 或运行 jar

# 健康检查
curl http://localhost:8080/api/health
```

邮件服务配置（dev 环境通过环境变量注入，Zoho 邮箱示例）：

```bash
MAIL_USERNAME=eudora@vn.com MAIL_PASSWORD=<密码> mvn spring-boot:run
```

全部环境变量：

| 环境变量 | 说明 |
| -------- | ---- |
| `MYSQL_HOST` / `MYSQL_PORT` / `MYSQL_DATABASE` / `MYSQL_USERNAME` / `MYSQL_PASSWORD` | MySQL 连接 |
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD` | Redis 连接 |
| `JWT_SECRET` / `JWT_EXPIRE_MILLIS` | JWT 密钥与有效期 |
| `MAIL_HOST` / `MAIL_PORT` / `MAIL_USERNAME` / `MAIL_PASSWORD` | SMTP 邮件（默认 Zoho） |

## 后端 API

统一响应：`{ "code": 0, "message": "success", "data": ... }`（code=0 成功，非 0 错误）。

| 方法 | 路径 | 说明 | 鉴权 |
| ---- | ---- | ---- | ---- |
| GET | `/api/health` | 健康检查 | 无 |
| POST | `/api/relay-station` | 保存 AI 中转站 | 无 |
| POST | `/api/auth/login` | 登录：邮箱 + 密码，返回 JWT | 无 |
| POST | `/api/auth/send-code` | 发送邮箱验证码 | 无 |
| GET | `/api/relay-station/config` | 获取当前用户中转站配置 | Bearer Token |

> 注册逻辑合并进 `/api/auth/login`：用户不存在时需同时传入 `inviteCode` 和 `emailCode`，后端校验邀请码并注册。

## 页面路由

| 路由 | 页面 | 说明 |
|------|------|------|
| `/` | 首页 | Hero + 数据亮点 + 产品特性 + CTA |
| `/features` | 产品特性 | 分组展示全部功能 |
| `/download` | 下载 | 桌面端 / 移动端 / Web 版分类 |
| `/changelog` | 更新日志 | 版本迭代记录 |
| `/login` | 登录 | 邮箱 + 密码登录 |
| `/register` | 注册 | 邮箱 + 验证码 + 邀请码注册 |
| `/console` | 控制台 | 概览 / 日志 / 钱包（需登录） |
| `/feedback` | 意见反馈 | 用户反馈表单 |
| `/kod-ai-services-faqs` | 常见问题 | FAQ 页面 |
| `/jsjsubmit` | 问卷调查 | 用户调研 |

## 部署

见 `document/部署文档.md`。使用 Docker Compose 编排前端（Nginx）+ 后端（Spring Boot），MySQL / Redis 复用外部实例。

```bash
cp .env.example .env && vi .env     # 填写连接信息
docker compose build
docker compose up -d
```

## 版本记录

### v0.3.0 (2026-08-05)

- **新增控制台模块**：独立 `/console` 布局（侧边栏导航），包含概览、日志、钱包页面
- **新增蒜宝助手**：官网浮动 AI 问答助手，支持流式对话与速率限制
- **新增支付模块**：支付回调、订单管理、钱包余额查询
- **新增 Dashboard 数据看板**：小时级调用趋势、模型用量统计
- **新增中转站 API**：`GET /api/relay-station/list`（站列表）、`GET /api/relay-station/{stationId}/keys`（API Key 列表）
- **Bug 修复**：注册页 inviteCode 与 emailCode 参数顺序颠倒，导致邀请码和验证码双双校验失败
- **Bug 修复**：登录成功默认跳转 `/` 改为 `/console`，修复登录后被送回首页的问题
- **Bug 修复**：注册页验证码倒计时 timer 清理逻辑存在闭包泄漏
- **Bug 修复**：移除 AuthService 中中转站同步注册逻辑（`registerOnRelayStation`），解决事务同步超时及 relayMessage 回填不可靠问题
- **前端优化**：Navbar 重构（移动端汉堡菜单、滚动毛玻璃、激活指示器），移除定价/API Key 入口
- **前端优化**：根布局分离，控制台与官网页面独立布局互不干扰
- **前端优化**：注册页简化，移除冗余组件与复杂密码强度算法
- **用户模型扩展**：User 实体新增 `balance`、`historicalConsumption`、`connect` 字段
- **基础设施**：docker-compose 新增 ASSISTANT_* 环境变量，application.yml 新增 assistant/payment 配置段
- **文档清理**：删除过期 v1.0 接口文档与部署文档

### v0.2.4 (2026-07-27)

- 前端：全站品牌升级「KOD」→「KOD蒜粒」，覆盖首页、导航栏、Footer、下载页、功能页、FAQ、更新日志、SEO meta
- 前端：首页文案优化（Hero 标题/副标题、"KOD蒜粒是什么"、价值主张、CTA）
- 前端：导航栏 Logo 简化（K + KOD蒜粒）
- 前端：FAQ 页面全部条目品牌名称同步更新

### v0.2.3 (2026-07-27)

- 前端：定价页面登录后替换为 Token 零售站入口，点击跳转中转站 Dashboard（新窗口打开）

### v0.2.2 (2026-07-27)

- 前端：全局品牌文案「算力」→「蒜粒」
- 前端：首页 Hero badge 改为「正在锐意开发中」
- 前端：全站页面背景统一白底，body 全局背景色设置
- 前端：新增 KAI 期算白皮书页面（`/kai`，含完整市场概念框架）
- 前端：导航栏新增 KAI 期算入口，品牌标语更新
- 前端：定价页、常见问题页等全站文案同步
- 部署：邮件服务切换为 kod@kai.com（Zoho SMTP）

### v0.2.1 (2026-07-24)

- 后端：补充 `spring-boot-starter-mail` 依赖与 SMTP 配置（Zoho 邮箱）
- 后端：新增 `POST /api/auth/send-code` 端点，验证码 Redis 存储（60s 防刷、5min 有效）
- 后端：注册流程加入邮箱验证码校验，`LoginRequest` 新增 `emailCode` 字段
- 后端：邮件发送改为尽力而为（失败不阻塞注册），验证码写日志便于 dev 调试
- 前端：抽取 `config.ts` 统一管理 `KOD_WEB_URL`、`API_BASE_URL`
- 前端：导航栏"立即使用"加入登录检查：未登录跳转登录页，登录后自动重定向 Web 端
- 前端：登录页支持外部 URL 重定向（登录成功后跳转 KOD Web 端）
- README：补充本地部署注意事项（Node.js 版本、MySQL/Redis、邮件配置）

### v0.2.0 (2026-07-24)

- 新增登录/注册页面，邮箱 + 密码 + 验证码体系
- JWT Token 鉴权（React Context + localStorage）
- 前端 UI 全面升级：Lucide 图标、品牌色板扩展、数据亮点区、下载页平台分类
- 首页移除开源项目引用，替换为 KOD 品牌文案
- 导航栏新增"立即使用"入口，直达 KOD Web 版
- 邮箱验证码服务（`/api/auth/send-code`）
- Docker Compose 部署方案 + 部署文档
- 注册流程合并入 `/api/auth/login`，需邀请码关联中转站

### v0.1.0 (2026-07-16)

- 官网上线：首页、产品特性、下载、定价、FAQ、意见反馈
- 后端登录/注册：首次登录即注册，JWT 认证
- Spring Boot 3.3 + MyBatis-Plus + Druid + Redis
- 前端 React 19 + Rsbuild + TanStack Router + TailwindCSS 4
