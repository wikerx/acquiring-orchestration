# Java 注释与链路日志基线报告

## 1. 基线环境

| 检查项 | 结果 |
|---|---|
| 工作目录 | `/Users/scott/Documents/code/ideaCodex/acquiring/acquiring-orchestration` |
| 当前分支 | `feature_scott_payment` |
| HEAD | `0c154776454dcffd5cca48cb8cc3ee647f266df9` |
| 工作区 | `git status --short` 无输出，执行阶段 0 前工作区干净 |
| 扫描范围 | 排除 `target/` 后的 `src/main/java`、`src/test/java`、`src/main/resources` |

> 说明：本报告为阶段 0 基线扫描结果。扫描脚本只用于定位问题，未自动改写 Java 注释或业务日志。字段注释、方法注释、`@param` 等项目存在语义判断空间，统计值用于后续分批治理排序，最终验收应以人工复核和专用校验脚本结果为准。

## 2. 总体统计

| 指标 | 数量 |
|---|---:|
| 生产 Java 文件 | 1038 |
| 测试 Java 文件 | 83 |
| Java 文件合计 | 1121 |
| 疑似缺少类级 Javadoc | 78 |
| 疑似重复类级 Javadoc | 263 |
| 疑似重复方法 Javadoc | 732 |
| 疑似缺少字段说明 | 1387 |
| 疑似 public/protected 方法缺少 Javadoc | 300 |
| `@classname` 与顶层类型不一致 | 0 |
| 命中空泛模板注释 | 5129 |
| 疑似 `@param` 与真实参数不一致 | 36 |
| void 方法疑似错误编写 `@return` | 3 |
| 不存在声明异常却编写 `@throws` | 0 |
| `@Slf4j` | 47 |
| `log.info` | 143 |
| `log.warn` | 53 |
| `log.error` | 2 |
| `log.debug` | 2 |
| `System.out` | 0 |
| `printStackTrace` | 0 |
| 疑似仅记录 `exception.getMessage()` 的 `log.error` | 1 |

## 3. 各模块统计

| 模块 | 生产 Java | 测试 Java | 缺类注释 | 重复类注释 | 重复方法注释 | 缺字段说明 | 缺 public/protected 方法注释 | 空泛模板注释 | `@Slf4j` | INFO | WARN | ERROR | DEBUG |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| channel-library | 54 | 11 | 7 | 3 | 0 | 48 | 20 | 3 | 5 | 20 | 2 | 0 | 0 |
| component-library | 257 | 26 | 30 | 67 | 234 | 16 | 4 | 1504 | 10 | 16 | 19 | 2 | 1 |
| service-admin | 324 | 10 | 22 | 117 | 372 | 468 | 66 | 2615 | 5 | 1 | 11 | 0 | 0 |
| service-checkout | 5 | 1 | 0 | 2 | 4 | 0 | 0 | 14 | 0 | 0 | 0 | 0 | 0 |
| service-gateway | 5 | 0 | 0 | 3 | 3 | 1 | 0 | 16 | 0 | 0 | 0 | 0 | 0 |
| service-job | 81 | 4 | 4 | 33 | 68 | 9 | 0 | 429 | 3 | 2 | 4 | 0 | 0 |
| service-merchant | 49 | 1 | 5 | 6 | 20 | 238 | 3 | 382 | 3 | 1 | 5 | 0 | 0 |
| service-openapi | 103 | 16 | 2 | 30 | 27 | 112 | 38 | 145 | 17 | 102 | 4 | 0 | 1 |
| service-payment | 139 | 12 | 6 | 0 | 0 | 493 | 169 | 1 | 4 | 1 | 8 | 0 | 0 |
| service-payout | 10 | 0 | 0 | 2 | 4 | 1 | 0 | 20 | 0 | 0 | 0 | 0 | 0 |
| service-risk | 11 | 2 | 2 | 0 | 0 | 1 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |

## 4. 高风险文件清单

| 排名 | 文件 | 主要问题 |
|---:|---|---|
| 1 | `service-admin/src/main/java/com/scott/payment/admin/service/impl/AdminExchangeRateServiceImpl.java` | 空泛模板注释 132 处，重复 Javadoc 58 处 |
| 2 | `service-admin/src/main/java/com/scott/payment/admin/application/exchange/AdminExchangeRateApplicationService.java` | 空泛模板注释 133 处，重复 Javadoc 56 处 |
| 3 | `component-library/component-redis/src/main/java/com/scott/payment/component/redis/zset/impl/RedisZSetServiceImpl.java` | 空泛模板注释 122 处，重复 Javadoc 32 处 |
| 4 | `service-merchant/src/main/java/com/scott/payment/merchant/controller/MerchantSystemController.java` | 空泛模板注释 151 处 |
| 5 | `service-admin/src/main/java/com/scott/payment/admin/dto/channel/ChannelAlertDTOs.java` | 疑似缺少字段说明 139 处 |
| 6 | `component-library/component-redis/src/main/java/com/scott/payment/component/redis/hash/impl/RedisHashServiceImpl.java` | 空泛模板注释 102 处，重复 Javadoc 32 处 |
| 7 | `service-merchant/src/main/java/com/scott/payment/merchant/service/impl/MerchantSystemServiceImpl.java` | 空泛模板注释 133 处，关键服务日志不足 |
| 8 | `service-payment/src/test/java/com/scott/payment/payment/service/impl/PaymentTransactionConsistencyBaselineTests.java` | 测试方法注释和字段注释缺口较多 |
| 9 | `service-merchant/src/main/java/com/scott/payment/merchant/dto/transaction/MerchantTransactionDTOs.java` | 疑似缺少字段说明 122 处 |
| 10 | `service-admin/src/main/java/com/scott/payment/admin/application/channel/AdminChannelApplicationService.java` | 空泛模板注释 97 处，缺少关键写操作日志 |

## 5. 重复注释最多的文件

| 文件 | 疑似重复 Javadoc 数量 |
|---|---:|
| `service-admin/src/main/java/com/scott/payment/admin/service/impl/AdminExchangeRateServiceImpl.java` | 58 |
| `service-admin/src/main/java/com/scott/payment/admin/application/exchange/AdminExchangeRateApplicationService.java` | 56 |
| `component-library/component-redis/src/main/java/com/scott/payment/component/redis/zset/impl/RedisZSetServiceImpl.java` | 32 |
| `component-library/component-redis/src/main/java/com/scott/payment/component/redis/hash/impl/RedisHashServiceImpl.java` | 32 |
| `service-admin/src/main/java/com/scott/payment/admin/application/monitor/AdminJobSchedulerApplicationService.java` | 28 |
| `service-admin/src/main/java/com/scott/payment/admin/application/merchant/AdminMerchantInfoApplicationService.java` | 26 |
| `service-admin/src/main/java/com/scott/payment/admin/service/impl/AdminMerchantInfoServiceImpl.java` | 26 |
| `component-library/component-core/src/main/java/com/scott/payment/component/core/card/CardNoGenerator.java` | 26 |
| `component-library/component-redis/src/main/java/com/scott/payment/component/redis/list/impl/RedisListServiceImpl.java` | 26 |
| `component-library/component-redis/src/main/java/com/scott/payment/component/redis/string/impl/RedisStringServiceImpl.java` | 26 |

## 6. 空泛注释最多的文件

| 文件 | 命中次数 |
|---|---:|
| `service-merchant/src/main/java/com/scott/payment/merchant/controller/MerchantSystemController.java` | 151 |
| `service-admin/src/main/java/com/scott/payment/admin/application/exchange/AdminExchangeRateApplicationService.java` | 133 |
| `service-merchant/src/main/java/com/scott/payment/merchant/service/impl/MerchantSystemServiceImpl.java` | 133 |
| `service-admin/src/main/java/com/scott/payment/admin/service/impl/AdminExchangeRateServiceImpl.java` | 132 |
| `component-library/component-redis/src/main/java/com/scott/payment/component/redis/zset/impl/RedisZSetServiceImpl.java` | 122 |
| `component-library/component-redis/src/main/java/com/scott/payment/component/redis/hash/impl/RedisHashServiceImpl.java` | 102 |
| `service-admin/src/main/java/com/scott/payment/admin/application/channel/AdminChannelApplicationService.java` | 97 |
| `service-admin/src/main/java/com/scott/payment/admin/service/impl/AdminEmailServiceImpl.java` | 91 |
| `service-admin/src/main/java/com/scott/payment/admin/application/email/AdminEmailApplicationService.java` | 87 |
| `component-library/component-redis/src/main/java/com/scott/payment/component/redis/list/impl/RedisListServiceImpl.java` | 82 |

## 7. 无日志的关键业务类

扫描规则按 `controller`、`application`、`service/impl`、`client`、`mq`、`handler`、`filter`、`interceptor`、`aspect`、`scheduler`、`job` 等路径关键词识别执行类；DTO、配置类、常量类可能被误报，阶段 6 前需要人工二次筛选。

| 模块 | 现状 |
|---|---|
| service-gateway | 仅发现 `GatewayClientIpHeaderFilter`，没有请求开始、路由完成、响应结束和耗时日志 |
| service-openapi | 测试日志较多，生产链路主要集中在认证切面和安全拦截事件记录，OpenAPI 请求入口到 payment 调用缺少完整 INFO 链路 |
| service-payment | 仅 4 个 `@Slf4j`、1 条 `log.info`、8 条 `log.warn`；核心 `PaymentTransactionServiceImpl`、准备服务、记录服务等交易阶段日志明显不足 |
| service-risk | 生产代码无 `@Slf4j` 和业务日志，风控请求、规则数量、命中规则、结论、耗时均缺少统一日志 |
| channel-library | MPGS 客户端已有部分请求/响应脱敏日志，但渠道调用日志字段还未覆盖统一事件、渠道结果、耗时和关联交易字段 |
| service-admin / service-merchant | 大量 Controller/ApplicationService/ServiceImpl 没有写操作和分页查询摘要日志，操作日志 MQ 消费者已有少量日志 |
| service-job | 存在 `TraceIdSupport` 和少量任务日志，但任务开始、分片、扫描区间、成功失败统计不完整 |

## 8. 当前 traceId 传播链路

| 链路点 | 当前发现 | 缺口 |
|---|---|---|
| 公共上下文 | `component-core` 存在 `TraceContext`，提供 `X-Trace-Id` 常量和 ThreadLocal 存取 | 未与 MDC 统一绑定；未看到请求过滤器统一清理 |
| Gateway | 存在 `GatewayClientIpHeaderFilter`，只处理 `X-Gateway-Client-Ip` | 未生成、校验、覆盖、响应回传 `X-Trace-Id`；未写 MDC；未记录路由耗时 |
| Servlet 服务 | 未发现统一 `OncePerRequestFilter` 绑定 `TraceContext` 和 MDC | 请求结束清理 MDC/ThreadLocal 缺失 |
| RestTemplate | 多个内部 RestClient 存在，但未发现统一 `ClientHttpRequestInterceptor` | 内部 HTTP 调用不会自动透传 `X-Trace-Id` |
| MQ | `BaseMqMessage` 仅有 `messageId` 和 `createdAt`；操作日志消息体有 `traceId` 字段 | 通用 MQ 基类未承载 traceId/retryCount；生产者未从 TraceContext/MDC 补齐；消费者未统一绑定和清理 MDC |
| 异步任务 | 未发现 `TaskDecorator` | 异步线程无法保证 traceId 传播和清理 |
| 定时任务 | `service-job` 有 `TraceIdSupport` 使用 MDC | 未形成跨 job handler、分片和 HTTP 调用的统一 traceId 策略 |
| 渠道调用 | MPGS 内部记录部分上下文 | 未看到平台 traceId 到渠道 correlationId 的统一策略；不得向第三方发送内部敏感头 |

## 9. 当前日志配置是否实际生效

所有服务均存在 `src/main/resources/log-config/logback-spring.xml`，各 profile 的 `application-*.yml` 均配置了 `logging.config: classpath:log-config/logback-spring.xml`。logback 统一包含：

- 控制台 appender；
- 滚动文件 appender；
- `ASYNC_FILE`；
- UTF-8；
- `Asia/Shanghai`；
- `traceId=%X{traceId:-}`；
- `spanId=%X{spanId:-}`；
- `applicationName`；
- `instanceId`；
- `maxFileSize`、`maxHistory=30`、`totalSizeCap`。

缺口：

1. 未发现独立 ERROR 文件 appender。
2. 日志格式包含 `spanId`，但代码未发现完整 span 实现，当前属于空占位。
3. Nacos 部署配置目录存在 `docs/deployment/nacos/*.yaml`，本轮只定位到服务配置文件，后续阶段 10 需要逐项检查是否覆盖 `logging.level`、`logging.config`、`LOG_PATH`、root level 等。
4. 配置具备 traceId 输出能力，但业务代码未统一写入 MDC，因此实际日志中 traceId 可能为空。

## 10. 后续分批修改清单

| 批次 | 范围 | 目标 | 风险 |
|---|---|---|---|
| 1 | `component-library/component-core`、`component-web`、`service-gateway` | 建立 traceId 生成、校验、MDC 绑定、响应回传和清理 | 中 |
| 2 | `component-library/component-http`、各内部 RestClient 配置 | 建立统一 RestTemplate 拦截器，自动透传 `X-Trace-Id` | 中 |
| 3 | `component-library/component-mq`、admin/merchant/payment MQ 消费者 | MQ 消息补 traceId/retryCount，消费者绑定和清理 MDC | 中 |
| 4 | `component-library/component-core` 或现有安全工具包 | 扩展统一日志脱敏入口，禁止脱敏失败回退原文 | 高 |
| 5 | `service-gateway`、`service-openapi` | 补入口、鉴权、解密、调用 payment、响应加密、耗时日志 | 高 |
| 6 | `service-payment`、`service-risk`、`channel-library` | 补完整支付、风控、渠道请求响应关键阶段日志，不改变状态机和金额规则 | 高 |
| 7 | `service-admin`、`service-merchant`、`service-job` | 补重要写操作、分页查询摘要、任务执行统计日志 | 中 |
| 8 | 全项目注释治理 | 先清理重复和空泛注释，再按真实职责补类/字段/方法注释 | 中 |
| 9 | `scripts/verify-java-comments.py`、`scripts/verify-logging-rules.py` | 建立质量门禁脚本，不自动写注释 | 低 |
| 10 | `docs/code-quality/*` | 输出后续设计、脱敏策略、最终验收报告 | 低 |

## 11. 阶段 0 结论

当前项目日志配置基础存在，但完整链路日志尚未形成：traceId 没有从 Gateway、Servlet、内部 HTTP、MQ、异步线程、定时任务到渠道调用形成闭环；核心支付和风控服务日志数量明显不足。注释问题集中在重复 Javadoc 和空泛模板注释，尤其是 `service-admin`、`component-library` 和部分 merchant 文件。下一阶段建议先执行注释清理中的重复/空泛注释删除与重写，再进入 traceId 基础设施建设，避免在大量模板注释上继续叠加日志改造。
