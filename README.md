# kod-ai-portal

kod 官网项目 —— 前后端分离的单仓多项目仓库。

kod 是基于开源项目 [chatbox](https://github.com/chatboxai/chatbox) 二次研发的自有 AI 助手产品，本仓库为其**官方网站**，视觉与信息架构对标 [chatboxai.app](https://chatboxai.app/zh)。

## 目录结构

```
kod-ai-portal/
├── frontend/          # 官网前端（React 19 + Rsbuild + TailwindCSS + TanStack）
├── backend/           # 官网后端（Java 17 + Spring Boot 3 + MyBatis-Plus）
├── openspec/          # OpenSpec 规格与变更管理
├── README.md
├── .gitignore
└── .editorconfig
```

## 技术栈

### 前端（`frontend/`）
- **框架**：React 19 + TypeScript
- **构建**：Rsbuild（Rspack）
- **样式**：TailwindCSS v4
- **路由 / 数据**：TanStack Router（文件式路由）+ TanStack Query
- **代码检查**：oxlint

> 技术栈参考 `kai-new-api/web/default`。

### 后端（`backend/`）
- **语言 / 框架**：Java 17 + Spring Boot 3.3
- **ORM**：MyBatis-Plus（Spring Boot 3 starter）
- **构建**：Maven
- **数据库**：dev 使用 H2 内存库（免依赖启动）；test / prod 使用 MySQL
- **包结构**：`com.kod` 下 `controller` / `service` / `mapper` / `entity` / `config` 分层

## 环境要求

- **Node.js** ≥ 20（推荐 24.x），npm ≥ 10（或使用 Bun）
- **JDK** ≥ 17（已在 JDK 21 验证）
- **Maven** ≥ 3.9

## 本地启动

### 前端

```bash
cd frontend
npm install          # 安装依赖（也可用 bun install）
npm run dev          # 启动开发服务，默认 http://localhost:3000
npm run build        # 生产构建，产物在 dist/
npm run typecheck    # 类型检查
npm run lint         # 代码检查
```

开发环境下 `/api` 请求会代理到后端（默认 `http://localhost:8080`，可通过 `.env` 的 `VITE_API_BASE_URL` 覆盖，参考 `frontend/.env.example`）。

### 后端

```bash
cd backend
mvn clean package -DskipTests    # 构建 jar
mvn spring-boot:run              # 启动（默认 dev 环境，H2 内存库）
# 或：java -jar target/kod-portal-backend-0.1.0.jar

# 健康检查
curl http://localhost:8080/api/health
```

切换环境（通过 `spring.profiles.active`）：

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=test
java -jar target/kod-portal-backend-0.1.0.jar --spring.profiles.active=prod
```

test / prod 环境的数据库连接通过环境变量注入（`DB_HOST` / `DB_PORT` / `DB_NAME` / `DB_USERNAME` / `DB_PASSWORD`），不在代码中硬编码。

## 后端 API

统一响应结构：`{ "code": 0, "message": "success", "data": ... }`（`code` 为 0 表示成功，非 0 为错误）。

| 方法 | 路径 | 说明 | 鉴权 |
| ---- | ---- | ---- | ---- |
| GET  | `/api/health` | 健康检查 | 无 |
| POST | `/api/relay-station` | 保存 AI 中转站（`url` + `inviteCode` 存第一表，`apiKey` 存第二表；邀请码唯一） | 无 |
| POST | `/api/auth/login` | 登录或注册：首次登录即注册（必填有效 `inviteCode` 关联中转站），返回 JWT；老用户校验密码，邀请码不生效 | 无 |
| GET  | `/api/relay-station/config` | 凭 JWT 获取当前用户关联中转站的 `url` 与 `apiKey` | `Authorization: Bearer <token>` |

数据表：

- `relay_station`（第一表）：`url`、`invite_code`（唯一索引）
- `relay_station_key`（第二表）：`api_key`、`station_id`（关联第一表主键）
- `sys_user`：`email`（唯一）、`password`（BCrypt）、`station_id`（注册时由邀请码关联）

示例（本地 dev）：

```bash
# 1. 保存中转站
curl -X POST http://localhost:8080/api/relay-station -H 'Content-Type: application/json' \
  -d '{"url":"https://fane.kai.com/v1","apiKey":"sk-xxxx","inviteCode":"INVITE-001"}'

# 2. 首次登录即注册，拿到 token
curl -X POST http://localhost:8080/api/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"a@b.com","password":"pass123","inviteCode":"INVITE-001"}'

# 3. 凭 token 获取中转站配置
curl http://localhost:8080/api/relay-station/config -H "Authorization: Bearer <token>"
```

> JWT 密钥与有效期通过 `JWT_SECRET` / `JWT_EXPIRE_MILLIS` 环境变量配置（见 `application.yml`），生产环境务必覆盖默认密钥。

## 分支与环境

| 分支   | 用途       | 说明                     |
| ------ | ---------- | ------------------------ |
| `dev`  | 本地开发   | 日常开发主分支           |
| `test` | 测试环境   | 部署到测试环境的代码     |
| `prod` | 正式环境   | 部署到生产环境的代码     |

约定：功能开发基于 `dev`，验证后合并到 `test`，测试通过后合并到 `prod`。

## 规格与变更管理

本仓库使用 [OpenSpec](openspec/) 管理规格与变更，规格位于 `openspec/`，变更提案位于 `openspec/changes/`。
