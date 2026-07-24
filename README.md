# Love Space

Love Space 是一个面向两个人的私密共享空间，用来记录和管理共同生活中的回忆、日程与计划。项目采用 Vue 3 前端和 Spring Boot 后端，支持本地开发、单 JAR 构建以及通过 HTTPS 反向代理部署。

## 功能概览

- 情侣空间初始化与双账号登录
- 首页仪表盘、心情和实时同步
- 日历、纪念日、月报和愿望清单
- 回忆、相册、地图、标签和媒体上传
- 日记、信笺和通知中心
- 个人资料、头像、密码修改与数据导出
- 回收站和数据恢复
- CSRF 防护、会话认证、账号/密码恢复限流
- 上传文件大小、空间配额和磁盘剩余空间保护

## 技术栈

- 前端：Vue 3、TypeScript、Vite、Vue Router
- 后端：Java 17、Spring Boot、Spring Security、Spring Data JPA、Flyway
- 数据库：MySQL 8（测试使用 H2）
- 部署：Spring Boot 单 JAR，可由 Nginx、Caddy 等 HTTPS 反向代理暴露

## 目录结构

```text
backend/        Spring Boot 后端、数据库迁移和后端测试
frontend/       Vue 3 前端
scripts/        PowerShell 开发、构建、数据库和部署脚本
data/           本地上传数据（不提交）
outputs/        构建和发布产物（不提交）
```

## 环境要求

- Windows + PowerShell 7（推荐使用 `pwsh`）
- JDK 17 或更高版本
- Maven 3.9 或更高版本
- Node.js 20 LTS 或更高版本
- MySQL 8，并确保 `mysql` 客户端在 `PATH` 中

## 快速启动

### 1. 获取代码并创建配置

在项目根目录执行：

```powershell
Copy-Item .env.example .env
```

编辑 `.env`，至少修改以下配置：

```dotenv
DB_USERNAME=root
DB_PASSWORD=修改为你的数据库密码
SETUP_TOKEN=生成一个高熵随机字符串
```

`.env` 只保存在本机，不要提交到 GitHub。初始化口令和密码恢复口令不要复用。

### 2. 初始化数据库

确认 MySQL 已启动后执行：

```powershell
pwsh -ExecutionPolicy Bypass -File .\scripts\setup-db.ps1
```

脚本会根据 `.env` 创建数据库并设置 `utf8mb4`。应用启动时会自动执行 Flyway 数据库迁移。

### 3. 启动开发环境

```powershell
pwsh -ExecutionPolicy Bypass -File .\scripts\start-dev.ps1
```

首次启动会安装前端依赖，并在后台启动：

- 前端：<http://localhost:5173>
- 后端健康检查：<http://localhost:8080/api/health>
- 日志目录：`work/logs/`

若依赖已经安装，可使用：

```powershell
pwsh -ExecutionPolicy Bypass -File .\scripts\start-dev.ps1 -SkipInstall
```

### 4. 首次初始化空间

打开前端地址后，未初始化的实例会进入 `/setup`：

1. 设置空间名称和在一起的日期；
2. 创建两个独立登录账号及密码；
3. 填入 `.env` 中的 `SETUP_TOKEN`；
4. 初始化完成后使用账号登录。

初始化口令只用于首次创建空间，不会保存为网站功能凭据。密码至少 8 位，请使用更长且唯一的密码。

## 构建和测试

### 一键构建

该脚本会构建前端、运行 Maven 测试并打包后端。前端静态文件会被嵌入后端 JAR：

```powershell
pwsh -ExecutionPolicy Bypass -File .\scripts\build.ps1
```

产物位置：

- 后端 JAR：`backend/target/love-space-backend-1.0.0.jar`
- 单 JAR 副本：`outputs/Love-Space-v1.0.jar`

### 单独执行测试

```powershell
mvn -f backend/pom.xml test
```

前端目前没有独立测试运行器，修改前端后至少执行：

```powershell
Push-Location frontend
npm run build
Pop-Location
```

## 生产部署

### 1. 生产配置

在 `.env` 中设置：

```dotenv
SPRING_PROFILES_ACTIVE=prod
SERVER_ADDRESS=127.0.0.1
SERVER_PORT=8080
SESSION_COOKIE_SECURE=true
CORS_ALLOWED_ORIGINS=https://你的域名
```

生产配置会强制校验：回环地址绑定、安全 Cookie、HTTPS CORS 来源和转发头处理。

### 2. 构建并启动

```powershell
pwsh -ExecutionPolicy Bypass -File .\scripts\build.ps1
pwsh -ExecutionPolicy Bypass -File .\scripts\start.ps1
```

生产环境不要使用 `-Lan`，也不要把 Spring Boot 的 8080 端口直接暴露到公网。应由 Nginx、Caddy 或其他反向代理监听 443，并将请求转发到 `127.0.0.1:8080`。前端静态资源已经打进 JAR，反向代理可以直接代理应用入口和 `/api` 请求。

示意拓扑：

```text
浏览器 -- HTTPS:443 --> 反向代理 -- HTTP:127.0.0.1:8080 --> Love Space JAR
```

如果反向代理与应用不在同一台机器上，应使用私有网络地址，并通过防火墙只允许代理服务器访问应用端口。

## 重要配置

所有配置都在根目录 `.env` 中维护，完整模板见 [.env.example](.env.example)。常用配置包括：

- `DB_URL`、`DB_USERNAME`、`DB_PASSWORD`：数据库连接
- `SETUP_TOKEN`：首次初始化口令
- `PASSWORD_RESET_TOKEN`：可选的高熵密码恢复口令
- `UPLOAD_DIR`：上传文件目录，默认 `./data/uploads`
- `MEDIA_MAX_BYTES`、`MEDIA_TOTAL_MAX_BYTES`：上传配额
- `CORS_ALLOWED_ORIGINS`：允许的前端来源
- `VITE_API_BASE_URL`：前端 API 基础路径

密码恢复口令应使用随机生成值，并通过 [scripts/rotate-password-reset-token.ps1](scripts/rotate-password-reset-token.ps1) 轮换。不要把真实 `.env`、数据库、上传文件、日志、构建产物或证书私钥提交到仓库。

## 开发约定

- 后端源码按 `api`、`service`、`domain`、`repository`、`security` 分层。
- 数据库结构变更必须新增顺序 Flyway migration。
- 本地运行数据、日志和构建产物由 `.gitignore` 排除。
- 提交前请运行相关测试，并确认没有把 `.env` 或生成文件加入 Git。

## 许可证

当前仓库未声明开源许可证。如需公开分发，请先补充合适的 LICENSE 文件。
