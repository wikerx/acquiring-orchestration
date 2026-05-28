# Global Payment Architecture 架构设计说明

## 1. 架构目标

本项目用于构建一套基于 Spring Cloud 的全球支付系统架构，覆盖管理后台、商户系统、收银台、收单交易、代付交易、渠道适配库、定时任务、Redis、RocketMQ、MySQL 主从数据源以及交易核心表分表能力。

核心设计原则：

1. 前端与外部请求统一进入 `service-gateway`。
2. 业务服务以 `service-*` 命名，且表示可独立部署的微服务。
3. 公共能力统一收敛到 `component-library` 下的 `component-*` 模块。
4. 渠道适配以 `channel-library` 聚合为 jar 模块，由 `service-payment`、`service-payout` 引入。
5. `component-*` 模块只能作为基础组件被引用，不能依赖任何业务服务，避免循环依赖。
6. 业务服务之间不共享数据库表的直接写入能力，谁拥有业务域，谁负责该业务域的数据。
7. 跨服务同步调用使用 OpenFeign，异步解耦使用 RocketMQ。
8. Redis 主要用于缓存、分布式锁、幂等、Nonce 防重放。
9. MySQL 支持主从读写分离，交易核心表预留分表能力。
10. 短期任务调度使用 XXL-JOB，后续可替换或扩展为更完整的调度平台。

## 2. 推荐项目结构

```text
global-payment-architecture
├── pom.xml
├── README.md
│
├── component-library
│   ├── pom.xml
│   ├── component-core
│   ├── component-web
│   ├── component-security
│   ├── component-db
│   ├── component-redis
│   ├── component-mq
│   └── component-job
│
├── channel-library
│   ├── pom.xml
│   ├── payment-channel-library
│   └── payout-channel-library
│
├── service-gateway
├── service-admin
├── service-merchant
├── service-checkout
├── service-openapi
├── service-payment
├── service-payout
├── service-job
└── docs
    ├── architecture
    ├── database
    ├── api
    └── deployment
```

## 3. 模块分层说明

### 3.1 component-library

`component-library` 是组件库父模块，只做依赖聚合和版本管理，不写业务代码。

它下面的 `component-*` 模块是所有业务服务的公共基础能力来源。

建议依赖方向：

```text
service-*  --->  component-*
component-* 不能依赖 service-*
```

禁止出现：

```text
component-security ---> service-merchant
component-db       ---> service-payment
component-mq       ---> service-payment
```

否则后面一定会出现循环依赖。

### 3.2 channel-library

`channel-library` 是渠道适配库父模块，只做渠道 SDK、渠道报文转换、渠道签名、渠道错误码映射和渠道调用适配聚合，不作为微服务部署。

建议依赖方向：

```text
service-payment -> payment-channel-library -> component-*
service-payout  -> payout-channel-library  -> component-*
```

禁止出现：

```text
payment-channel-library -> service-payment
payout-channel-library  -> service-payout
```

否则渠道库会反向依赖交易核心，后续会产生循环依赖和发布耦合。

## 4. component-* 模块设计

### 4.1 component-core

定位：最底层公共核心包。

允许被所有模块依赖。

建议包结构：

```text
component-core
└── src/main/java/com/sinopay/payment/component/core
    ├── constant
    ├── enums
    ├── exception
    ├── model
    │   ├── ApiResult.java
    │   ├── PageRequest.java
    │   └── PageResult.java
    ├── trace
    │   └── TraceContext.java
    └── util
```

职责：

1. 统一返回对象；
2. 统一异常；
3. 错误码；
4. 基础枚举；
5. 分页对象；
6. TraceId 上下文；
7. 通用工具类。

依赖规则：

```text
component-core 不依赖任何内部 component 模块。
```

### 4.2 component-web

定位：Web 层公共能力。

建议包结构：

```text
component-web
└── src/main/java/com/sinopay/payment/component/web
    ├── config
    ├── filter
    │   └── TraceIdFilter.java
    ├── interceptor
    │   └── RequestLogInterceptor.java
    ├── handler
    │   └── GlobalExceptionHandler.java
    └── advice
```

职责：

1. 全局异常处理；
2. TraceId 过滤器；
3. 请求日志拦截器；
4. 参数校验异常处理；
5. WebMvc 通用配置。

推荐依赖：

```text
component-web -> component-core
```

禁止依赖：

```text
component-web -> component-security
component-web -> service-*
```

### 4.3 component-security

定位：安全、签名、加密、鉴权、防重放。

建议包结构：

```text
component-security
└── src/main/java/com/sinopay/payment/component/security
    ├── config
    ├── crypto
    │   ├── AesEncryptor.java
    │   └── HmacSha256Signer.java
    ├── sign
    │   ├── SignatureRequest.java
    │   ├── SignatureVerifier.java
    │   └── MerchantSignatureVerifier.java
    ├── replay
    │   ├── NonceValidator.java
    │   └── TimestampValidator.java
    ├── auth
    │   ├── InternalAuthChecker.java
    │   └── ExternalApiAuthChecker.java
    └── callback
        └── ChannelCallbackVerifier.java
```

职责：

1. 内部 API 鉴权；
2. 外部商户 API 签名；
3. timestamp + nonce 防重放；
4. HMAC-SHA256 签名；
5. AES 加解密；
6. 渠道回调验签扩展接口；
7. 商户 API Key / Secret 校验模型。

推荐依赖：

```text
component-security -> component-core
component-security -> component-redis
```

说明：

`component-security` 可以依赖 `component-redis`，用于 nonce 防重放。但不要反过来让 `component-redis` 依赖 `component-security`。

### 4.4 component-db

定位：数据库、主从、分表、事务、MyBatis-Plus。

建议包结构：

```text
component-db
└── src/main/java/com/sinopay/payment/component/db
    ├── config
    │   ├── MybatisPlusConfig.java
    │   ├── DataSourceConfig.java
    │   └── TransactionConfig.java
    ├── entity
    │   └── BaseEntity.java
    ├── datasource
    │   ├── DataSourceType.java
    │   ├── DataSourceContextHolder.java
    │   └── DynamicDataSource.java
    ├── sharding
    │   ├── ShardingKey.java
    │   └── PaymentOrderShardingAlgorithm.java
    └── handler
        └── MybatisMetaObjectHandler.java
```

职责：

1. MyBatis-Plus 配置；
2. 主从数据源；
3. 读写分离；
4. 分表策略预留；
5. 事务配置；
6. 基础实体字段。

推荐依赖：

```text
component-db -> component-core
```

禁止依赖：

```text
component-db -> component-redis
component-db -> component-mq
component-db -> service-*
```

### 4.5 component-redis

定位：Redis 基础能力。

建议包结构：

```text
component-redis
└── src/main/java/com/sinopay/payment/component/redis
    ├── config
    │   └── RedisConfig.java
    ├── cache
    │   └── CacheService.java
    ├── lock
    │   └── RedisLockService.java
    ├── idempotent
    │   └── IdempotentService.java
    └── nonce
        └── NonceCacheService.java
```

职责：

1. RedisTemplate 配置；
2. 缓存工具；
3. 分布式锁；
4. 幂等 Key 管理；
5. Nonce 缓存。

推荐依赖：

```text
component-redis -> component-core
```

禁止依赖：

```text
component-redis -> component-security
component-redis -> service-*
```

### 4.6 component-mq

定位：RocketMQ 基础能力。

建议包结构：

```text
component-mq
└── src/main/java/com/sinopay/payment/component/mq
    ├── config
    │   └── RocketMqConfig.java
    ├── constant
    │   ├── MqTopic.java
    │   └── MqTag.java
    ├── message
    │   ├── BaseMqMessage.java
    │   ├── PaymentCreatedMessage.java
    │   └── PaymentSucceededMessage.java
    ├── producer
    │   └── MqProducer.java
    └── consumer
        └── AbstractIdempotentConsumer.java
```

职责：

1. RocketMQ Producer 封装；
2. RocketMQ Consumer 基类；
3. Topic / Tag 常量；
4. 消息体基础模型；
5. 消费幂等模板；
6. 消息重试处理模板。

推荐依赖：

```text
component-mq -> component-core
component-mq -> component-redis
```

说明：

消费幂等可以使用 Redis，因此 `component-mq` 可以依赖 `component-redis`。

禁止依赖：

```text
component-mq -> service-*
```

### 4.7 component-job

定位：XXL-JOB 基础能力。

建议包结构：

```text
component-job
└── src/main/java/com/sinopay/payment/component/job
    ├── config
    │   └── XxlJobConfig.java
    ├── model
    │   └── JobExecuteResult.java
    ├── handler
    │   └── AbstractJobHandler.java
    └── log
        └── JobLogHelper.java
```

职责：

1. XXL-JOB 配置；
2. 任务执行器基类；
3. 任务执行结果模型；
4. 任务日志工具。

推荐依赖：

```text
component-job -> component-core
```

## 5. 业务服务模块设计

### 5.1 service-gateway

定位：统一接入网关。

职责：

1. 后台管理系统 API 接入；
2. 商户系统 API 接入；
3. 收银台 API 接入；
4. 商户开放 API 接入；
5. 渠道回调 API 接入；
6. 内部 API 与外部 API 路由区分；
7. 商户签名校验；
8. timestamp + nonce 防重放；
9. 渠道回调路径预留；
10. 统一 TraceId。

建议包结构：

```text
service-gateway
└── src/main/java/com/sinopay/payment/gateway
    ├── GatewayApplication.java
    ├── config
    ├── filter
    │   ├── TraceGatewayFilter.java
    │   ├── InternalApiAuthFilter.java
    │   ├── ExternalApiSignFilter.java
    │   └── ChannelCallbackFilter.java
    ├── route
    └── constants
```

推荐依赖：

```text
service-gateway
  -> component-core
  -> component-security
  -> component-redis
```

### 5.2 service-admin

定位：后台管理系统接口。

职责：

1. 商户管理；
2. 店铺管理；
3. 渠道配置管理；
4. 支付方式管理；
5. 风控规则管理；
6. 交易查询；
7. 对账查询；
8. 结算查询；
9. 账务查询入口。

建议包结构：

```text
service-admin
└── src/main/java/com/sinopay/payment/admin
    ├── AdminApplication.java
    ├── controller
    ├── application
    ├── client
    ├── dto
    └── converter
```

说明：

`service-admin` 主要做后台接口聚合，不建议直接承载过多核心交易逻辑。

### 5.3 service-merchant

定位：商户系统接口。

职责：

1. 商户资料查询；
2. 店铺资料查询；
3. API 密钥管理；
4. 商户订单查询；
5. 退款订单查询；
6. 代付订单查询；
7. 商户账单查询；
8. 商户结算查询。

建议包结构：

```text
service-merchant
└── src/main/java/com/sinopay/payment/merchant
    ├── MerchantApplication.java
    ├── controller
    ├── application
    ├── domain
    ├── infrastructure
    ├── mapper
    ├── entity
    └── dto
```

### 5.4 service-checkout

定位：收银台接口服务。

职责：

1. 查询支付订单展示信息；
2. 提交付款人支付信息；
3. 查询可用支付方式；
4. 本地支付方式信息展示；
5. OXXO / SPEI 等支付信息展示能力；
6. 调用 `service-payment` 完成支付确认。

建议包结构：

```text
service-checkout
└── src/main/java/com/sinopay/payment/checkout
    ├── CheckoutApplication.java
    ├── controller
    ├── application
    ├── client
    ├── dto
    └── vo
```

### 5.5 service-openapi

定位：商户开放 API、商户通知与渠道回调入口服务。

职责：

1. 商户侧收单、代付、查询、退款等开放 API 入口；
2. 请求头验签；
3. 报文数据解密；
4. 商户号、AppId、API Key 等基础参数校验；
5. 商户产品权限、接口权限和限额前置校验；
6. timestamp + nonce 防重放；
7. 幂等键校验；
8. 外部请求模型转换为内部交易命令；
9. 渠道侧回调入口、基础验签和报文验真；
10. 平台向商户发送支付、退款、代付结果通知；
11. 开放 API 请求日志、响应日志、渠道回调入口日志和通知日志记录。

建议包结构：

```text
service-openapi
└── src/main/java/com/sinopay/payment/openapi
    ├── OpenApiApplication.java
    ├── annotation
    │   └── v1
    ├── aspect
    │   └── v1
    ├── api
    │   └── rest
    │       └── v1
    │           ├── dto
    │           │   ├── body
    │           │   ├── converter
    │           │   └── header
    │           ├── heartbeat
    │           ├── notify
    │           ├── payment
    │           └── payout
    ├── application
    ├── client
    ├── notify
    ├── service
    │   └── impl
    └── vo
```

包名约束：Java 包名全小写；实现类放在 `service.impl`，不使用 `serviceImpl`；请求/响应对象统一使用 `DTO`、`VO` 后缀。

说明：

商户侧外部请求统一链路：

```text
merchant/client -> service-gateway -> service-openapi -> service-payment/service-payout
```

渠道侧回调也统一进入 `service-openapi`，再分发给交易域服务处理状态：

```text
channel callback -> service-gateway -> service-openapi -> service-payment/service-payout
```

### 5.6 service-payment

定位：收单交易核心。

职责：

1. 创建支付订单；
2. 支付订单状态机；
3. 收银台支付确认；
4. 支付结果处理；
5. 退款申请；
6. 退款状态处理；
7. 支付订单查询；
8. 支付渠道适配库调用；
9. 支付 MQ 事件发送；
10. 交易核心表分表预留。

建议包结构：

```text
service-payment
└── src/main/java/com/sinopay/payment/payment
    ├── PaymentApplication.java
    ├── api
    │   └── internal
    ├── application
    │   ├── command
    │   ├── query
    │   └── service
    ├── domain
    │   ├── order
    │   ├── refund
    │   └── state
    ├── infrastructure
    │   ├── channel
    │   ├── client
    │   ├── mapper
    │   ├── repository
    │   └── mq
    ├── entity
    ├── dto
    └── converter
```

### 5.7 service-payout

定位：代付交易核心。

职责：

1. 创建代付订单；
2. 代付订单状态机；
3. 代付风控校验；
4. 代付提交渠道；
5. 代付结果处理；
6. 代付订单查询；
7. 代付渠道适配库调用；
8. 代付 MQ 事件发送；
9. 代付核心表分表预留。

建议包结构：

```text
service-payout
└── src/main/java/com/sinopay/payment/payout
    ├── PayoutApplication.java
    ├── api
    │   └── internal
    ├── application
    ├── domain
    │   ├── order
    │   └── state
    ├── infrastructure
    │   ├── channel
    │   ├── client
    │   ├── mapper
    │   ├── repository
    │   └── mq
    ├── entity
    ├── dto
    └── converter
```

### 5.8 channel-library

定位：渠道适配聚合库，不作为独立微服务部署。

职责：

1. 收单支付渠道请求适配；
2. 收单退款渠道请求适配；
3. 代付渠道请求适配；
4. 渠道报文转换；
5. 渠道签名、加密和验签支持；
6. 渠道错误码映射；
7. 渠道 SDK 调用封装；
8. 供 `service-payment`、`service-payout` 以 jar 方式引入。

建议包结构：

```text
channel-library
├── payment-channel-library
│   └── src/main/java/com/sinopay/payment/channel/payment
│       ├── adapter
│       ├── model
│       ├── route
│       └── converter
└── payout-channel-library
    └── src/main/java/com/sinopay/payment/channel/payout
        ├── adapter
        ├── model
        ├── route
        └── converter
```

说明：

1. `payment-channel-library` 只服务收单支付域，由 `service-payment` 引入。
2. `payout-channel-library` 只服务代付域，由 `service-payout` 引入。
3. 渠道回调入口仍属于对外接入面，统一进入 `service-openapi`。
4. 渠道回调进入后，交易状态处理归属 `service-payment` 或 `service-payout`。

### 5.9 service-job

定位：定时任务执行服务。

职责：

1. 支付订单超时关闭；
2. 渠道订单状态补偿查询；
3. 代付订单状态补偿查询；
4. 对账文件拉取任务预留；
5. 结算任务预留；
6. 清算任务预留。

建议包结构：

```text
service-job
└── src/main/java/com/sinopay/payment/job
    ├── JobApplication.java
    ├── handler
    │   ├── PaymentTimeoutCloseJob.java
    │   ├── PaymentStatusSyncJob.java
    │   └── PayoutStatusSyncJob.java
    ├── client
    └── config
```

## 6. 依赖关系建议

### 6.1 component 依赖关系

```text
component-core
  ↑
component-web
component-db
component-redis
component-job
  ↑
component-security
component-mq
```

更明确地说：

```text
component-web      -> component-core
component-db       -> component-core
component-redis    -> component-core
component-job      -> component-core
component-security -> component-core + component-redis
component-mq       -> component-core + component-redis
```

### 6.2 service 依赖关系

```text
service-gateway  -> component-core + component-security + component-redis
service-admin    -> component-core + component-web + component-security
service-merchant -> component-core + component-web + component-db + component-redis
service-checkout -> component-core + component-web + component-security + component-redis
service-openapi  -> component-core + component-web + component-security + component-redis + component-mq + component-db
service-payment  -> component-core + component-web + component-db + component-redis + component-mq + component-security + payment-channel-library
service-payout   -> component-core + component-web + component-db + component-redis + component-mq + component-security + payout-channel-library
service-job      -> component-core + component-job
```

### 6.3 channel-library 依赖关系

```text
payment-channel-library -> component-core + component-security
payout-channel-library  -> component-core + component-security
```

## 7. 数据归属建议

禁止设计统一的 `service-data`。

建议数据归属如下：

```text
service-merchant
  - merchant_info
  - merchant_store
  - merchant_api_key

service-openapi
  - openapi_request_log_xxxx
  - openapi_idempotent_record
  - merchant_notify_task
  - merchant_notify_log
  - openapi_error_mapping

service-payment
  - payment_order_xxxx
  - payment_refund_order_xxxx
  - payment_order_event_xxxx
  - payment_channel_config
  - payment_channel_route_rule
  - payment_channel_request_log_xxxx
  - payment_channel_callback_log_xxxx

service-payout
  - payout_order_xxxx
  - payout_order_event_xxxx
  - payout_channel_config
  - payout_channel_route_rule
  - payout_channel_request_log_xxxx
  - payout_channel_callback_log_xxxx

后续扩展：
service-reconciliation
  - channel_statement
  - reconciliation_result

service-settlement
  - merchant_settlement_order
  - settlement_batch

service-ledger
  - accounting_entry
  - account_balance
```

## 8. Codex 生成要求

请按以上结构生成一个 Spring Cloud 多模块 Maven 项目骨架。

要求：

1. 父工程 `global-payment-architecture` 管理所有版本；
2. `component-library` 是组件父模块；
3. 所有 `component-*` 是 jar 模块，不需要启动类；
4. 所有 `channel-*` 或 `*-channel-library` 是 jar 模块，不需要启动类；
5. 所有 `service-*` 是 Spring Boot 应用，需要启动类；
6. 每个模块生成标准包结构；
7. 先生成 `pom.xml`、启动类、基础配置类、示例 Controller；
8. 不需要完整业务逻辑；
9. 避免循环依赖；
10. 代码风格统一；
11. Java 8；
12. Spring Boot 3.x；
13. Spring Cloud；
14. Nacos、RocketMQ、Redis、MySQL、XXL-JOB 依赖先预留；
15. 支付交易表、代付交易表预留分表能力；
16. 商户开放 API 与渠道回调入口由 `service-openapi` 统一承接。

## 9. 当前落地说明

当前仓库骨架优先满足 Java 8 要求，因此实际采用：

1. Java 8；
2. Spring Boot 2.7.x；
3. Spring Cloud 2021.x；
4. Spring Cloud Alibaba 2021.x。

说明：Spring Boot 3.x 要求 Java 17 及以上；如果后续决定升级到 Java 17，再统一切换到 Spring Boot 3.x 与对应 Spring Cloud 版本。

## 10. 多环境配置与打包

系统环境统一分为：

1. `dev`：开发环境；
2. `test`：测试环境；
3. `uat`：预发布验收环境；
4. `prod`：生产环境。

根 `pom.xml` 通过 Maven profile 统一切换环境，默认启用 `dev`：

```bash
mvn -Pdev clean package
mvn -Ptest clean package
mvn -Puat clean package
mvn -Pprod clean package
```

各 `service-*` 模块资源目录统一包含：

```text
src/main/resources
├── application.yml
├── application-dev.yml
├── application-test.yml
├── application-uat.yml
├── application-prod.yml
├── application-sample.yml
├── banner.txt
├── seata.conf
└── log-config
    └── logback-spring.xml
```

`application.yml` 只保留服务名、端口和 profile 入口：

```yaml
spring:
  profiles:
    active: @profiles.active@
```

`application-{env}.yml` 只放 Nacos 连接信息和 `spring.config.import`。Redis Cluster、RocketMQ、数据库、分表、XXL-JOB、Seata 等外部依赖配置统一放到 Nacos Config 独立 DataId。

推荐 DataId：

```text
{service-name}-{env}.yml
common-{env}.yml
dataSource-{env}.yml
sharding-{env}.yml
redis-{env}.yml
rocketmq-{env}.yml
seata-{env}.yml
xxl-job-{env}.yml
```
