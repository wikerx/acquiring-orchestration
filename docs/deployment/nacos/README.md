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

## Redis

Redis 按集群模式配置，禁止在业务服务本地写死单节点 Redis 地址。

## Dev 基础设施默认值

本地 dev 环境默认连接以下基础设施，生产和预发环境必须通过环境变量或独立 Nacos DataId 覆盖：

| 组件 | 默认地址 | 默认账号 | 说明 |
| --- | --- | --- | --- |
| MySQL | `127.0.0.1:3306/payment_acquiring` | `root` | `master`、`slave_1`、`slave_2` 先指向同一个库，后续从库就绪后替换从库 URL。 |
| Redis | `127.0.0.1:6379` | 无用户名 | 默认密码从 `REDIS_PASSWORD` 读取，未设置时使用 dev 默认值。 |
| Nacos | `127.0.0.1:8848` | `nacos` | dev 默认 namespace ID 为 `324ad8dc-58d0-4d0d-b264-24a9f951a2b0`，对应命名空间 `dev`。 |

读请求如需走从库，可在 service 或 mapper 方法上使用 `@DS(DataSourceName.SLAVE)`；未声明时默认走 `master`。

## 数据库与分表

数据库时区统一使用 UTC+8，即 `Asia/Shanghai`。JDBC URL 必须显式携带 `serverTimezone=Asia/Shanghai`。

数据库支持一主多从：

- `master`：写库，负责新增、更新、删除。
- `slave`：读库分组，负责查询、统计、报表等读请求。
- `slave_1`、`slave_2`：当前 dev 默认都连接同一个 MySQL，生产环境按真实从库拆分。

分表统一按季度分表。所有需要分表的业务表都必须传入交易时间字段 `transaction_date_time`，表名格式为：

```text
{logical_table}_{yyyy}_q{quarter}
```

示例：`transaction_date_time = 2026-05-29 10:30:00` 时，`transaction` 表路由到 `transaction_2026_q2`。
