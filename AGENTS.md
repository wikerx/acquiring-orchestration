# AGENTS.md

# 跨境收单支付系统后端协作规范

## 项目定位

当前项目 `acquiring-orchestration` 是跨境收单支付系统后端工程，包含商户 OpenAPI、收单支付、代付、渠道适配、后台管理、商户后台、网关、定时任务、公共组件等模块。

本项目涉及资金安全、交易状态一致、幂等、防重放、签名加密、日志审计和多服务协同。所有 AI 编码助手在修改代码、文档或配置说明时，必须优先保障：

1. 安全
2. 资金准确
3. 状态一致
4. 幂等
5. 可追踪
6. 可维护

---

## 当前模块地图

### 公共组件

* `component-library/component-core`：统一返回模型、基础异常、认证上下文、密码与 token 工具。
* `component-library/component-web`：Web 通用配置、统一异常处理、操作日志切面、内部鉴权拦截。
* `component-library/component-db`：MyBatis Plus 公共实体、Mapper、认证与 RBAC 支撑、ISO 字典能力。
* `component-library/component-security`：OpenAPI JWT、签名、加密、密钥、重放保护工具。
* `component-library/component-redis`、`component-http`、`component-mq`、`component-job`：Redis、HTTP、RocketMQ、轻量级任务调度共享契约基础封装。

### 业务模块

* `channel-library`：渠道适配抽象，包含收单渠道和代付渠道。
* `service-gateway`：网关服务。
* `service-admin`：管理后台服务。
* `service-merchant`：商户后台服务。
* `service-checkout`：收银台服务。
* `service-openapi`：商户开放接口入口服务。
* `service-payment`：收单支付核心服务。
* `service-payout`：代付核心服务。
* `service-job`：轻量级任务调度中心与定时任务服务。

### 当前真实背景说明

* `service-payment` 和 `service-payout` 当前仍偏骨架或模拟实现，不应误判为已经具备完整交易核心能力。
* 后续不能继续在模拟实现类中堆复杂支付或代付业务逻辑。
* 前端仓库独立维护，不在本规范的主要修改范围内。

---

## 修改前后输出要求

### 修改前必须说明

1. 准备修改的模块
2. 准备修改的文件
3. 修改原因
4. 风险点
5. 验证方式

### 修改后必须说明

1. 实际修改的文件
2. 影响范围
3. 风险点
4. 建议测试用例

---

## 总体原则

1. 不允许为了“代码好看”随意改变业务逻辑。
2. 不允许一次性大范围重构多个模块。
3. 不允许一次性格式化整个仓库。
4. 不允许未经明确要求修改外部接口字段、接口路径、签名规则、加密规则、状态码。
5. 不允许删除看似无用但可能被反射、配置、网关路由、定时任务、MyBatis、Spring 扫描使用的代码。
6. 支付系统优先保证安全、资金准确、幂等、状态一致、日志可追踪。
7. 不要生成大量 AI 风格的模板化代码和模板化注释。
8. 不要为小改动新增 Markdown 报告。
9. 不要为了统一目录而做与当前任务无关的大规模搬迁。
10. 不要把模拟实现继续演化成正式支付核心。

---

## 模块边界规则

### `service-openapi`

`service-openapi` 是商户开放接口入口，负责：

* 商户 JWT 鉴权
* 请求体解密
* 参数校验
* 防重放
* 商户基础权限校验
* 响应 `data` 加密
* 调用内部 `payment` / `payout` 服务

禁止：

* 在 OpenAPI Controller 中写支付核心业务
* 在 OpenAPI 层直接落支付交易主单
* 在 OpenAPI 层直接完成渠道扣款
* 绕过 `@VerificationAndProcessing` 新增商户对外 API
* 把完整卡号、CVV、密钥、JWT、私钥写入日志
* 返回未加密的成功 `data` 给商户侧接口

所有商户对外接口原则上必须：

* 使用 `POST`
* 走 `/api/rest/{domain}/{version}` 路径
* 使用 `@VerificationAndProcessing`
* 请求体加密
* 响应 `data` 加密
* 保留版本兼容能力
* 明确校验分组
* 明确幂等口径

### `service-payment`

`service-payment` 是收单支付核心服务，后续应承载：

* 支付交易主单
* 交易操作单
* 授权、请款、撤销、冲正、退款
* 渠道路由
* 风控编排
* 渠道请求与响应记录
* 交易状态机
* 幂等控制
* MQ 事件发布
* 对账、清分、结算所需基础数据

当前 `PaymentTransactionServiceImpl` 属于模拟实现，不允许继续在该类中堆完整支付核心逻辑。

后续应逐步拆分为：

* 交易受理应用服务
* 交易状态机
* 幂等服务
* 渠道路由服务
* 渠道调用服务
* 交易仓储服务
* 交易事件发布服务

### `service-payout`

`service-payout` 是代付核心服务，后续应承载：

* 代付申请
* 代付审核
* 代付渠道路由
* 代付状态机
* 渠道回调
* 退汇处理
* 幂等控制
* 代付流水与账务状态

当前代付实现偏模拟，不允许继续在模拟类中堆完整代付逻辑。

### `service-admin`

`service-admin` 是管理后台服务，新增管理端接口时优先使用：

```text
api
application
service
service.impl
dto
mapper
entity / DO
converter
```

不要再新增新的 `controller` 包。已有旧包如需调整，必须小步迁移，不得一次性大范围搬迁。

### `service-merchant`

`service-merchant` 是商户后台服务，后续风格应逐步对齐 `service-admin`：

```text
api
application
service
service.impl
dto
mapper
entity / DO
converter
```

已有 `controller` 包如被修改，可在任务范围内逐步迁移到 `api`，但不要为了统一目录大范围改动。

### `component-library`

`component-library` 只能放跨服务复用的基础能力，不允许变成业务垃圾桶。

可以放：

* 统一返回模型
* 基础异常
* 工具类
* Web 通用配置
* 安全加密组件
* Redis / MQ 基础封装
* MyBatis 基础配置
* 通用认证上下文

不应该放：

* 具体支付交易业务
* 某个渠道的特殊逻辑
* 某个后台页面的业务逻辑
* 某个商户功能的私有规则

### `channel-library`

`channel-library` 只放渠道适配抽象和通用模型。

禁止：

* 把平台交易状态机写在渠道库
* 把商户业务规则写在渠道库
* 把管理后台规则写在渠道库
* 直接暴露渠道原始响应给商户

渠道响应进入平台后，必须映射为平台统一状态、统一错误码、统一失败原因分层。

---

## 包结构和命名规则

优先使用：

```text
api
application
service
service.impl
dto
vo
entity
mapper
converter
config
support
security
client
```

不要随意新增：

```text
handler2
biz
manager
processor
helper
temp
test
new
old
```

除非有明确职责说明。

### 类型命名规则

```text
外部接口入参：xxxRequest
外部接口出参：xxxResponse
内部服务调用入参：xxxCommand 或 xxxClientRequestDTO
内部服务调用出参：xxxResult 或 xxxClientResponseDTO
查询条件：xxxQuery
后台页面展示：xxxVO
内部传输：xxxDTO
数据库实体：xxxDO
领域实体：xxxEntity
枚举：xxxEnum
```

禁止把 `DO` / `Entity` 直接作为外部接口入参或出参。

---

## 注释和文档规则

当前项目存在较多模板化注释，后续必须收敛。

### 注释允许与禁止

禁止继续生成：

* 每个字段都写无意义注释
* 每个构造器都写模板注释
* 每个简单 getter/setter 都写注释
* `@author`、`@date`、`@email`、`@status` 这类模板头
* “当前负责衔接，后续可扩展”等无实际约束的套话
* 与代码命名完全重复的注释

允许保留或新增：

* 业务规则说明
* 支付状态流转说明
* 幂等设计说明
* 金额精度和舍入说明
* 加密/签名安全边界说明
* 兼容历史接口的原因
* 不允许删除或修改的特殊逻辑说明

### 代码注释粒度

* 每个生产类、接口、枚举、record 必须有类注释，说明业务职责和所在层级。
* `public` 方法必须有方法注释，说明用途、关键参数、返回值和关键副作用。
* 复杂私有方法、关键分支、安全边界、状态流转、数据编排必须补简洁说明。
* DTO、VO、DO 不要求为每个字段补机械注释，但涉及金额、状态、安全、兼容语义的字段应补说明。
* 修改代码时必须同步更新注释。

### 文档文件规则

* 不要为小改动新增散落 Markdown。
* 优先更新现有 `docs` 下最接近的文档。
* 不要生成“扫描报告”“修复报告”“临时总结”类文件，除非用户明确要求。
* 不要把 PRD 内容长期写进代码仓库的工程约束文档。

---

## OpenAPI 安全规则

商户对外 API 必须满足：

1. 必须使用 `POST`
2. 必须经过商户身份认证
3. 必须经过请求体解密
4. 必须经过参数校验
5. 必须经过防重放校验
6. 成功响应的 `data` 必须加密
7. 不允许返回敏感明文
8. 不允许绕过 `@VerificationAndProcessing` 新增商户接口
9. 不允许在日志打印完整请求体明文
10. 不允许打印完整卡号、CVV、JWT、`merchantKey`、私钥、API Key

当前 OpenAPI 安全相关类包括：

```text
@VerificationAndProcessing
OpenApiHeaderInterceptor
OpenApiRequestBodyAdvice
OpenApiResponseBodyAdvice
OpenApiPayloadDecoder
OpenApiRequestArgumentResolver
OpenApiJwtReplayProtectionService
MerchantJwtVerifier
OpenApiPayloadCrypto
MerchantSecurityService
```

修改这些类时必须单独说明影响范围和回归测试场景。

---

## 渠道回调和商户通知规则

渠道回调接口不能直接套用商户 OpenAPI 的 `@VerificationAndProcessing`，但必须有自己的安全机制。

渠道回调必须具备：

* 渠道维度签名校验
* 渠道 IP 白名单
* 回调原文保存
* 回调幂等
* 渠道订单号与平台订单号映射
* 状态流转校验
* 重复回调安全处理
* 异常回调告警

商户通知重试接口必须是内部权限接口或后台权限接口，不能裸露给外部随意调用。

---

## 内部服务接口安全规则

所有 `/internal/**` 接口必须明确内部调用边界。

禁止认为路径叫 `internal` 就安全。

内部接口至少满足以下一种或多种机制：

* 网关内网隔离
* 内部服务签名
* 内部 token
* mTLS
* Nacos 内网访问限制
* IP 白名单
* 服务间调用专用 Header

`service-payment` 和 `service-payout` 的内部创建接口后续必须补充内部调用鉴权或明确只允许内网访问。

---

## 幂等规则

以下操作必须有幂等控制：

* 创建支付
* 授权
* 请款
* 预授权完成
* 撤销
* 冲正
* 退款
* 创建代付
* 代付审核
* 渠道回调
* 商户通知
* 对账入账
* 清分
* 结算
* MQ 消费

幂等维度必须明确，例如：

```text
merchantId + merchantOrderNo + operationType
merchantId + transactionId + operationType
channelCode + channelOrderNo + callbackType
messageId + consumerGroup
```

禁止只依赖 Redis 做资金类最终幂等。资金类幂等必须有数据库唯一约束或状态机保护。

---

## 交易状态机规则

支付和代付状态不能使用散落字符串。

禁止新增：

```java
"SUCCESS"
"FAILED"
"RECEIVED"
"PROCESSING"
```

这类散落状态值。

必须统一：

* 平台交易状态枚举
* 渠道原始状态
* 状态映射规则
* 状态可流转范围
* 终态不可逆规则
* 失败原因分层
* 对付款人、商户、BOPS 展示不同失败原因

状态流转必须单向、可审计、可追踪。

---

## 金额、币种、汇率规则

金额必须使用 `BigDecimal` 表示业务金额。

禁止：

* 使用 `double` / `float` 处理金额、费率、汇率
* 默认所有币种都是 2 位小数
* 在不知道币种精度时直接 `movePointRight(2)`
* 过早四舍五入
* 混淆交易金额、授权金额、请款金额、退款金额、手续费、保证金、结算金额

金额模型必须明确：

* 金额
* 币种
* 币种精度
* 最小单位金额
* 舍入规则
* 汇率来源
* 汇率生效时间
* 汇率使用场景：交易、结算、查询、清分

汇率至少保留 8 位以上小数精度，结算汇总展示再按币种规则处理。

---

## 日志规则

核心链路日志必须包含必要上下文：

* `traceId`
* `requestId`
* `merchantId`
* `storeId`
* `merchantOrderNo`
* `paymentOrderNo`
* `payoutOrderNo`
* `channelCode`
* `channelOrderNo`

禁止日志输出：

* 完整卡号
* CVV
* JWT
* `merchantKey`
* API Key
* 私钥
* 完整身份证件号
* 银行卡号
* 未脱敏手机号和邮箱
* 完整请求密文和解密明文

日志要区分：

* 商户可见错误
* 付款人可见错误
* BOPS 内部真实错误
* 技术异常

---

## 配置和环境规则

禁止生产环境启用：

* 本地模拟支付
* 本地模拟代付
* Noop MQ
* Noop Channel
* Redis 防重放降级
* 明文密钥
* 默认密码
* 测试商户密钥
* 测试数据库账号

如果配置项类似：

```text
remoteEnabled=false
replay.required=false
NoopMqProducer
```

出现在 `prod` / `uat` 环境，必须明确阻止或给出风险说明。

---

## 数据库和事务规则

资金、状态、幂等相关操作必须有事务边界。

禁止：

* 先发 MQ 后提交本地事务
* 先通知商户后提交本地事务
* 回调重复入账
* 退款重复扣减
* 结算重复生成
* 终态交易被覆盖
* 无条件 update 状态
* 无 where 条件 update/delete

建议后续采用：

* 本地事务 + 事件表
* 事务提交后异步发 MQ
* MQ 消费幂等
* 状态机 CAS 更新
* 唯一索引兜底幂等

---

## 测试规则

修改以下内容必须补测试或说明测试场景：

* JWT 验签
* 请求体加解密
* 响应 `data` 加密
* 防重放
* 金额换算
* 币种精度
* 汇率计算
* 支付创建幂等
* 渠道回调幂等
* 状态流转
* 退款
* 代付
* MQ 重复消费
* 权限校验
* 敏感日志脱敏

测试不能只验证 happy path，必须覆盖异常场景和重复请求场景。

---

## 重构顺序

禁止一上来重构全仓库。

推荐顺序：

### P0：安全和风险收口

* 检查生产环境是否允许本地模拟支付/代付
* 检查 Redis 防重放生产配置
* 检查 `/internal/**` 内部接口鉴权
* 检查渠道回调入口安全
* 检查日志是否可能输出敏感明文

### P1：支付核心骨架落地

* 交易主单
* 交易操作单
* 幂等表
* 状态机
* 渠道请求记录
* 渠道响应记录
* MQ 事件表

### P2：代码结构统一

* `service-admin` 保持 `api -> application -> service`
* `service-merchant` 逐步对齐
* 禁止新增混乱包名
* `DTO / Request / Response / Command / Query / VO / DO` 命名统一

### P3：注释和文档瘦身

* 删除模板化注释
* 删除无意义类头
* 合并重复文档
* 禁止新增临时扫描报告
* 保留关键业务规则注释

---

## 基础编码规则

### Java 与 Spring 规则

* 使用 Java 17
* 使用构造器注入，不使用字段注入
* Controller 不直接承担复杂事务逻辑、数据库组合查询或支付核心编排
* Service 不应被 Controller 直接绕过 Application 层去做复杂跨域编排
* Mapper / Repository 只做数据访问
* Converter 只做对象转换

### API 规则

* 外部接口必须区分 `Request DTO`、`Response DTO`、内部 `DTO`、数据库 `DO`
* 禁止把 `DO` 或 `Entity` 直接作为外部接口入参或出参
* 管理后台接口使用 `CommonResult`
* 对外 OpenAPI 不得绕过统一安全链路

### 常量与状态规则

* 不使用魔法值
* 枚举、状态、操作类型统一收敛到常量或 `Enum`
* 不在业务代码中散落手写状态字符串

### MyBatis 与 SQL 规则

* 优先使用 `LambdaQueryWrapper`
* 非必要不写原始 SQL
* 分页查询使用分页插件
* 防止全表 update 和全表 delete

### Redis 与 MQ 规则

* Redis key 必须带业务前缀
* RocketMQ 消息必须可重试、可幂等
* 不假设 MQ exactly-once

---

## 本地 Skills 使用规则

本项目在 `.agents/skills/` 下维护仓库级本地 Skills，用于让 Codex / Claude Code 在不同任务中使用统一工作流。

### 可用 Skills

1. `payment-backend-review`：后端代码结构和支付系统工程质量审查。
2. `openapi-security-review`：商户 OpenAPI 安全链路审查。
3. `idempotency-state-machine-review`：幂等和状态机审查。
4. `amount-currency-rate-review`：金额、币种、汇率、结算计算审查。
5. `docs-governance`：文档目录和文档规范治理。
6. `spring-boot-clean-refactor`：Spring Boot 分层和小步重构。
7. `sql-mybatis-review`：SQL、Mapper、索引、幂等约束审查。
8. `test-regression-plan`：生成修改后的回归测试方案。

### 使用要求

1. 涉及支付核心代码时，优先使用 `payment-backend-review`。
2. 涉及 OpenAPI、签名、加密、防重放时，优先使用 `openapi-security-review`。
3. 涉及支付状态、回调、MQ、退款、结算时，优先使用 `idempotency-state-machine-review`。
4. 涉及金额、币种、汇率、手续费、保证金时，优先使用 `amount-currency-rate-review`。
5. 涉及文档目录、Markdown、README 时，优先使用 `docs-governance`。
6. 涉及 Controller、Service、Mapper 分层重构时，优先使用 `spring-boot-clean-refactor`。
7. 涉及 SQL、Mapper XML、索引、幂等表时，优先使用 `sql-mybatis-review`。
8. 每次修改完成后，使用 `test-regression-plan` 输出测试建议。

### 禁止行为

1. 不要安装未知来源的外部 Skill。
2. 不要把个人全局 Skill 写入项目。
3. 不要安装 `npm` / `pip` / `brew` 依赖来配合 Skill。
4. 不要让 Skill 自动修改业务代码。
5. 不要把 Skill 写成大而全的重复说明，每个 Skill 只聚焦一个任务。

---

## 外部 Skill 处理规则

当前建议优先使用项目本地 Skills，不建议安装未知外部 Skills。

如需安装外部 curated Skill，请先列出 Skill 名称、来源、用途、风险，等待用户确认后再执行。

禁止：

* 直接安装未知 GitHub 仓库 Skill
* 直接修改用户全局 `~/.agents/skills`
* 直接修改 `/etc/codex/skills`
* 直接安装会执行脚本的 Skill
* 直接安装带未知依赖的 Skill
