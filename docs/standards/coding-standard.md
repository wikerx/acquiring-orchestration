# 代码编写规范

本规范以《阿里巴巴 Java 开发手册》的工程习惯为基线，并补充支付系统特有要求。

## 命名

1. 类名使用 `UpperCamelCase`，方法、参数、局部变量使用 `lowerCamelCase`。
2. 常量使用全大写，单词之间用下划线分隔，例如 `MAX_RETRY_TIMES`。
3. 抽象类使用 `Abstract` 或 `Base` 开头；异常类使用 `Exception` 结尾；测试类使用 `Test` 结尾。
4. 包名统一小写，避免复数和缩写堆叠。
5. 不使用拼音与英文混合命名，行业固定缩写除外，例如 `FX`、`KYC`、`AML`、`MCC`。
6. 基础包名统一使用 `com.scott.payment`。
7. 模块目录使用小写中划线，例如 `service-openapi`；Java 包名不使用大写或中划线。
8. 实现类包名使用 `service.impl`，不使用 `serviceImpl`。
9. 数据传输对象使用 `DTO`、`VO`、`BO`、`DO`、`PO` 作为规范后缀，例如 `PaymentCreateRequestDTO`。

## 分层

推荐后端分层：

- `controller`：接口入参校验、协议适配，不承载业务编排。
- `application`：用例编排、事务边界、幂等控制。
- `domain`：核心领域模型与业务规则。
- `infrastructure`：数据库、消息、第三方渠道、缓存等技术实现。
- `client`：外部服务调用封装。
- `common`：通用常量、工具、异常、结果模型。

## 类注释

1. 所有顶层 Java 类型必须保留类级 Javadoc，说明作者、版本、类名、创建时间、邮箱、用途和状态。
2. `@description` 必须写清楚类职责，避免只写“工具类”“业务类”等空泛描述。
3. 新建类统一使用以下模板：

```java
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AuthorizeSample
 * @date : 2026-05-28 10:28
 * @email : scott_x@163.com
 * @description : 授权示例
 * @status : create
 */
```

## 方法与字段注释

1. 新增类中的字段必须写清楚业务含义、单位、格式、是否敏感、是否允许为空。
2. 新增 public/protected 方法必须写 Javadoc，说明方法职责、核心参数、返回值和异常边界。
3. 对支付鉴权、加密、资金、分表、幂等、MQ、外部渠道调用等核心私有方法，也必须写 Javadoc。
4. 方法体内部只在关键处理点写必要注释，例如验签、解密、金额转换、分表路由、幂等锁、异常映射；禁止把每一行代码机械翻译成注释。
5. 测试用例必须用中文日志说明当前 case 的目的、关键输入摘要和结果摘要；敏感字段只允许输出长度、指纹或脱敏值，当前 OpenAPI 安全方案不再输出 `kid`。
6. 新增或重构代码时，Controller、ApplicationService、Service、Mapper、Converter、DTO、DO、前端页面主组件都必须补齐符合职责的注释；禁止只写“控制器”“服务类”“工具类”等无信息注释。
7. 注释详细程度要求遵循“让第一次接手该模块的同事能快速理解职责边界和关键约束”这一标准，尤其要写清楚任务调度中的锁、超时、重试、终态覆盖保护，以及支付链路中的幂等、金额单位和状态机终态。
8. 禁止生成模板化 AI 注释或重复代码字面含义的低质量注释；如果代码本身已经足够清晰，优先补“为什么这样设计”，而不是解释“这一行在做什么”。

## 异常

1. 不捕获 `Throwable`、`Error`。
2. 不吞异常，捕获后必须记录上下文或转换为业务异常。
3. 对外接口不直接暴露内部异常栈。
4. 支付链路错误必须区分：可重试、不可重试、处理中、需人工介入。

## 日志

1. 日志统一使用 Lombok `@Slf4j`，禁止手写 `LoggerFactory.getLogger(...)`。
2. 使用参数化日志，禁止字符串拼接。
3. 日志必须包含交易号、商户号、渠道号、请求号等可追踪字段。
4. 禁止打印完整卡号、CVV、证件号、密钥、token、签名原文等敏感信息。
5. 失败日志要包含错误码、错误来源、渠道响应码和处理建议。

## JSON

1. JSON 序列化与反序列化统一使用 `fastjson2`。
2. 业务代码优先调用 `component-core` 中的 `JsonUtils`，避免直接散落调用底层 JSON API。
3. Web 层统一由 `component-web` 的 fastjson2 HTTP message converter 处理接口入参与响应。
4. 不引入 `fastjson 1.x`、`Gson` 或业务侧自建 `ObjectMapper`，避免同一系统出现多套 JSON 行为。

## Controller 响应模型

1. `CommonResult` 用于业务接口，包括 OpenAPI、管理后台、商户后台、服务间内部接口。它承载支付业务码，例如 `T200`、`F401001`、`F500`，并提供 `isSuccess` 给服务间调用判断结果。
2. `ApiResult` 只用于轻量基础接口，例如健康检查、简单回调 ACK、临时基础探针。不要在需要支付业务码、响应加密、分页或业务数据的接口中使用。
3. Controller 可使用静态导入 `success` 简化写法，但必须保留数据语义：

```java
return success(service.query(request)); // 有业务 data
return success();                       // 无业务 data，仅表示操作成功
```

4. 禁止把有返回数据的接口统一改成 `success()`。这会丢失响应 `data`，并破坏 OpenAPI 响应加密、前端渲染和服务间调用。
5. 失败响应由统一异常处理或 `CommonResult.error(...)` 构造，业务代码不要返回成功码表达失败。

## REST 路由

1. 对外 OpenAPI 路径保留当前版本结构：`/api/rest/{domain}/{version}/{resource}`，例如 `/api/rest/iso/v1/currencies/query`。
2. 对外 API Controller 按资源拆分，一个清晰外部 API 入口对应一个 Controller，不把国家、币种等不同资源混在同一个 Controller。
3. 查询接口使用 `@PostMapping(".../query")`；创建接口使用 `@PostMapping`；整体替换使用 `@PutMapping`；局部更新使用 `@PatchMapping`；删除使用 `@DeleteMapping`。
4. POST 查询、交易或变更接口的加密业务参数统一放在 JSON 请求体 `data` 字段。

## 集合与并发

1. 集合判空优先使用工具方法或 `isEmpty()`。
2. 不在循环中频繁访问数据库或远程接口。
3. 共享可变状态必须明确并发边界。
4. 分布式锁必须设置过期时间，并记录锁键设计。

## 数据库

1. 表名、字段名使用小写下划线。
2. 每张业务表必须包含 `id`、`gmt_create`、`gmt_modified`。
3. 金额使用最小货币单位整数或高精度 decimal，禁止使用浮点数。
4. 状态字段使用明确枚举值，禁止魔法数字散落在代码中。
5. 涉及交易、资金、对账的表必须保留审计字段。
6. 表示具体时间点的字段必须使用 `DATETIME(3)`，默认当前时间使用 `CURRENT_TIMESTAMP(3)`，自动更新时间使用 `ON UPDATE CURRENT_TIMESTAMP(3)`。
7. `DATE`、`TIME`、只表示业务日期的字段、外部渠道原始字符串时间字段不要强行改为 `DATETIME(3)`。
8. 后端时间字段优先使用 `LocalDateTime`，不要为了页面展示不显示毫秒而改成 `String`、`Date` 或 `Timestamp`。

## 前端时间展示

1. Admin 管理系统日期时间统一展示为 `yyyy-MM-dd HH:mm:ss`，页面不展示毫秒。
2. 前端优先复用 `formatDateTime` 或 `BaseDateTime`，不要在页面内重复实现时间格式化函数。
3. 禁止使用 `substring` 截断接口返回时间，禁止要求后端接口去掉毫秒。
4. 日期范围查询组件保持原有行为，不因展示格式治理改变查询入参。

## 测试

1. 领域规则、金额计算、状态流转、幂等和补偿逻辑必须有单元测试。
2. 渠道适配器必须覆盖成功、失败、超时、重复通知、签名错误等场景。
3. 修复缺陷必须补充回归测试。
