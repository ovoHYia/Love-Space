# 仓库指南

## 项目结构与模块组织

Love Space 由前后端两部分组成。`frontend/` 是 Vue 3 + TypeScript 单页应用：页面位于 `src/views/`，共享 UI 位于 `src/components/`，API 调用位于 `src/api/`，客户端状态位于 `src/stores/`，静态资源位于 `frontend/public/`。`backend/` 是 Java 17 + Spring Boot 4 服务，按层组织：REST 控制器在 `api/`，业务逻辑在 `service/`，持久化实体在 `domain/`，Spring Data 仓库在 `repository/`，横切的配置、安全与时间工具分别在 `config/`、`security/`、`time/`。数据库迁移为 `backend/src/main/resources/db/migration/` 下按序编号的 Flyway SQL 文件。根目录 `scripts/` 存放 PowerShell 工作流脚本。本地上传、日志、发布暂存与生成产物位于 `data/`、`work/`、`outputs/`、`frontend/dist/`、`backend/target/`，这些路径一律不得提交。

## 构建、测试与开发命令

- `pwsh -ExecutionPolicy Bypass -File .\scripts\start-dev.ps1` 在锁文件安装戳缺失或过期时安装前端依赖，随后启动 Vite 与 Spring Boot，日志写入 `work/logs/`。`-SkipInstall` 仅在安装戳最新时使用。后端经 `mvn spring-boot:run` 启动，与 Maven 构建一样会执行 `frontend/dist` 新鲜度校验，因此全新检出后需先在 `frontend/` 下运行一次 `npm run build`，否则后端会立即退出。
- `pwsh -ExecutionPolicy Bypass -File .\scripts\setup-db.ps1` 按根目录 `.env` 中 `DB_URL` 指定的库名创建并配置 MySQL 数据库，依赖 `mysql` 客户端。
- `pwsh -ExecutionPolicy Bypass -File .\scripts\build.ps1` 除非传入 `-SkipInstall`，否则先安装前端依赖，再依次执行 `npm test`、前端构建、`mvn clean package`，校验 JAR 内的静态前端资源，并把发布 JAR 复制到 `outputs/Love-Space-v1.0.jar`。
- `pwsh -ExecutionPolicy Bypass -File .\scripts\build.ps1 -MySqlTests` 额外启用真实 MySQL 测试：提供 `MYSQL_TEST_URL` 时优先使用，否则使用本机 `love_space_test` 库；密码优先 `MYSQL_TEST_PASSWORD`，未设置时回退到根目录 `.env` 的 `DB_PASSWORD`；用户名默认 `root`。本机 `_test` 库必须预先存在，该选项不会自动创建。
- 当用户请求构建、部署、发布或完整验证时，直接在工作区执行 `scripts/build.ps1 -MySqlTests` 并汇报结果，不要只把命令丢给用户；跳过 MySQL 检查的普通 `mvn test` 不算完整验证。
- 在 `frontend/` 下，`npm run dev`、`npm test`、`npm run build` 分别用于启动 Vite、运行 Vitest、类型检查并生成 `dist/`。
- `mvn -f backend/pom.xml test` 默认使用 Spring `test` profile 与 H2 运行后端测试；只有存在 `MYSQL_TEST_URL` 与 `MYSQL_TEST_PASSWORD` 进程变量时才会执行真实 MySQL 测试。Maven 在 `validate` 阶段会校验 `frontend/dist/` 的新鲜度，因此需先运行 `npm run build`，或直接使用 `scripts/build.ps1`。

## 代码风格与命名约定

Vue、TypeScript、JSON、CSS 使用 2 空格缩进；Java、XML 保持现有的 4 空格缩进。Vue 组件与视图用 PascalCase 命名（如 `DashboardView.vue`），composable 用 camelCase（如 `toast.ts`），Java 类型用 PascalCase。`com.lovespace` 下的包名一律小写；实体名用单数，并沿用 `*Controller`、`*Service`、`*Repository` 后缀。遵循既有的 import 顺序，避免无关的格式化改动。

## 测试指南

后端行为测试放在 `backend/src/test/java/com/lovespace/` 下，新增或更新时均使用描述性的 `*Test.java` 命名，并尽可能覆盖 HTTP 层行为。每次 schema 变更都必须附带 Flyway 迁移，迁移文件名按现有最大编号顺序递增，例如 `V20__add_feature.sql`。前端单元测试使用 Vitest；UI 变更后运行 `npm test` 与 `npm run build`。
真实 MySQL 测试只能指向名称以 `_test` 结尾的本机数据库，不得指向生产库。

## 提交与 Pull Request 指南

本仓库已有提交历史。提交主题使用简体中文的 Conventional Commit 祈使句风格，例如 `feat: 添加纪念日提醒`，并保持单次提交聚焦。Pull Request 应说明用户可见的变更，注明对数据库或配置的影响，关联相关 issue；UI 变更需附截图。绝不提交 `.env`、上传文件、日志、构建或发布产物、凭据；新增必需配置项时，同步更新对应的 `.env.example` 模板。
