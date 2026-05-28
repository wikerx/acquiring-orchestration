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

