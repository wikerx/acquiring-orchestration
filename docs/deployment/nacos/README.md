# Nacos 配置拆分规范

各服务本地只保留服务名、端口、激活环境和 Nacos 连接信息。Redis、RocketMQ、数据库、分表、Seata、XXL-JOB 等基础设施配置统一放在 Nacos Config。

## DataId 约定

| DataId | 用途 |
| --- | --- |
| `{service-name}-{env}.yml` | 单服务专属配置 |
| `common-{env}.yml` | 所有服务共享配置 |
| `dataSource-{env}.yml` | 数据库与连接池配置 |
| `sharding-{env}.yml` | 分表配置 |
| `redis-{env}.yml` | Redis Cluster 配置 |
| `rocketmq-{env}.yml` | RocketMQ 配置 |
| `seata-{env}.yml` | Seata 配置 |
| `xxl-job-{env}.yml` | XXL-JOB 配置 |

`env` 可选值：`dev`、`test`、`uat`、`prod`。

## 本地服务配置

各服务的 `application-{env}.yml` 只负责连接 Nacos，并通过 `spring.config.import` 拉取上述 DataId。

## Redis

Redis 按集群模式配置，禁止在业务服务本地写死单节点 Redis 地址。

## Dev 基础设施默认值

本地 dev 环境默认连接以下基础设施，生产和预发环境必须通过环境变量或独立 Nacos DataId 覆盖：

| 组件 | 默认地址 | 默认账号 | 说明 |
| --- | --- | --- | --- |
| MySQL | `127.0.0.1:3306/payment_acquiring` | `root` | `master`、`slave` 先指向同一个库，后续从库就绪后只替换 `MYSQL_SLAVE_URL`。 |
| Redis | `127.0.0.1:6379` | 无用户名 | 默认密码从 `REDIS_PASSWORD` 读取，未设置时使用 dev 默认值。 |
| Nacos | `127.0.0.1:8848` | `nacos` | dev 默认 namespace 使用 `public`，避免本地未创建命名空间时注册失败。 |

读请求如需走从库，可在 service 或 mapper 方法上使用 `@DS(DataSourceName.SLAVE)`；未声明时默认走 `master`。
