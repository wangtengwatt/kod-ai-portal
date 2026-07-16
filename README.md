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

## 分支与环境

| 分支   | 用途       | 说明                     |
| ------ | ---------- | ------------------------ |
| `dev`  | 本地开发   | 日常开发主分支           |
| `test` | 测试环境   | 部署到测试环境的代码     |
| `prod` | 正式环境   | 部署到生产环境的代码     |

约定：功能开发基于 `dev`，验证后合并到 `test`，测试通过后合并到 `prod`。

## 规格与变更管理

本仓库使用 [OpenSpec](openspec/) 管理规格与变更，规格位于 `openspec/`，变更提案位于 `openspec/changes/`。
