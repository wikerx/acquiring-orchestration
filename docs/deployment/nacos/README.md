# Nacos 配置拆分规范

各服务本地只保留服务身份、环境无关的启动默认值和 Nacos 连接信息。Redis、RocketMQ、数据库、分表、Seata、XXL-JOB 等环境基础设施配置统一放在 Nacos Config。

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

- `application.yml`：保留 `server.port`、`spring.application.name`、`spring.profiles.active`、`spring.main` 等启动入口，以及不依赖部署环境的服务安全默认值。
- `application-dev.yml`、`application-test.yml`、`application-uat.yml`、`application-prod.yml`：连接当前环境的 Nacos，并可承载必须在 profile 层强制生效的安全门禁；Nacos 地址、命名空间、账号和密码必须按环境隔离。
- 禁止在本地 yml 中写死 Redis、RocketMQ、数据库、分表、Seata、XXL-JOB 等业务环境配置；这些配置必须进入对应的 Nacos yaml DataId。
- `service-gateway` 是接入层例外：只拉取 `service-gateway-{env}.yaml` 和 `common-{env}.yaml`，不引入 `dataSource`、`sharding`、`redis`、`rocketmq`、`seata`、`xxl-job`，避免网关耦合业务基础设施。

Nacos yaml 的职责边界：

- `{service-name}-{env}.yaml`：单服务个性配置，例如 `service-admin-dev.yaml`、`service-merchant-dev.yaml`、`service-openapi-dev.yaml`、`service-payment-dev.yaml`、`service-risk-dev.yaml`、`service-data-dev.yaml`、`service-gateway-dev.yaml`。
- `common-{env}.yaml`：所有服务共享配置，例如时间格式、管理端点、链路头名称。
- `service-gateway-{env}.yaml`：只放网关接入层说明、白名单路径和观测规则，不放数据库、Redis、MQ、Seata、分表配置。
- `service-risk-{env}.yaml`：只放风控服务内部鉴权、健康检查白名单和服务专属规则参数；Redis 与 MQ 连接参数分别复用公共 DataId。
- `service-data-{env}.yaml`：只放异步数据消费和商户通知执行参数；操作日志、风控审计和商户通知消费者均由该服务独占。
- `dataSource-{env}.yaml`：主从数据源、连接池、MyBatis-Plus。
- `sharding-{env}.yaml`：ShardingSphere 逻辑拓扑和物理表治理规则；第一版直接启用单写，不保存服务级迁移 `mode`。
- `redis-{env}.yaml`、`rocketmq-{env}.yaml`、`seata-{env}.yaml`、`xxl-job-{env}.yaml`：对应中间件配置。

## Hosted Checkout Gateway 入口

`/api/rest/checkout/**`、`/checkout/api/**`、`/checkout/config/**` 和 `/checkout/health`
只允许通过 `service-gateway` 访问。Gateway 会清除客户端伪造的入口头，并使用
`CHECKOUT_GATEWAY_INGRESS_SECRET` 生成短时 HMAC-SHA256 签名；`service-openapi` 和
`service-checkout` 在控制器前完成验签。三个服务必须注入同一环境专属密钥，长度不得少于
32 个字符。仓库只提供隔离的 dev 联调默认值；test、uat、prod 必须通过环境 Secret 覆盖，正式密钥
不得写入仓库、明文 Nacos 配置或日志。

滚动发布顺序：

1. 先在三个服务的 Secret 管理和 Nacos 占位符中准备同一密钥。
2. 先部署 `service-gateway`，确认转发请求已经携带新签名头。
3. 再部署 `service-openapi` 和 `service-checkout`，启用下游直连拒绝。
4. 最后通过网络策略、安全组或容器 Service 类型禁止公网直接访问下游端口；WAF 只指向 Gateway。

密钥缺失或少于 32 个字符时，收银台入口返回 503 并失败关闭，不允许降级为直连。

## Redis

Redis 按集群模式配置，禁止在业务服务本地写死单节点 Redis 地址。

只有实际使用 Redis 的服务导入 `redis-{env}.yaml`。`service-checkout` 使用 `StringRedisTemplate`，
`service-job` 和 `service-payout` 使用共享 Redis 防并发/缓存失效能力，因此这三个服务都必须引入
`component-redis` 并导入 `redis-{env}.yaml`；`service-gateway` 不使用业务 Redis，也不导入该 DataId。

## Dev 基础设施默认值

本地 dev 环境默认连接以下基础设施，生产和预发环境必须通过环境变量或独立 Nacos DataId 覆盖：

| 组件 | 默认地址 | 默认账号 | 说明 |
| --- | --- | --- | --- |
| MySQL | `${MYSQL_ENDPOINT}` | 环境注入 | `master`、`slave_1`、`slave_2` 由环境安全配置提供，仓库文档不保存真实连接信息。 |
| Redis | `${REDIS_ENDPOINTS}` | 环境注入 | 集群节点和认证信息由环境变量或受控配置中心提供。 |
| Nacos | `${NACOS_ENDPOINT}` | 环境注入 | namespace、认证和网络边界由各环境独立配置。 |

交易逻辑表统一选择 `transaction` 复合数据源，底层 primary/replica 路由由 ShardingSphere 管理；业务 Mapper 不得直接使用 `@DS(MASTER/SLAVE)`。非交易表的数据源边界继续按各服务现有配置管理。

## 数据库与分表

数据库时区统一使用 UTC+8，即 `Asia/Shanghai`。JDBC URL 必须显式携带 `serverTimezone=Asia/Shanghai`。

数据库支持一主多从：

- `master`：写库，负责新增、更新、删除。
- `slave`：读库分组，负责查询、统计、报表等读请求。
- `slave_1`、`slave_2`：当前 dev 默认都连接同一个 MySQL，生产环境按真实从库拆分。

交易分片与治理代码位置：

```text
component-library/component-db/src/main/java/com/scott/payment/component/db/sharding/QuarterTableShardingAlgorithm.java
component-library/component-db/src/main/java/com/scott/payment/component/db/sharding/TransactionShardingProperties.java
component-library/component-db/src/main/java/com/scott/payment/component/db/sharding/TransactionShardingGovernanceProperties.java
```

业务路由绑定 `transaction-sharding`，物理表治理绑定 `transaction-sharding.governance`。
第一版不加载 `global-payment.sharding`、`LEGACY` 或 `COMPARE` 路由。

测试入口位置：

```text
component-library/component-db/src/test/java/com/scott/payment/component/db/sharding/QuarterTableShardingAlgorithmTest.java
component-library/component-db/src/test/java/com/scott/payment/component/db/sharding/TransactionShardingDataSourceConfigurationTest.java
service-job/src/test/java/com/scott/payment/job/service/impl/ShardingTablePreCreateServiceImplTests.java
```

分表统一按季度分表。所有需要分表的业务表都必须传入交易时间字段 `transaction_date_time`，表名格式为：

```text
{logical_table}_{yyyy}{QQ}
```

其中 `QQ` 表示季度编号，`Q1 -> 01`、`Q2 -> 02`、`Q3 -> 03`、`Q4 -> 04`。季度按 `Asia/Shanghai` 计算；四条 `test_*` 规则不进入正式配置。

脱敏配置草案分别位于：

```text
docs/deployment/nacos/transaction-sharding-dev-draft.yaml
docs/deployment/nacos/transaction-sharding-governance-dev-draft.yaml
```

仓库中的候选和草案是同一个 `sharding-{env}.yaml` DataId 的评审材料：

- `sharding-dev.yaml` 提供 dev 已发布的交易逻辑拓扑和治理配置基线；
- `transaction-sharding-dev-draft.yaml` 提供 `transaction-sharding` 业务拓扑；
- `transaction-sharding-governance-dev-draft.yaml` 提供 `transaction-sharding.governance`。

真实候选配置必须按 YAML 对象结构合并为一个文档，不能直接拼接两个
`transaction-sharding` 根节点，否则后出现的根节点会覆盖前一个。五个服务已经只导入
`sharding-{env}.yaml`，不得把草案文件名直接作为未导入的新 DataId 发布。

五个直接访问服务没有独立迁移模式，必须直接使用 `transaction` 逻辑数据源并加载相同规则版本。

发布约束：

- `physical-nodes` 只登记已经存在且当前规则声明的全部正式表通过 schema、`DATETIME(3)`、字符集和号段校验的季度。
- 当前只接受同时包含 `transaction_card_vault` 与 `transaction_shipping_info` 的完整 25 表正式拓扑；任意缺表、未知表、重复表均拒绝启动。
- `data.card-vault.enabled` 默认关闭；开启前必须先创建各季度 `transaction_card_vault` 物理表并发布包含该表的完整规则，否则 `service-data` 拒绝启动。
- Job 先 Dry Run，再建表并校验，最后只生成候选 `rule-version` 和 SHA-256 checksum；应用不会自动发布 Nacos。
- 五个直接访问服务必须加载相同版本后才能开放新季度。
- `/actuator/info` 的 `transactionSharding` 节点必须显示五个服务一致的 `ruleVersion` 和
  `ruleChecksumPrefix`。
- 回滚只能使用仍识别当前全部节点的上一版 ShardingSphere 制品和规则，不能恢复旧物理路由，禁止双写。

第 24 张卡资料表的推荐发布顺序为：先部署同时识别 23/24 表的兼容代码，确认所有服务继续加载
旧 23 表规则；再预建并验证卡资料物理表；随后发布 24 表规则及对应 checksum；最后才允许开启
`service-payment` 生产消息和 `service-data` 消费开关。回滚时先关闭卡资料生产/消费，再回退到完整
23 表规则，禁止在已有卡资料写入期间直接移除逻辑表。

第 25 张 `transaction_shipping_info` 的发布必须先部署识别 25 表拓扑的代码，再创建并核对模板表及
所有已发布季度物理表，随后发布 `2026.08.14-001` 规则和对应 checksum。任一季度缺表时不得把该
季度加入 `physical-nodes`，也不得先开启收货快照写入。

完整的候选规则生成、五服务滚动加载、单写验收、季度边界和回滚门禁见
[`ShardingSphere 发布、验收与回滚手册`](../shardingsphere-rollout-rollback-runbook.md)。该手册是
变更单模板，不授权 Nacos 实际发布、数据库 DDL/Drop 或生产重启。
