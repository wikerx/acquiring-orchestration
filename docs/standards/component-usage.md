# 公共组件使用说明

本文档说明当前支付框架中 `component-library` 的基础组件职责和调用方式。所有组件默认遵守 Java 17、Spring Boot 3、阿里巴巴 Java 开发规范和支付系统敏感数据保护规则。

## 调用链路

商户请求完整链路：

```text
merchant/client
  -> service-gateway
  -> service-openapi
  -> service-payment
```

- `service-gateway`：只负责路径白名单、路由、基础响应头和兜底错误，不加载数据库、Redis、MQ、Seata、分表配置。
- `service-openapi`：负责 JWT 鉴权、JWT jti 防重放、请求体解密、公共参数校验、商户基础信息校验，然后调用支付核心服务。
- `service-payment`：负责收单交易创建、订单号生成、交易状态初始化、异步事件发送。
- `service-gateway` 使用 `spring-cloud-starter-gateway-server-webflux` 和 `spring-cloud-starter-loadbalancer`；`lb://service-openapi` 必须依赖 LoadBalancer 才能从 Nacos 实例列表完成转发。

## 数据源配置

`dataSource-{env}.yaml` 中 `spring.datasource.dynamic.strategy` 必须配置动态数据源策略类名：

```yaml
spring:
  datasource:
    dynamic:
      strategy: com.baomidou.dynamic.datasource.strategy.LoadBalanceDynamicDataSourceStrategy
```

不能写 `round_robin` 这种别名。新版 `dynamic-datasource` 会把该字段绑定为 `Class<DynamicDataSourceStrategy>`，错误别名会导致服务启动失败。

## Redis 组件

Redis 基础接口在 `component-redis`：

- `CacheService`：字符串缓存读写。
- `IdempotentService`：基于 `SET NX EX` 的幂等控制。
- `RedisLockService`：基于 Redis 的分布式锁，释放锁时会校验锁值。
- `RedisOrderNoGenerator`：基于 Redis 自增序列生成分布式订单号。
- `RedisIdentityService`：平台业务标识、每日 STAN、每日递减 STAN 生成。
- `RedisDeduplicationService`：基于 Redis Set 的去重、唯一 ARN、每日文件序号生成。

按 Redis 数据结构拆分的通用接口：

- `RedisStringService`：String 写入、读取、TTL、删除、自增、自减、`setIfAbsent`。
- `RedisHashService`：Hash 单字段、多字段、字段查询、字段删除、字段自增、TTL。
- `RedisListService`：List 左/右压入、批量压入、范围查询、弹出、按下标修改、删除元素。
- `RedisSetService`：Set 添加、成员查询、成员判断、删除、TTL。
- `RedisZSetService`：ZSet 添加、分数查询、排名查询、分数范围查询、加分、按排名/分数删除。

示例：

```java
boolean firstRequest = idempotentService.acquire("payment:create:" + merchantOrderNo, 600);
if (!firstRequest) {
    throw new ServiceException(ApiResultEnum.ORDER_ALREADY_EXISTS);
}
```

Redis 序列化统一由 `RedisTemplateConfig` 配置：

- key/hashKey：字符串序列化；
- value/hashValue：JSON 序列化；
- 支持 Java 17 时间类型。
- Redis 工具实现类使用条件装配：只有存在 `RedisTemplate` 或 `StringRedisTemplate` 时才注册，避免 OpenAPI、Gateway 等暂时不依赖 Redis 的服务被工具包阻塞启动。

常用调用示例：

```java
redisStringService.set("payment:merchant:200045", merchantInfo, Duration.ofHours(2));

redisHashService.put("payment:merchant:route:200045", "defaultChannel", "checkout", Duration.ofDays(1));

redisListService.rightPush("payment:notify:queue", notifyTask);

boolean duplicate = redisDeduplicationService.checkAndAdd(
        "payment:dedup:trade:" + merchantId,
        merchantOrderNo,
        Duration.ofDays(1)
);

String stan = redisIdentityService.nextDailyStan("MASTERCARD", "123456");
```

当前未引入 Redisson，去重、序列号、锁均使用 Spring Data Redis 实现。后续如果需要可重入锁、公平锁、布隆过滤器、
读写锁等能力，再单独引入 Redisson，并放在独立实现中，避免基础组件默认变重。

## MQ 组件

MQ 基础接口在 `component-mq`：

- `MqProducer`：统一消息发送入口；
- `RocketMqProducer`：RocketMQ 可用时使用；
- `NoopMqProducer`：RocketMQ 未启用时自动降级，避免本地开发和单元测试因 MQ 缺失启动失败。

示例：

```java
BaseMqMessage message = new BaseMqMessage();
message.setMessageId(transactionId);
message.setCreatedAt(LocalDateTime.now());
mqProducer.send(MqTopic.PAYMENT_EVENT, "TRANSACTION_CREATED", message);
```

## 订单号

当前提供两种订单号入口：

- `PaymentOrderNoGenerator`：本地 JVM 订单号工具，适合模拟接口、单元测试、轻量开发。
- `RedisOrderNoGenerator`：Redis 分布式序列订单号，适合多实例部署。

格式示例：

```text
PA202605312040001230001
```

其中 `PA` 表示收单支付，时间使用 `Asia/Shanghai`，后缀为自增序列。

## OpenAPI 调用 Payment

`service-openapi` 通过 `PaymentInternalClient` 调用 `service-payment`：

```yaml
openapi:
  payment-client:
    remote-enabled: true
```

测试环境可以设置：

```yaml
openapi:
  payment-client:
    remote-enabled: false
```

这样 OpenAPI 单元测试不会依赖 `service-payment` 进程，但 dev 联调和生产应保持远程调用打开。

当 `remote-enabled=false` 时，`service-openapi` 会在本地生成模拟平台订单号和 `RECEIVED` 状态，方便商户侧
验证响应解析、日志追踪和加解密闭环；该模式只用于单元测试或单服务本地调试。

内部微服务 URL 不写入 Nacos，也不写入参数设置表。`http://service-payment`、`/internal/payment/**`
这类服务名和接口路径属于代码级服务契约，由对应内部 REST 客户端常量维护；Nacos 只维护远程调用开关、
超时、重试、内部 HMAC 密钥等环境级参数。业务运行期参数，例如 Hosted Checkout 前端域名
`platform.checkout.frontend-base-url`，才进入参数设置表。

## ISO 国家与币种工具

`component-core` 提供 ISO 识别工具，方便外卡收单接口做国家、币种、浏览器语言和金额辅币位校验：

- `IsoCountryResolver`：支持按国家二位字母、三位字母、三位数字、英文全称、英文简称、中文全称、中文别名、浏览器语言识别国家。
- `IsoCountryInfo`：输出 alpha2、alpha3、numeric、英文全称、英文简称、中文名、七大洲、国旗图标、主要语言和默认币种。
- `IsoCurrencyResolver`：支持按 ISO 4217 三位字母、三位数字、英文名、中文名、币种符号识别币种。
- `IsoCurrencyInfo`：输出币种三位字母、三位数字、英文名、中文名、辅币位、最小金额、币种符号和最小单位换算倍数。

说明：ISO 4217 没有标准“两位字母币种代码”，支付接口应使用三位字母，例如 `USD`、`CNY`、`JPY`。当前代码内置 249 个 ISO 3166-1 国家/地区和 233 个 JDK 当前可用 ISO 4217 币种；数据库初始化脚本在 `docs/sql/base-iso-dictionary-schema.sql`，表名为 `base_iso_country`、`base_iso_currency`。国家默认币种通过 `base_iso_country.currency_alpha3_code` 关联 `base_iso_currency.alpha3_code`。

示例：

```java
IsoCountryInfo countryInfo = IsoCountryResolver.resolve("USA")
        .orElseThrow();

IsoCurrencyInfo currencyInfo = IsoCurrencyResolver.resolve("840")
        .orElseThrow();

BigDecimal minorAmount = IsoCurrencyResolver.toMinorUnit(new BigDecimal("123.45"), currencyInfo);
```

## 金额单位

当前基础链路为了保持接口示例简单，默认按两位小数币种把主单位金额转换为最小单位：

```text
123.45 USD -> 12345
```

真实生产交易需要按 ISO 4217 币种精度配置处理，例如 JPY 为 0 位小数、KWD 为 3 位小数。后续接入通道前，
建议将币种精度做成基础表或 Nacos 公共配置，避免不同币种金额转换错误。
