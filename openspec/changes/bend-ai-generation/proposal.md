# Change: B 端 AI 生成应用（bend-ai-generation）

## Why
当前 Liganex 的产品面集中在基础设施层（MCP server、开放平台 hub、客户端 studio、skill）与平台扩展（AI 自动化/可观测/沙箱）。
面向 B 端客户的**直接价值**尚未落地——客户需要的是成品：商品文案、主图、场景图、营销视频，而不是一个工具接口。
跨境 ERP 场景下，卖家每天要生产大量 Listing 文案、多语言翻译、商品图与短视频，这些是高频、刚需、可量化付费的痛点。
因此新增应用层：以 ERP 业务数据作为上下文，调用模型供应商产出内容，构成 Liganex 面向 B 端的营收面。

## What Changes
- 新增 capability `ai-content-generation`，覆盖四类生成能力：文生文、文生图、图生图、图生视频。
- 新增模型供应商抽象层（首期 Volcengine 方舟/Seedream/即梦，预留 OpenAI 等），避免绑定单一厂商。
- 新增异步任务框架（生图/生视频为长任务，需提交→轮询/回调→结果）。
- 新增按客户的用量/配额控制（成本控制，区别于基础设施层的轻量授权）。
- 可选：生成前经 liganex-mcp 拉取商品/订单数据作为生成上下文（grounded generation）。
- 新增 B 端**客户业务系统（多菜单）**：统一登录/导航框架，承载 AI 创作、订单管理、商品管理、库存管理、数据看板、账户配额等模块（前端置于 `liganex-studio` frontend）。
- **单一前端入口约束**：面向客户的所有操作收敛到 `liganex-studio` 这一个前端应用，不另起独立客户前端/子域/微前端；原「客户端 / agent 运行时」职责也并入此应用。
- 流式对话作为业务系统的「AI 创作 / 智能助手」模块入口之一：文本/图片输入 → 意图路由（生成能力 / ERP 查询）→ SSE 流式返回；与四类生成能力并列，并承载智能客服问答。
- 新增**视频生成落地切片**：前端节点式画布（`liganex-studio` frontend，提示词/图片 → 视频生成 → 结果预览）+ 后端 OpenAI Sora 供应商适配器与异步任务（`liganex-studio/backend` 的 `module/generation`），兑现「供应商可插拔」与「未配置明确报错」两条既有约束。

## 与 design.md 的仓归属偏离（本次实现说明）
design.md 提议生成后端独立为 `liganex-gen` 仓。本次**未拆仓**，视频生成切片直接落在 `liganex-studio/backend` 的 `module/generation`，保持模块化单体（ADR-0006）。

- **偏离理由**：单供应商、单能力的最小闭环尚不足以支撑独立部署单元；先以模块边界（package 结构 + DTO 隔离）兑现同等演进能力，避免过早分布式化带来的运维成本。
- **约束**：`module/generation` 不与其他模块共享实体；供应商请求构造与响应字段解析全部收敛在 `provider/openai` 内，使线上字段差异只在适配器内收敛。
- **影响**：design.md 的仓库拓扑为**目标态**，本次为**过渡态**；日后拆分 `liganex-gen` 时须整体迁移 `module/generation` 包与 `V9__ai_video_generation.sql`，并保持 REST 契约不变。

## Impact
- 新增业务仓库（详见 design.md 的仓库归属提议）：建议生成后端独立为 `liganex-gen`，B 端工作台 UI 并入 `liganex-studio`。
- 影响 `openspec/config.yaml` 项目定位（已更新为"两层产品面"）。
- 不影响已归档的 mcp-server / security 主规范；仅对 security 的授权边界做澄清（见下）。
- 新增前端 surface：B 端**客户业务系统（多菜单）**总入口，含 AI 创作（流式对话 + 四类生成）、订单/商品/库存管理、数据看板、账户配额等菜单模块。

## Non-goals
- 不做通用 AIGC 平台：聚焦跨境 ERP 卖家场景（商品文案/图文/短视频），不泛化到任意创作。
- 不绑定单一模型厂商：抽象层必须可插拔，首期只实现 Volcengine 适配。
- 首期不做多模型自动择优/质量路由（仅配置化静态路由）。
- 不实现完整计费账单系统：仅做用量统计与客户级配额上限（防成本失控），计费留给后续。
- 不替代 liganex-mcp：mcp 是数据/工具连接层；本应用层是其消费方之一，而非并入。
- 不建设多个面向客户的独立前端：所有客户操作收敛到 `liganex-studio` 单一前端（见 design.md 单一前端入口约束）。

## ADR Relationship
- 关联 `docs/adr/ADR-0002-auth-scope`：明确 **ADR-0002 的轻量授权仅覆盖基础设施层（MCP/ERP 连接、内部工具）**，
  其"不实现 OAuth 2.1 / 细粒度 scope"的结论**不适用于本 B 端应用面**。B 端面向外部付费客户，至少需客户级 API Key + 用量/配额控制。
- 触发 ADR-0002 中声明的补齐条件："一旦接入多租户或真实客户数据，须补齐 OAuth 2.1 + scope 校验"——本应用面即该触发场景。
- 新建 `docs/adr/ADR-0003-bend-auth-quota.md` 记录 B 端应用的多租户/配额授权边界（见 tasks）。
