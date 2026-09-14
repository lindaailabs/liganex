# Tasks: B 端 AI 生成应用

> 规划态任务，实现时按此拆分为独立 change。单条不超过 2 小时工作量。

## 设计/契约
- [ ] 定义模型供应商抽象接口 `generateText/generateImage/editImage/generateVideo` 与统一返回结构
- [ ] 确定 `liganex-gen` 与 `liganex-studio` 的仓库归属（采纳 design.md 提议或备选）
- [ ] 新建 `docs/adr/ADR-0003-bend-auth-quota.md`（B 端应用多租户/配额授权边界）

## 供应商接入
- [ ] 实现 Volcengine 适配器：方舟文本 / Seedream 图像 / 即梦视频
- [ ] 配置化静态路由，验证抽象层可插拔

## 生成能力 MVP（每类独立验证）
- [ ] 文生文 MVP：商品文案 + 多语言翻译
- [ ] 文生图 MVP：商品主图/场景图
- [ ] 图生图 MVP：背景替换 + 局部重绘
- [ ] 图生视频 MVP：商品展示视频

## 平台能力
- [ ] 异步任务框架：提交/状态机/轮询/结果持久化
- [ ] 用量与配额服务：客户级统计 + 超额拒绝（不调用供应商）
- [ ] ERP 数据上下文接入（可选）：经 liganex-mcp 读取 SKU 属性生成 grounded 文案

## 前端：B 端客户业务系统（多菜单）
- [ ] 业务系统框架：统一登录、多菜单导航布局（AI 创作 / 订单 / 商品 / 库存 / 数据看板 / 账户配额）、权限路由
- [ ] 工作台首页：聚合指标卡片与图表（订单/库存预警/近期生成/配额剩余）
- [ ] 订单管理页：经 liganex-mcp 查询订单列表/详情/筛选
- [ ] 商品管理页：SKU 列表/属性（生成时可选作上下文）
- [ ] 库存管理页：库存看板 + 补货建议
- [ ] 数据看板页：销售/利润分析图表
- [ ] 账户配额页：订阅档位、用量统计、剩余配额、API Key 管理

## 流式对话模块（AI 创作菜单下）
- [ ] 对话后端：消息接收、意图路由（生成 vs ERP 查询）、SSE 流式推送、会话上下文管理
- [ ] 前端流式对话 UI（liganex-studio，置于 AI 创作菜单）：实时渲染、上传参考图、中断/重试
- [ ] 与生成能力打通：对话中直接触发文生文/文生图并内联展示结果

## 视频生成切片（本次实现，前端画布 + OpenAI Sora 供应商）

### 供应商抽象与 OpenAI Sora 适配
- [ ] 定义视频生成供应商接口与工厂（按名称解析；未知供应商明确报错，不静默回退）
- [ ] 实现 OpenAI Sora 适配器：**供应商请求/响应字段解析全部收敛在该适配器内**，不泄漏到 service/controller
- [ ] 供应商状态归一化到统一状态机（pending/running/succeeded/failed 四种映射）
- [ ] 配置化默认供应商与凭证注入（无 key 时 `configured=false`）
- [ ] `ErrorCode` 新增视频生成错误码，并同步 `GlobalExceptionHandler` 段映射（未配置→503、未知供应商→400、上游失败→502）
- [ ] `liganex-studio-backend/pom.xml` 引入 `spring-boot-starter-restclient`：Spring Boot 4 把 RestClient 自动配置拆成独立 starter（同 Flyway），仅靠 `spring-boot-starter-web` 没有 `RestClient.Builder` bean，适配器构造器注入会在启动时失败。**该缺陷常规单测发现不了**（测试均自建 `RestClient.builder()`，绕过容器装配），故配套一条最小上下文装配冒烟测试（`VideoGenerationWiringTest`）固化该依赖，删依赖即构建失败

### 视频生成异步任务
- [ ] `V9__ai_video_generation.sql`：任务表 + owner 索引 + 状态约束
- [ ] 实体 / Mapper（owner 维度查询）
- [ ] 服务层：提交、查询、列表；**owner 隔离（跨用户一律 404）**
- [ ] 服务层：**仅非终态才回源供应商**，避免读取放大上游调用
- [ ] REST 接口（提交 / 查询 / 列表）

### 前端：节点式画布（video canvas）
- [ ] 引入 `@xyflow/react`，实现提示词 / 图片 / 视频生成 / 结果预览四类节点
- [ ] 连线→参数推导纯逻辑（上游提示词/图片推导生成请求，缺提示词时给出可读错误）
- [ ] API 层：响应与状态归一化、终态集合
- [ ] 轮询 hook：**终态即停**
- [ ] 页面接入路由与侧边栏菜单，未配置服务时展示明确提示

## 验证
- [ ] `openspec validate bend-ai-generation` 通过
- [ ] 后端 `mvn -pl liganex-studio-backend -am compile` 通过
- [ ] 后端单测（不依赖真实网络）：工厂解析/未知供应商/未配置分支、Sora 适配器请求体与四种状态映射、**无 key 提交返回 `VIDEO_PROVIDER_NOT_CONFIGURED` 且未发起任何 HTTP 调用**、owner 隔离 404
- [ ] 断言未配置错误经 handler 映射为 **HTTP 503**（而非 500），未知供应商为 400
- [ ] 装配冒烟：`RestClient.Builder` 由 Boot 自动配置提供，适配器可被容器装配（防 starter 依赖被误删）
- [ ] 前端 `npm run typecheck && npm run build && npm test` 通过（测试只覆盖纯逻辑与 API 层，不渲染画布）
- [ ] 端到端冒烟：提交一次文生图 → 配额扣减 → 异步完成 → 资产可查看
- [ ] 人工冒烟：画布拖出「提示词→视频生成→结果预览」并连线 → 提交 → 无 key 时 UI 显示明确的「服务未配置」提示而非白屏/泛化错误
