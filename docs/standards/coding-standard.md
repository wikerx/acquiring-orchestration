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

推荐后端分层，具体包名以模块现有结构和最近的 `AGENTS.md` 为准：

- `api` / `controller`：接口入参校验、协议适配，不承载业务编排。
- `application`：用例编排、事务边界、幂等控制。
- `domain`：核心领域模型与业务规则。
- `infrastructure`：数据库、消息、第三方渠道、缓存等技术实现。
- `client`：外部服务调用封装。
- `common`：通用常量、工具、异常、结果模型。

新增或实质修改的依赖统一使用构造器注入。历史字段注入只在当前任务触达且能够通过测试证明兼容时小步整改，禁止以统一风格为由批量修改。

## 对象转换与 MapStruct

1. 后端对象转换优先使用 MapStruct，适用于 `DO/Entity -> DTO/VO/Response`、`Request -> DTO/DO`、`Query -> DTO`、`List<T>` 和分页记录转换等普通字段映射场景。
2. 已引入 MapStruct 的模块必须复用现有版本、依赖和注解处理器配置，不重复引入依赖；如涉及 Lombok，必须确认注解处理器配置不破坏现有编译。
3. MapStruct 转换器统一使用 `@Mapper(componentModel = "spring")`，业务类通过构造器注入；禁止使用 `XxxConverter.INSTANCE` 绕过 Spring 管理。
4. 转换器命名优先沿用项目现有 `converter` 包和 `XxxConverter` 风格；避免使用 `XxxMapper` 与 MyBatis Mapper 混淆。
5. 字段名一致时不需要写 `@Mapping`；字段名不一致、需要忽略、不允许自动转换的字段必须显式声明 `@Mapping` 或 `ignore = true`。
6. 编辑更新场景优先使用 `@MappingTarget` 更新已有对象；禁止覆盖主键、创建人、创建时间等系统字段。局部更新且 null 不应覆盖旧值时，必须配置 `NullValuePropertyMappingStrategy.IGNORE`。
7. 以下场景必须优先整改为 MapStruct：大量重复手写 `set/get`、同类转换在多个类中重复、核心业务对象使用 `BeanUtils.copyProperties`、使用 JSON 序列化反序列化做对象复制、Controller 中直接拼装 VO、Service 中存在大段无业务含义的字段赋值。
8. 以下场景不得强行 MapStruct 化：转换中需要查数据库或调用外部服务、依赖当前登录用户权限、包含敏感字段脱敏、包含金额/汇率/费率精度计算、包含状态机或复杂业务规则。此类逻辑可保留在 Service 或 Assembler 中，普通字段复制部分仍可拆给 MapStruct。
9. 禁止把复杂业务规则堆进 `@Mapping(expression = "...")`；表达式过多时应拆为清晰的辅助方法，并说明业务边界。
10. 禁止 MapStruct 与 `BeanUtils.copyProperties`、JSON 对象复制在同一类普通转换场景中混乱并存；确需保留动态字段复制、测试构造或第三方对象适配时，必须有明确原因。
11. 敏感字段不得自动映射到前端 VO/Response，包括 `password`、`salt`、`secretKey`、`privateKey`、`apiKey`、`accessToken`、`refreshToken`、`jwtSecret`、`aesKey`、`rsaPrivateKey`、`certificateContent`、`cvv`、`fullCardNo`、`authorization` 等。确需展示时必须经过脱敏、权限控制和审计。
12. 替换手写转换或 `BeanUtils.copyProperties` 后，必须核对 null 行为、默认值、日期格式、`BigDecimal` 精度、敏感字段暴露风险，并执行对应模块编译和测试。

## 类注释

1. 对外契约、核心领域类型、安全边界、状态机、金额计算和非显然基础设施类型应提供类级 Javadoc，说明职责、边界和关键不变量。
2. 简单且命名自解释的内部类型不强制添加低信息量注释。
3. 新建代码禁止添加作者、邮箱、创建日期、类名复述和 `@status` 模板。这些信息由版本控制维护，容易过期。
4. 修改已有模板注释时，只在当前任务需要时更新职责内容，不为了清理元数据批量改动历史文件。

## 方法与字段注释

1. 对外模型和不能由名称准确表达的业务字段，应写清业务含义、单位、格式、敏感性和空值语义；不要机械注释普通依赖或自解释字段。
2. 公共契约、具有副作用的方法和非显然 public/protected 方法应写 Javadoc，说明职责、核心参数、返回值和异常边界；简单委托方法不强制重复代码含义。
3. 对支付鉴权、加密、资金、分表、幂等、MQ、外部渠道调用等核心私有方法，也必须写 Javadoc。
4. 方法体内部只在关键处理点写必要注释，例如验签、解密、金额转换、分表路由、幂等锁、异常映射；禁止把每一行代码机械翻译成注释。
5. 测试名称和断言应直接表达 case 目的。除排查确有需要外，不为每个测试机械打印开始/结束日志；任何测试输出仍须脱敏，当前 OpenAPI 安全方案不输出 `kid`。
6. 新增或重构代码时，Controller、ApplicationService、Service、Mapper、Converter、DTO、DO、前端页面主组件都必须补齐符合职责的注释；禁止只写“控制器”“服务类”“工具类”等无信息注释。
7. 注释详细程度要求遵循“让第一次接手该模块的同事能快速理解职责边界和关键约束”这一标准，尤其要写清楚任务调度中的锁、超时、重试、终态覆盖保护，以及支付链路中的幂等、金额单位和状态机终态。
8. 禁止生成模板化 AI 注释或重复代码字面含义的低质量注释；如果代码本身已经足够清晰，优先补“为什么这样设计”，而不是解释“这一行在做什么”。

## 异常

1. 不捕获 `Throwable`、`Error`。
2. 不吞异常，捕获后必须记录上下文或转换为业务异常。
3. 对外接口不直接暴露内部异常栈。
4. 支付链路错误必须区分：可重试、不可重试、处理中、需人工介入。

## 日志

1. 新增和实质修改的业务类统一使用 Lombok `@Slf4j`；需要静态 logger 的基础设施或兼容代码可沿用现有方式，不得为统一样式批量重构历史类。
2. 统一使用 SLF4J 参数化日志，格式为“中文事件描述, `event: STABLE_EVENT_CODE`, `fieldName: {}`”；字段之间使用半角逗号和空格，禁止字符串拼接、`String.format` 组装日志和 `[{}]` 值包装。
3. 中文事件描述应简洁、准确、可搜索；`event` 事件码和字段名使用稳定英文，字段名采用 `lowerCamelCase`。已有监控依赖的事件码不得因文案优化随意改名。
4. 日志按场景包含 `traceId`、`requestId`、`merchantId`、订单号、交易号、渠道号等关联字段。同一语义在开始、结束和失败日志中使用同一字段名。
5. `info` 记录正常关键流程，`debug` 记录可关闭的诊断摘要，`warn` 记录可恢复异常或结果未确认，`error` 记录已确认业务失败或技术异常。日志级别不能替代结构化结果处理，也不能用断言或异常中断本应继续的跑量请求。
6. 失败日志应在安全边界内包含稳定错误码、错误来源、渠道响应码、异常类型和处理建议；禁止通过动态 message 反推业务语义。
7. 禁止打印完整卡号、CVV、证件号、账户号、密钥、token、JWT、签名原文、明文/完整密文报文或未脱敏渠道响应。需要定位时只记录掩码、长度、摘要或白名单字段。
8. 协议值、枚举值、公开 message 和第三方原始码不得为了“中文化”而翻译或改写；中文只用于日志事件说明和非契约诊断文案。

推荐示例：

```java
log.info("支付请求完成, event: PAYMENT_REQUEST_END, merchantId: {}, orderNo: {}, transactionId: {}, resultCode: {}, elapsedMs: {}",
        merchantId, orderNo, transactionId, resultCode, elapsedMs);
```

## JSON

1. JSON 序列化与反序列化统一使用 `fastjson2`。
2. 业务代码优先调用 `component-core` 中的 `JsonUtils`，避免直接散落调用底层 JSON API。
3. Web 层统一由 `component-web` 的 fastjson2 HTTP message converter 处理接口入参与响应。
4. 不引入 `fastjson 1.x`、`Gson` 或业务侧自建 `ObjectMapper`，避免同一系统出现多套 JSON 行为。

## Controller 响应模型

1. `CommonResult` 用于业务接口，包括 OpenAPI、管理后台、商户后台、服务间内部接口。商户 OpenAPI 外层承载 V1.3 请求码（正常形成业务响应为 `A0000`），交易结果由 `data` 内的专属字段表达；管理后台、商户后台和服务间接口继续使用既有内部契约。
2. `ApiResult` 只用于轻量基础接口，例如健康检查、简单回调 ACK、临时基础探针。不要在需要支付业务码、响应加密、分页或业务数据的接口中使用。
3. Controller 可使用静态导入 `success` 简化写法，但必须保留数据语义：

```java
return success(service.query(request)); // 有业务 data
return success();                       // 无业务 data，仅表示操作成功
```

4. 禁止把有返回数据的接口统一改成 `success()`。这会丢失响应 `data`，并破坏 OpenAPI 响应加密、前端渲染和服务间调用。
5. 失败响应由统一异常处理或 `CommonResult.error(...)` 构造，业务代码不要返回成功码表达失败。

## 错误码治理

1. 对外 OpenAPI V1.3 的 48 个稳定公开码统一定义在 `component-core` 的 `OpenApiResultCode`，完整治理元数据维护在 `OpenApiResultDefinitionRegistry`；`ApiResultEnum` 仅保留内部调用与历史兼容，不能作为新增公开码入口。Controller、ApplicationService 和安全拦截器不得直接拼接新的码字符串，也不得以 HTTP 状态码、异常类名或 message 反推业务码。
2. 服务间内部接口统一使用 `ServiceException` 表达失败，并由 `IResult`、`ApiResultEnum` 和 `CommonResult` 传递结构化 `code/message/data`。调用方只能按 `code` 分支，禁止按 `message` 文案等值判断。
3. Admin 和 Merchant 当前为兼容既有前端及内部调用协议，继续复用 `ApiResultEnum`，尚未维护独立的完整错误码枚举。未经接口版本设计评审，不得在这两个服务中复制或重新编号同义错误码。
4. OpenAPI V1.3 使用固定五位码：`A` 表示请求与接入结果，`C` 表示商户配置，`T` 表示交易事实或平台业务判断，`F1000/F2000/F3000` 分别表示已确认渠道问题、平台系统故障和渠道调用未确认。码的首字母和数字不编码 HTTP 状态，也不能单独决定交易终态、重试、入账或履约。
5. 新增或调整对外返回码必须同时更新 `OpenApiResultCode`、治理注册表、三类受众文案、公开码表、SDK 和回归测试。已发布编码不得改变含义、复用于其他场景或仅修改 message 掩盖语义变化；旧码迁移必须经过 `OpenApiErrorMapper` 或交易结果投影器并保留来源证据。
6. OpenAPI 外层结果与内层交易结果必须分离：已形成约定业务响应时外层为 `A0000`，交易成功、处理中、等待或失败由业务对象内的 `code` 和真实状态共同表达；`F1000/F2000/F3000` 不得反向覆盖状态机事实。
7. 对外 message 只提供稳定、非敏感的概要描述，不得包含内网地址、DNS 结果、密钥、SQL、异常栈或渠道原文；INTERNAL、MERCHANT、PAYER 必须由服务端可信入口选择，客户端参数不得提升受众权限，详细定位信息仅进入脱敏审计日志。

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
2. 每张业务表必须有稳定主键和创建/更新时间。字段名沿用所属表和模块已采用的 `create_time/update_time`、`created_at/updated_at` 或历史约定，禁止在同一张表内混用命名风格。
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
