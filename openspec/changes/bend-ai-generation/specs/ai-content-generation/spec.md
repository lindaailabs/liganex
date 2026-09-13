# Spec: ai-content-generation

面向 B 端客户的 AI 内容生成能力，覆盖文生文/文生图/图生图/图生视频，并统一经模型供应商抽象层接入。

## ADDED Requirements

### Requirement: 模型供应商抽象层
系统 SHALL 提供统一的模型供应商抽象接口，屏蔽具体厂商差异，支持文生文、文生图、图生图、图生视频四类调用。

#### Scenario: 经抽象层路由生成请求
- **GIVEN** 已配置至少一家模型供应商（首期 Volcengine）
- **WHEN** 业务层发起任意一类生成请求
- **THEN** 请求经抽象层路由到对应供应商适配实现，并返回标准化结果（含资产 URL / 文本内容 / 错误码）

#### Scenario: 供应商可插拔
- **GIVEN** 抽象层已定义 `generateText/generateImage/editImage/generateVideo` 接口
- **WHEN** 新增一家供应商（如 OpenAI）
- **THEN** 仅需实现对应适配器并注册，无需改动业务层代码

#### Scenario: 新增 OpenAI Sora 视频供应商
- **GIVEN** 抽象层已定义视频生成供应商接口与按名称解析的工厂
- **WHEN** 新增 OpenAI Sora 适配器并注册为组件
- **THEN** 请求可按供应商标识路由到该适配器，service 与 controller 无需改动；
- **AND** 该供应商的请求构造与响应字段解析只存在于适配器内，不泄漏到业务层，使线上字段差异可在适配器内收敛

#### Scenario: 供应商状态归一化
- **GIVEN** 供应商返回自有状态字面量
- **WHEN** 适配器解析响应
- **THEN** 映射到统一状态机 `pending/running/succeeded/failed`，业务层与前端只依赖统一状态，不感知供应商差异

### Requirement: 文生文（text-to-text）
系统 SHALL 支持以文本提示词生成文本，首期场景含商品文案、多语言翻译/Listing 优化、客服话术。

#### Scenario: 生成商品文案
- **GIVEN** 用户提供商品标题与卖点关键词
- **WHEN** 调用文生文并选择"商品文案"模板
- **THEN** 返回符合跨境平台调性的标题/五点描述/搜索词

#### Scenario: 多语言翻译
- **GIVEN** 一段中文商品描述与目标语言（如 EN/DE/JP）
- **WHEN** 调用文生文翻译
- **THEN** 返回保留电商术语准确性的译文

### Requirement: 文生图（text-to-image）
系统 SHALL 支持以文本提示词生成图像，首期场景含商品主图、场景图、营销图。

#### Scenario: 生成商品主图
- **GIVEN** 用户提供商品描述与风格约束
- **WHEN** 调用文生图
- **THEN** 返回图像资产 URL 及生成参数快照

### Requirement: 图生图（image-to-image）
系统 SHALL 支持以图像+提示词进行图像编辑，首期场景含背景替换、风格迁移、局部重绘（inpainting）。

#### Scenario: 商品背景替换
- **GIVEN** 一张商品白底图与目标场景描述
- **WHEN** 调用图生图并指定"背景替换"
- **THEN** 返回替换背景后的图像资产

### Requirement: 图生视频（image-to-video）
系统 SHALL 支持以图像生成短视频，首期场景含商品展示视频、营销短片。

#### Scenario: 生成商品展示视频
- **GIVEN** 一张商品主图与运动/时长参数
- **WHEN** 调用图生视频
- **THEN** 返回视频资产 URL 及生成任务标识

### Requirement: 异步任务与状态
系统 SHALL 将生图/生视频等长任务建模为异步任务，支持提交、状态查询、回调通知与结果持久化。

#### Scenario: 提交长任务并轮询
- **GIVEN** 用户提交一个图生视频任务
- **WHEN** 任务进入队列
- **THEN** 立即返回 taskId，用户可凭 taskId 查询 pending/running/succeeded/failed 状态并获取结果

#### Scenario: 任务结果持久化
- **GIVEN** 任务执行成功
- **THEN** 资产元数据（URL、供应商、参数、耗时、客户归属）写入存储，可回溯

### Requirement: 用量与配额控制
系统 SHALL 按 B 端客户维度记录生成用量并施配额上限，防止成本失控。

#### Scenario: 配额内正常生成
- **GIVEN** 客户当月用量未达配额
- **WHEN** 发起生成请求
- **THEN** 正常执行并累加用量计数

#### Scenario: 超额拒绝
- **GIVEN** 客户当月用量已达配额上限
- **WHEN** 发起生成请求
- **THEN** 拒绝并返回明确配额超限错误，不调用供应商（不产生成本）

### Requirement: ERP 数据作为生成上下文（可选）
系统 SHOULD 支持在生成前经 liganex-mcp 拉取商品/订单数据，使产出贴合真实业务（grounded generation）。

#### Scenario: 以真实商品属性生成文案
- **GIVEN** 用户选定某 SKU 并要求"据此生成 Listing"
- **WHEN** 系统经 liganex-mcp 读取该 SKU 的属性/价格/库存
- **THEN** 生成的文案包含真实卖点且数值与 ERP 一致，不臆造

### Requirement: B 端客户业务系统（多菜单框架）
系统 SHALL 为 B 端客户提供统一的客户业务系统（多菜单控制台），作为面向客户的入口，集成 AI 创作、订单、商品、库存、数据看板、账户配额等模块。

#### Scenario: 统一登录与导航
- **GIVEN** B 端客户通过客户级账号登录
- **WHEN** 进入系统
- **THEN** 展示左侧/顶部多菜单导航（AI 创作、订单管理、商品管理、库存管理、数据看板、账户配额），并默认进入工作台首页

#### Scenario: 工作台首页聚合
- **GIVEN** 客户登录后进入首页
- **WHEN** 首页加载
- **THEN** 聚合展示关键指标（近期订单、库存预警、近期生成任务、配额剩余），并以卡片/图表呈现

#### Scenario: 模块间数据贯通
- **GIVEN** 客户在「AI 创作」中选定某 SKU 生成文案
- **WHEN** 系统需读取该 SKU 属性
- **THEN** 经 liganex-mcp 取得真实 ERP 数据，无需在各模块间重复录入

#### Scenario: 账户与配额管理
- **GIVEN** 客户进入「账户配额」菜单
- **WHEN** 查看用量
- **THEN** 展示当前订阅档位、各类生成用量与剩余配额、API Key 管理入口

#### Scenario: 客户操作收敛到单一前端入口
- **GIVEN** 客户需要进行 AI 生成、ERP 查询、配额查看等任意操作
- **WHEN** 访问系统
- **THEN** 全部经由 `liganex-studio` 这一个前端应用完成，不存在面向客户的第二套独立前端/子域/微前端

### Requirement: B 端流式对话工作台（业务系统的 AI 创作模块）
系统 SHALL 在 B 端客户业务系统内提供流式（逐 token）对话模块（「AI 创作 / 智能助手」），作为 AI 生成能力与 ERP 数据查询的交互入口之一。

#### Scenario: 流式输出对话回复
- **GIVEN** B 端客户在业务系统的「AI 创作」对话模块发起一条消息
- **WHEN** 后端开始生成回复
- **THEN** 响应以 SSE/流式方式逐 token 返回，前端实时渲染，且支持中途取消

#### Scenario: 多轮上下文理解
- **GIVEN** 用户已进行多轮对话并上传过参考图
- **WHEN** 用户追问"把刚才那张主图换成红色背景"
- **THEN** 系统基于会话上下文理解指代，无需重复上传或描述（会话状态由应用侧维护）

#### Scenario: 意图路由到生成或 ERP 查询
- **GIVEN** 用户输入"给这个 SKU 写个德语 Listing"或"美国仓还有多少库存"
- **WHEN** 对话引擎解析意图
- **THEN** 前者路由到文生文生成能力，后者经 liganex-mcp 查询 ERP 并返回结构化结果

#### Scenario: 对话内联展示生成结果
- **GIVEN** 用户在对话中触发一次文生图
- **WHEN** 生成完成
- **THEN** 图像资产直接内联展示在对话流中，可一键插入草稿或下载

### Requirement: 供应商未配置时的明确报错
系统 SHALL 在供应商凭证未配置时，于发起任何出站调用**之前**拒绝请求，并返回可区分于内部错误的明确错误码，避免用户面对白屏或泛化错误。

#### Scenario: 未配置视频生成服务
- **GIVEN** 视频生成服务凭证未注入（如未配置 API Key）
- **WHEN** 用户提交视频生成请求
- **THEN** 返回错误码 `VIDEO_PROVIDER_NOT_CONFIGURED`，HTTP 状态为 503
- **AND** 系统不向供应商发起任何 HTTP 调用（不产生成本）
- **AND** 前端展示明确的「服务未配置」提示，而非白屏或泛化错误

#### Scenario: 未知供应商
- **GIVEN** 请求指定的供应商标识不在已注册的适配器列表中
- **WHEN** 提交生成请求
- **THEN** 返回「不支持的供应商」错误（HTTP 400），且不发起任何出站调用

#### Scenario: 未配置错误不被归为内部错误
- **GIVEN** 错误码为 `VIDEO_PROVIDER_NOT_CONFIGURED`
- **WHEN** 统一异常处理器转换响应
- **THEN** HTTP 状态为 503（服务不可用），而非 500（内部错误），使用户可据此判断是配置缺失而非服务故障

### Requirement: 节点式生成画布（video canvas）
系统 SHALL 在前端提供节点式画布，支持以「提示词 / 图片 → 视频生成 → 结果预览」的连线编排生成任务，并支持提交与轮询。

#### Scenario: 连线推导生成参数
- **GIVEN** 画布上存在提示词节点与视频生成节点
- **WHEN** 用户将提示词节点连线到视频生成节点
- **THEN** 生成请求的 prompt 由上游提示词节点内容推导得出，无需在生成节点上重复录入

#### Scenario: 缺少上游提示词时拒绝提交
- **GIVEN** 视频生成节点没有任何上游提示词节点连线，或上游提示词为空
- **WHEN** 用户提交生成
- **THEN** 画布给出可读的校验提示，不发起后端请求

#### Scenario: 提交并轮询到终态
- **GIVEN** 画布已连线且提示词非空
- **WHEN** 用户提交生成
- **THEN** 返回任务标识并开始轮询
- **AND** 当状态进入终态（succeeded/failed）时停止轮询，避免无谓的上游调用
- **AND** 结果预览节点展示视频资产或失败原因

#### Scenario: 未配置服务时画布给出明确提示
- **GIVEN** 后端未配置视频生成服务
- **WHEN** 用户在画布上提交生成
- **THEN** 画布展示「服务未配置」的明确提示，不出现白屏或泛化错误
