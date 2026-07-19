# Nacos 配置拆分规范

各服务本地只保留服务名、端口、激活环境和 Nacos 连接信息。Redis、RocketMQ、数据库、分表、Seata、XXL-JOB 等基础设施配置统一放在 Nacos Config。

Nacos DataId 统一使用标准 YAML 后缀 `.yaml`。dev 环境使用命名空间 `dev`，命名空间 ID 为 `324ad8dc-58d0-4d0d-b264-24a9f951a2b0`；test、uat、prod 环境分别使用各自环境命名空间，不再使用 `public`。

## DataId 约定

| DataId | 用途 |
| --- | --- |
| `{service-name}-{env}.yaml` | 单服务专属配置 |
| `common-{env}.yaml` | 所有服务共享配置 |
| `dataSource-{env}.yaml` | 数据库与连接池配置 |
| `sharding-{env}.yaml` | 分表配置 |
| `redis-{env}.yaml` | Redis Cluster 配置 |
| `rocketmq-{env}.yaml` | RocketMQ 配置 |
| `seata-{env}.yaml` | Seata 配置 |
| `xxl-job-{env}.yaml` | XXL-JOB 配置 |

`env` 可选值：`dev`、`test`、`uat`、`prod`。

## 本地服务配置

各服务的 `application-{env}.yml` 只负责连接 Nacos，并通过 `spring.config.import` 拉取上述 DataId。

本地 `application.yml` 与 `application-{env}.yml` 的职责边界：

- `application.yml`：只保留 `server.port`、`spring.application.name`、`spring.profiles.active`、`spring.main` 等启动入口配置。
- `application-dev.yml`、`application-test.yml`、`application-uat.yml`、`application-prod.yml`：只保留当前环境的 Nacos 地址、命名空间、账号密码、`file-extension: yaml` 和 `spring.config.import`。
- 禁止在本地 yml 中写死 Redis、RocketMQ、数据库、分表、Seata、XXL-JOB 等业务环境配置；这些配置必须进入对应的 Nacos yaml DataId。
- `service-gateway` 是接入层例外：只拉取 `service-gateway-{env}.yaml` 和 `common-{env}.yaml`，不引入 `dataSource`、`sharding`、`redis`、`rocketmq`、`seata`、`xxl-job`，避免网关耦合业务基础设施。

Nacos yaml 的职责边界：

- `{service-name}-{env}.yaml`：单服务个性配置，例如 `service-admin-dev.yaml`、`service-merchant-dev.yaml`、`service-openapi-dev.yaml`、`service-payment-dev.yaml`、`service-risk-dev.yaml`、`service-gateway-dev.yaml`。
- `common-{env}.yaml`：所有服务共享配置，例如时间格式、管理端点、链路头名称。
- `service-gateway-{env}.yaml`：只放网关接入层说明、白名单路径和观测规则，不放数据库、Redis、MQ、Seata、分表配置。
- `service-risk-{env}.yaml`：只放风控服务内部鉴权、健康检查白名单和服务专属规则骨架配置；接入规则库、名单库、Redis 或 MQ 后再拆入对应公共 DataId。
- `dataSource-{env}.yaml`：主从数据源、连接池、MyBatis-Plus。
- `sharding-{env}.yaml`：参与分表的逻辑表、分表字段、起始表、结束表和物理表命名格式。
- `redis-{env}.yaml`、`rocketmq-{env}.yaml`、`seata-{env}.yaml`、`xxl-job-{env}.yaml`：对应中间件配置。

## Redis

Redis 按集群模式配置，禁止在业务服务本地写死单节点 Redis 地址。

## Dev 基础设施默认值

本地 dev 环境默认连接以下基础设施，生产和预发环境必须通过环境变量或独立 Nacos DataId 覆盖：

| 组件 | 默认地址 | 默认账号 | 说明 |
| --- | --- | --- | --- |
| MySQL | `127.0.0.1:3306/payment_acquiring` | `root` | `master`、`slave_1`、`slave_2` 先指向同一个库，驱动使用 MySQL Connector/J 8.4.0，后续从库就绪后替换从库 URL。 |
| Redis | `127.0.0.1:6379` | 无用户名 | dev 默认按单机无密码接入；如本地 Redis 开启鉴权，可通过 `REDIS_PASSWORD` 环境变量覆盖。 |
| Nacos | `127.0.0.1:8848` | `nacos` | dev 默认 namespace ID 为 `324ad8dc-58d0-4d0d-b264-24a9f951a2b0`，对应命名空间 `dev`。 |

读请求如需走从库，可在 service 或 mapper 方法上使用 `@DS(DataSourceName.SLAVE)`；未声明时默认走 `master`。

## 数据库与分表

数据库时区统一使用 UTC+8，即 `Asia/Shanghai`。JDBC URL 必须显式携带 `serverTimezone=Asia/Shanghai`。

数据库支持一主多从：

- `master`：写库，负责新增、更新、删除。
- `slave`：读库分组，负责查询、统计、报表等读请求。
- `slave_1`、`slave_2`：当前 dev 默认都连接同一个 MySQL，生产环境按真实从库拆分。

分表算法代码位置：

```text
component-library/component-db/src/main/java/com/scott/payment/component/db/sharding/PaymentOrderShardingAlgorithm.java
component-library/component-db/src/main/java/com/scott/payment/component/db/sharding/PaymentQuarterShardingProperties.java
```

`PaymentQuarterShardingProperties` 通过 `@ConfigurationProperties(prefix = "global-payment.sharding")` 绑定 Nacos 中的 `global-payment.sharding` 节点。

测试入口位置：

```text
component-library/component-db/src/test/java/com/scott/payment/component/db/sharding/PaymentOrderShardingAlgorithmTest.java
service-openapi/src/test/java/com/scott/payment/openapi/OpenApiApplicationTests.java
service-openapi/src/test/java/com/scott/payment/openapi/MerchantOpenApiEndToEndTests.java
```

分表统一按季度分表。所有需要分表的业务表都必须传入交易时间字段 `transaction_date_time`，表名格式为：

```text
{logical_table}_{yyyy}{QQ}
```

其中 `QQ` 表示季度编号，`Q1 -> 01`、`Q2 -> 02`、`Q3 -> 03`、`Q4 -> 04`。示例：`transaction_date_time = 2026-05-29 10:30:00` 时，`test_transaction` 表路由到 `test_transaction_202602`。

分表起始表和结束表在 `sharding-{env}.yaml` 中配置，每个环境独立维护：

```yaml
global-payment:
  sharding:
    strategy: quarter
    database-timezone: Asia/Shanghai
    sharding-column: transaction_date_time
    tables:
      transaction:
        enabled: true
        logical-table: test_transaction
        template-table: test_transaction
        id-column: id
        sharding-column: transaction_date_time
        start-year: 2026
        start-quarter: 1
        end-year: 2035
        end-quarter: 4
        table-name-format: "%s_%d%02d"
        actual-data-source: master
```

字段说明：

- `enabled`：控制当前逻辑表是否参与分表。
- `template-table`：物理表建表模板，自动预建表通过 `CREATE TABLE target LIKE template` 复制结构。
- `id-column`：物理表自增主键字段，当前默认 `id`。
- `start-year` / `start-quarter`：当前环境的起始物理表。
- `end-year` / `end-quarter`：当前环境已经准备好的最后一张物理表，后续追加表时扩展这里。
- `table-name-format`：物理表命名规则，默认生成 `test_transaction_202602` 这种表名；禁止使用旧格式 `%s_%d_q%d`。
- `actual-data-source`：物理表所在数据源，当前默认在 `master`。
