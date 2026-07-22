# Graph Report - .  (2026-07-22)

## Corpus Check
- 95 files · ~118,757 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 1010 nodes · 2136 edges · 54 communities (49 shown, 5 thin omitted)
- Extraction: 85% EXTRACTED · 15% INFERRED · 0% AMBIGUOUS · INFERRED: 322 edges (avg confidence: 0.8)
- Token cost: 0 input · 0 output

## Community Hubs (Navigation)
- 媒体资源接口
- 纪念日接口
- 日记接口
- 回忆实体模型
- 回忆时间线前端
- 仪表盘首页
- 认证接口
- 私信接口
- 用户实体模型
- 心情实体模型
- API客户端
- 应用路由与认证
- 统一错误处理
- 信件实体模型
- 集成测试
- 日记交互组件
- 纪念日页面
- 回忆业务服务
- 关系实体模型
- 安全配置
- 前端依赖配置
- 回忆接口
- 应用配置
- Toast通知
- 信笺页面
- 登录与重置
- Node构建配置
- 仪表盘业务服务
- 初始化接口
- 数据仓库
- 个人资料接口
- 项目规范与品牌
- 密码认证服务
- 跨页面加载删除
- API数据模型
- 用户查询安全
- TypeScript构建配置
- 仪表盘接口
- 业务异常模型
- 会话主体安全
- PowerShell脚本基础
- 账户资料服务
- Spring启动入口
- 初始化业务服务
- TypeScript项目配置
- Service Worker
- Maven项目配置

## God Nodes (most connected - your core abstractions)
1. `User` - 38 edges
2. `Media` - 36 edges
3. `ApiException` - 35 edges
4. `ViewMapper` - 34 edges
5. `errorMessage()` - 33 edges
6. `MediaStorageService` - 32 edges
7. `Anniversary` - 28 edges
8. `CurrentUserService` - 28 edges
9. `LetterMessage` - 27 edges
10. `Memory` - 25 edges

## Surprising Connections (you probably didn't know these)
- `Frontend Static Assets` --conceptually_related_to--> `192px Couple Profile Icon`  [INFERRED]
  AGENTS.md → frontend/public/icon-192.png
- `Frontend Static Assets` --conceptually_related_to--> `512px Couple Profile Icon`  [INFERRED]
  AGENTS.md → frontend/public/icon-512.png
- `Frontend Static Assets` --conceptually_related_to--> `Love Space Social Preview Image`  [INFERRED]
  AGENTS.md → frontend/public/og.png
- `Love Space Social Preview Image` --conceptually_related_to--> `Love Space Branding`  [INFERRED]
  frontend/public/og.png → frontend/index.html
- `submit()` --calls--> `errorMessage()`  [EXTRACTED]
  frontend/src/views/SetupView.vue → frontend/src/api/client.ts

## Import Cycles
- None detected.

## Hyperedges (group relationships)
- **Spring Boot Environment Configurations** — backend_src_main_resources_application_configuration, backend_src_main_resources_application_prod_configuration, backend_src_test_resources_application_test_configuration [INFERRED 0.85]
- **Love Space Brand Assets** — frontend_index_love_space_brand, frontend_public_icon_192_couple_icon, frontend_public_icon_512_couple_icon, frontend_public_og_love_space_social_preview [INFERRED 0.85]

## Communities (54 total, 5 thin omitted)

### Community 0 - "媒体资源接口"
Cohesion: 0.05
Nodes (26): MediaView, Authentication, GetMapping, RequestMapping, Resource, ResponseEntity, RestController, Validated (+18 more)

### Community 1 - "纪念日接口"
Cohesion: 0.05
Nodes (26): AnniversaryController, Authentication, DeleteMapping, GetMapping, PostMapping, PutMapping, RequestMapping, ResponseStatus (+18 more)

### Community 2 - "日记接口"
Cohesion: 0.05
Nodes (26): DiaryController, Authentication, DeleteMapping, GetMapping, PostMapping, PutMapping, RequestMapping, ResponseStatus (+18 more)

### Community 3 - "回忆实体模型"
Cohesion: 0.07
Nodes (11): Entity, Override, PrePersist, PreUpdate, Table, Memory, Memory, MemoryRepository (+3 more)

### Community 4 - "回忆时间线前端"
Cohesion: 0.08
Nodes (27): unwrapList(), toLocalDateTimeInput(), applyPage(), clearYear(), editing, error, fileInput, filters (+19 more)

### Community 5 - "仪表盘首页"
Cohesion: 0.06
Nodes (28): mediaUrl(), Mood, anniversaries, data, dueReminders, duration, heartBurst, letters (+20 more)

### Community 6 - "认证接口"
Cohesion: 0.11
Nodes (20): AuthController, Authentication, AuthenticationManager, GetMapping, HttpServletResponse, Logger, PostMapping, RequestMapping (+12 more)

### Community 7 - "私信接口"
Cohesion: 0.13
Nodes (16): MessageRequest, MessageView, Authentication, DeleteMapping, GetMapping, PostMapping, RequestMapping, ResponseStatus (+8 more)

### Community 8 - "用户实体模型"
Cohesion: 0.09
Nodes (10): InitialUser, Entity, Override, PrePersist, PreUpdate, Table, User, Authentication (+2 more)

### Community 9 - "心情实体模型"
Cohesion: 0.09
Nodes (7): Entity, Override, PrePersist, PreUpdate, Table, Mood, Mood

### Community 10 - "API客户端"
Cohesion: 0.13
Nodes (22): API_BASE, ApiError, ensureCsrfToken(), request(), resetCsrfToken(), api, nav, route (+14 more)

### Community 11 - "应用路由与认证"
Cohesion: 0.09
Nodes (25): onSessionExpired(), { show }, router, applyAuth(), bootstrapAuth(), clearAuth(), login(), avatarInput (+17 more)

### Community 12 - "统一错误处理"
Cohesion: 0.20
Nodes (14): ApiError, GlobalExceptionHandler, Logger, ResponseEntity, ConstraintViolationException, DataIntegrityViolationException, ExceptionHandler, HandlerMethodValidationException (+6 more)

### Community 13 - "信件实体模型"
Cohesion: 0.09
Nodes (8): Entity, Override, PrePersist, Table, LetterMessage, LetterMessageRepository, Page, Pageable

### Community 14 - "集成测试"
Cohesion: 0.19
Nodes (11): ActiveProfiles, AfterEach, AutoConfigureMockMvc, PasswordEncoder, LoveSpaceApiIntegrationTest, BeforeEach, JdbcTemplate, MockHttpSession (+3 more)

### Community 15 - "日记交互组件"
Cohesion: 0.10
Nodes (21): card, emit, focusable(), onKey(), authorOf(), canEdit(), diaries, editing (+13 more)

### Community 16 - "纪念日页面"
Cohesion: 0.10
Nodes (21): daysUntilAnniversary(), todayInput(), anniversaries, countdownLabel(), countdownPrefix(), countdownValue(), days(), editing (+13 more)

### Community 17 - "回忆业务服务"
Cohesion: 0.23
Nodes (7): MemoryRequest, PageResponse, Authentication, MultipartFile, Service, Transactional, MemoryService

### Community 18 - "关系实体模型"
Cohesion: 0.12
Nodes (6): Couple, Entity, Override, PrePersist, PreUpdate, Table

### Community 19 - "安全配置"
Cohesion: 0.16
Nodes (11): ApiError, AuthenticationManager, HttpServletResponse, PasswordEncoder, SecurityConfig, Bean, Configuration, CorsConfigurationSource (+3 more)

### Community 20 - "前端依赖配置"
Cohesion: 0.10
Nodes (19): dependencies, lucide-vue-next, vue, vue-router, devDependencies, @types/node, typescript, vite (+11 more)

### Community 21 - "回忆接口"
Cohesion: 0.18
Nodes (12): MemoryView, Authentication, DeleteMapping, GetMapping, MultipartFile, PostMapping, PutMapping, RequestMapping (+4 more)

### Community 22 - "应用配置"
Cohesion: 0.11
Nodes (19): Application Configuration, CORS Policy, Flyway Enabled, JPA Schema Validation, Media Size Limits, Multipart Upload Limits, MySQL Datasource, Production Application Configuration (+11 more)

### Community 23 - "Toast通知"
Cohesion: 0.12
Nodes (15): icons, { toasts, dismiss }, state, ToastItem, ToastTone, useToast(), canNext, error (+7 more)

### Community 24 - "信笺页面"
Cohesion: 0.17
Nodes (15): formatDate(), formatDateTime(), sameId(), composerOpen, content, error, letters, loading (+7 more)

### Community 25 - "登录与重置"
Cohesion: 0.12
Nodes (16): closeReset(), error, loading, password, resetConfirm, resetError, resetLoading, resetOpen (+8 more)

### Community 26 - "Node构建配置"
Cohesion: 0.12
Nodes (16): compilerOptions, allowImportingTsExtensions, erasableSyntaxOnly, lib, module, moduleDetection, noEmit, noFallthroughCasesInSwitch (+8 more)

### Community 27 - "仪表盘业务服务"
Cohesion: 0.21
Nodes (7): CurrentUserService, DashboardService, Authentication, Service, Transactional, ViewMapper, Component

### Community 28 - "初始化接口"
Cohesion: 0.15
Nodes (9): SetupRequest, GetMapping, Logger, PostMapping, RequestMapping, ResponseStatus, RestController, SetupController (+1 more)

### Community 29 - "数据仓库"
Cohesion: 0.24
Nodes (8): CoupleRepository, Mood, MoodRepository, AccountService, Logger, PasswordEncoder, Service, JpaRepository

### Community 30 - "个人资料接口"
Cohesion: 0.19
Nodes (9): MoodRequest, PasswordChangeRequest, Authentication, MultipartFile, PutMapping, RequestMapping, ResponseStatus, RestController (+1 more)

### Community 31 - "项目规范与品牌"
Cohesion: 0.23
Nodes (12): Love Space Build Workflow, Flyway Database Migrations, Frontend Static Assets, Java 17 Spring Boot Service, Repository Guidelines, Vue 3 and TypeScript SPA, Love Space Branding, Web App Manifest (+4 more)

### Community 32 - "密码认证服务"
Cohesion: 0.24
Nodes (4): Override, Transactional, UserDetails, Transactional

### Community 33 - "跨页面加载删除"
Cohesion: 0.20
Nodes (12): errorMessage(), remove(), load(), pickRandom(), saveMood(), remove(), load(), remove() (+4 more)

### Community 34 - "API数据模型"
Cohesion: 0.29
Nodes (7): ApiDtos, CoupleView, DashboardResponse, MeResponse, MoodView, SetupStatus, SpaceNameRequest

### Community 35 - "用户查询安全"
Cohesion: 0.24
Nodes (4): UserRepository, DatabaseUserDetailsService, Service, UserDetailsService

### Community 36 - "TypeScript构建配置"
Cohesion: 0.18
Nodes (10): compilerOptions, allowArbitraryExtensions, erasableSyntaxOnly, noFallthroughCasesInSwitch, noUnusedLocals, noUnusedParameters, tsBuildInfoFile, types (+2 more)

### Community 37 - "仪表盘接口"
Cohesion: 0.29
Nodes (5): DashboardController, Authentication, GetMapping, RequestMapping, RestController

### Community 39 - "会话主体安全"
Cohesion: 0.39
Nodes (4): Override, SessionPrincipal, GrantedAuthority, UserDetails

### Community 41 - "账户资料服务"
Cohesion: 0.60
Nodes (3): ProfileRequest, UserView, Authentication

### Community 42 - "Spring启动入口"
Cohesion: 0.60
Nodes (3): LoveSpaceApplication, EnableScheduling, SpringBootApplication

### Community 43 - "初始化业务服务"
Cohesion: 0.70
Nodes (3): PasswordEncoder, Service, SetupService

## Knowledge Gaps
- **181 isolated node(s):** `com.lovespace:love-space-backend`, `name`, `private`, `version`, `type` (+176 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **5 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `ViewMapper` connect `仪表盘业务服务` to `媒体资源接口`, `纪念日接口`, `日记接口`, `用户查询安全`, `回忆实体模型`, `私信接口`, `心情实体模型`, `初始化业务服务`, `回忆业务服务`, `关系实体模型`, `数据仓库`?**
  _High betweenness centrality (0.055) - this node is a cross-community bridge._
- **Why does `Memory` connect `回忆实体模型` to `集成测试`?**
  _High betweenness centrality (0.040) - this node is a cross-community bridge._
- **Why does `ApiException` connect `业务异常模型` to `密码认证服务`, `纪念日接口`, `日记接口`, `媒体资源接口`, `认证接口`, `私信接口`, `用户实体模型`, `初始化业务服务`, `统一错误处理`, `回忆业务服务`, `初始化接口`, `数据仓库`?**
  _High betweenness centrality (0.035) - this node is a cross-community bridge._
- **What connects `com.lovespace:love-space-backend`, `name`, `private` to the rest of the system?**
  _181 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `媒体资源接口` be split into smaller, more focused modules?**
  _Cohesion score 0.05063291139240506 - nodes in this community are weakly interconnected._
- **Should `纪念日接口` be split into smaller, more focused modules?**
  _Cohesion score 0.0505175983436853 - nodes in this community are weakly interconnected._
- **Should `日记接口` be split into smaller, more focused modules?**
  _Cohesion score 0.05201266395296246 - nodes in this community are weakly interconnected._