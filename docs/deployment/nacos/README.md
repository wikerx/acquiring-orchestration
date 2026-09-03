# Nacos 配置拆分规范

各服务本地只保留服务身份、端口、Nacos 连接信息和不可由远程配置关闭的安全门禁。服务客户端、内部调用路由、容量、超时、缓存、调度和观测参数统一放入对应的服务 Nacos DataId；Redis、RocketMQ、数据库、分表、Seata、XXL-JOB 等环境基础设施配置继续使用公共 DataId。

Nacos DataId 统一使用标准 YAML 后缀 `.yaml`。dev 环境使用命名空间 `dev`，命名空间 ID 为 `324ad8dc-58d0-4d0d-b264-24a9f951a2b0`；test、uat、prod 环境分别使用各自环境命名空间，不再使用 `public`。

## DataId 约定

| DataId | 用途 |
| --- | --- |
| `{service-name}-{env}.yaml` | 单服务专属配置；同时承载服务业务参数和该服务参与的内部 HMAC 调用边 |
| `common-{env}.yaml` | 所有服务共享配置 |
| `dataSource-{env}.yaml` | 数据库与连接池配置 |
| `sharding-{env}.yaml` | 分表配置 |
| `redis-{env}.yaml` | Redis Cluster 配置 |
| `rocketmq-{env}.yaml` | RocketMQ 配置 |
| `seata-{env}.yaml` | Seata 配置 |
| `xxl-job-{env}.yaml` | XXL-JOB 配置 |

`env` 可选值：`dev`、`test`、`uat`、`prod`、`sample`。

## 本地服务配置

各服务的 `application-{env}.yml` 只负责连接 Nacos，并通过 `spring.config.import` 拉取上述 DataId。

本地 `application.yml` 与 `application-{env}.yml` 的职责边界：

- `application.yml`：保留 `server.port`、`server.shutdown`、`spring.application.name`、`spring.profiles.active`、`spring.main` 等启动入口。存在内部管理接口的服务额外保留 `internal-service.auth.enabled`，避免 Nacos 缺失或误操作时关闭鉴权。
- `application-dev.yml`、`application-test.yml`、`application-uat.yml`、`application-prod.yml`：连接当前环境的 Nacos，并可承载必须在 profile 层强制生效的安全门禁；Nacos 地址、命名空间、账号和密码必须按环境隔离。
- 服务客户端、内部调用方、允许路径、超时、缓存、调度和观测参数不得继续放在本地 `application.yml`；这些配置必须进入对应的 `{service-name}-{env}.yaml`。
- 禁止在本地 yml 中写死 Redis、RocketMQ、数据库、分表、Seata、XXL-JOB 等业务环境配置；这些配置必须进入对应的公共 Nacos yaml DataId。
- `service-gateway` 是接入层例外：只拉取 `service-gateway-{env}.yaml` 和 `common-{env}.yaml`，不引入 `dataSource`、`sharding`、`redis`、`rocketmq`、`seata`、`xxl-job`，避免网关耦合业务基础设施。

Nacos yaml 的职责边界：

- `{service-name}-{env}.yaml`：每个服务唯一的服务配置，例如 `service-admin-dev.yaml`、`service-payment-dev.yaml`。正文包含服务客户端、调用权限、容量、超时、缓存、调度、观测参数，以及该服务真实参与的内部调用边密钥；不得再创建内部鉴权专用 DataId。
- `common-{env}.yaml`：所有服务共享的非敏感配置，例如时间格式、管理端点、链路头名称；禁止写入内部 HMAC、JWT、数据库密码或其他密钥。
- `service-gateway-{env}.yaml`：只放网关接入层说明、白名单、观测规则和 `gateway-checkout` 调用边，不放数据库、Redis、MQ、Seata、分表配置。
- `service-risk-{env}.yaml`：放风控健康检查、服务专属规则参数和 `payment-risk` 调用边；Redis 与 MQ 连接参数分别复用公共 DataId。
- `service-data-{env}.yaml`：只放异步数据消费和商户通知执行参数；操作日志、风控审计和商户通知消费者均由该服务独占。
- `service-clearing-{env}.yaml`：放清分消费容量、超时重试、指标周期，以及 `admin-clearing`、`job-clearing` 调用边。费用版本、分片、Redis 和 RocketMQ 连接继续复用公共 DataId。服务启动后自动消费全部合法终态事件。
- `service-settlement-{env}.yaml`：放非敏感观测参数和 `admin-settlement` 调用边；结算周期、汇率、费用、保证金、余额入账规则和业务启停不得写入配置中心。服务启动后自动认领真实结算候选。
- `dataSource-{env}.yaml`：主从数据源、连接池、MyBatis-Plus。
- `sharding-{env}.yaml`：ShardingSphere 逻辑拓扑和物理表治理规则；第一版直接启用单写，不保存服务级迁移 `mode`。
- `redis-{env}.yaml`、`rocketmq-{env}.yaml`、`seata-{env}.yaml`、`xxl-job-{env}.yaml`：对应中间件配置。

## 服务级内部 HMAC 密钥管理

内部服务不再读取 `INTERNAL_SERVICE_AUTH_SECRET`、`PAYMENT_INTERNAL_SECRET`、
`SETTLEMENT_INTERNAL_AUTH_SECRET`、`DATA_INTERNAL_SECRET` 或仓库内开发默认值。每个服务只导入自己的
`{service-name}-{env}.yaml`；调用边密钥只复制到真实调用方和验签方的服务配置中，调用方使用
`active-secret` 签名，服务端同时接受 `active-secret` 和滚动发布期间的 `previous-secret`。

服务配置统一使用标准嵌套 YAML，并把受控凭据段合并到唯一的 `acquiring:` 根节点。脚本兼容读取历史
`acquiring.internal-auth.edges:` 点号根键，但 `migrate`、`sync-service-configs`、恢复和密钥轮换产生的所有新发布内容
只能输出下列嵌套结构：

```yaml
acquiring:
  # --- managed internal-auth bundle; rotate secrets through this script ---
  # 参数说明：内部服务 HMAC 凭据根节点；只允许由受控脚本维护，禁止人工复制到 common 配置。
  internal-auth:
    # 参数说明：按真实调用关系隔离的凭据集合；调用方与全部验签方必须保持同一调用边值一致。
    edges:
      # 调用边说明：Admin 调用 Settlement 手工结算、审批、取消和冲正命令时使用的凭据。
      admin-settlement:
        # 参数说明：当前生效的 HMAC-SHA256 密钥，长度不得少于 32 字符；禁止写入日志、仓库或工单。
        active-secret: <由受控脚本生成的随机值>
        # 参数说明：滚动轮换期间临时接受的上一版密钥；非轮换窗口必须为空字符串。
        previous-secret: ""
```

禁止把真实密钥写入本仓库模板、`common-{env}.yaml`、日志、命令历史或工单正文。仓库中的
`docs/deployment/nacos/service-xxx-dev.yaml` 只作为不含真实密钥的业务配置源模板。模板中的 `active-secret`、
`previous-secret` 和 `internal-secret` 只能引用 `acquiring.internal-auth.edges.*`；发布脚本在受限临时目录中将模板与
现有服务 DataId 的受控密钥段合并，再更新原 DataId。属性引用仍使用 Spring 展平后的
`acquiring.internal-auth.edges.*` 路径，这与 YAML 是否使用嵌套写法无冲突。

所有服务和公共 Nacos YAML 必须为可调整参数说明用途、取值范围或单位，以及生产环境的安全/容量注意事项。RocketMQ
资源、分表逻辑表等重复结构可使用紧邻配置的统一字段字典，避免复制无信息量的模板注释；密钥正文不得为了“示例完整”
写入注释。

### 调用边和读取方

| edge | 调用方 | 验签方 | 允许用途 |
| --- | --- | --- | --- |
| `merchant-admin` | 已停用的兼容边界 | `service-admin` | 仅保护仍保留的旧 `/internal/merchant/**` 接口，不作为商户后台正常查询链路 |
| `admin-job` | `service-admin` | `service-job` | 后台任务管理命令；同时保留可信登录操作人的 RBAC 上下文 |
| `admin-payment` | `service-admin` | `service-payment` | 后台支付状态变更、审批和异常处置命令 |
| `admin-clearing` | `service-admin` | `service-clearing` | 人工清分执行与补偿命令；管理页面查询仍由 Admin 本地数据源完成 |
| `admin-settlement` | `service-admin` | `service-settlement` | 手工结算、预审、审批、拒绝、取消和冲正命令 |
| `job-payment` | `service-job` | `service-payment` | 支付到期任务和补偿命令 |
| `job-clearing` | `service-job` | `service-clearing` | 清分补偿命令 |
| `job-data` | `service-job` | `service-data` | 数据与商户通知补偿命令 |
| `merchant-payment` | `service-merchant` | `service-payment` | 商户退款、撤销、请款或预授权完成等交易写操作 |
| `openapi-payment` | `service-openapi` | `service-payment` | 商户支付受理和查询内部协议 |
| `openapi-payout` | `service-openapi` | `service-payout` | 商户代付受理和查询内部协议 |
| `payment-risk` | `service-payment` | `service-risk` | 支付实时风控评估 |
| `gateway-checkout` | `service-gateway` | `service-openapi`、`service-checkout` | Hosted Checkout 入口签名 |

每个服务只能读取自己的服务 DataId。UAT/生产必须为服务创建独立 Nacos 用户和角色，按完整服务 DataId、
`DEFAULT_GROUP` 和环境 namespace 授予只读权限；发布账号另行授予写权限，应用账号不得拥有写权限。发布脚本会校验
同一调用边在参与服务中的 active/previous 值完全一致。`merchant-admin` 当前只保留在 `service-admin` 配置中，用于让
遗留接口保持失败关闭；`service-merchant` 的店铺、IP 白名单、交易和结算查询继续在本服务本地完成。

### 配置存储安全边界

Nacos 2.3.2 的原生 Encryption Plugin 只处理 `cipher-{algorithm}-` 前缀 DataId，无法在保持
`service-xxx-{env}.yaml` 名称的同时透明加密整份配置。当前配置组织以“一个服务一份原有 DataId”为优先，因此必须用
Nacos namespace、最小权限 RBAC、TLS/内网隔离、配置变更审计和数据库/磁盘加密共同保护内部 HMAC 密钥。

`common-{env}.yaml` 不得保存内部 HMAC 密钥，避免任一服务获得全部调用边权限。生产发布账号与应用只读账号必须分离；
应用账号只能读取自身服务 DataId 和确有依赖的公共 DataId。若部署环境不能满足上述访问控制和存储加密要求，则生产环境
必须改用外部 Secret Manager 注入密钥，不能为了沿用普通 DataId 而裸露密钥。

`component-library/component-nacos-encryption` 仅保留用于迁移和读取历史 cipher DataId；业务服务不再依赖该组件，也不再
要求设置 `NACOS_ENCRYPTION_MASTER_KEY`。确认所有历史 cipher DataId 已清理后，可在独立基础设施变更中卸载 Nacos
Server 的兼容插件；本次配置整理不重启或改造 Nacos 容器。

任何调用边密钥一旦出现在聊天、截图、日志或工单中，都必须视为已暴露，按“无停机密钥轮换”完成 prepare、activate、
verify 和 retire-previous，不能继续沿用或只修改单边配置。

### 环境初始化和 DataId 发布

`scripts/manage-nacos-internal-auth.sh` 不打印密钥。`migrate` 负责把历史 cipher 配置迁到普通服务 DataId；
`plan-service-configs` 只读检查业务模板与当前 Nacos 的差异；`sync-service-configs` 在显式确认后备份 12 份现有配置，
用仓库模板替换业务段并原样保留受控密钥段。脚本会在发布前后验证同一调用边在各参与服务中的 active/previous 值一致。

dev 业务配置同步示例：

```bash
scripts/manage-nacos-internal-auth.sh init-container-env /absolute/runtime/path/.nacos-runtime.env

NACOS_USERNAME='<受控发布账号>' NACOS_PASSWORD='<从 Secret 注入>' \
  scripts/manage-nacos-internal-auth.sh migrate dev 324ad8dc-58d0-4d0d-b264-24a9f951a2b0

NACOS_USERNAME='<受控只读账号>' NACOS_PASSWORD='<从 Secret 注入>' \
  scripts/manage-nacos-internal-auth.sh verify dev 324ad8dc-58d0-4d0d-b264-24a9f951a2b0

NACOS_USERNAME='<受控只读账号>' NACOS_PASSWORD='<从 Secret 注入>' \
  scripts/manage-nacos-internal-auth.sh plan-service-configs dev 324ad8dc-58d0-4d0d-b264-24a9f951a2b0

CONFIRM_PUBLISH_SERVICE_CONFIGS=YES \
NACOS_USERNAME='<受控发布账号>' NACOS_PASSWORD='<从 Secret 注入>' \
  scripts/manage-nacos-internal-auth.sh sync-service-configs dev 324ad8dc-58d0-4d0d-b264-24a9f951a2b0
```

仓库只保存 `service-xxx-dev.yaml`，避免复制 dev 默认值形成可被误发布到测试、UAT 或生产的重复模板。其他环境发布时，
必须在仓库外准备受控模板目录，目录内包含完整的 `service-xxx-{env}.yaml`，保留同等配置注释并按目标环境设置容量、地址和
安全参数；执行时通过 `SERVICE_CONFIG_TEMPLATE_DIR=/absolute/controlled/templates` 显式指定。脚本在缺少任一目标环境模板时
会失败关闭，不会回退读取 dev 模板。目标 namespace、发布账号、调用边密钥和本地备份也必须按环境隔离。

同步前的完整服务配置备份默认写入 `runtime/nacos-backups/{env}-{UTC时间}-{随机后缀}/`，每次发布使用不可复用的唯一目录，
目录和文件权限分别为 `700`、`600`，并由 `.gitignore` 排除。备份包含内部密钥，只能在受控主机保存和传递。

发布失败时必须停止后续部署和服务滚动重启，并使用同批备份一次性恢复全部 12 个服务 DataId；禁止只恢复单个调用方、
验签方，或重新生成部分调用边密钥进行临时补救。恢复命令会校验备份 manifest 中的环境、namespace、group、服务数量，
逐份校验服务配置，再在发布后复核全部调用边一致性：

```bash
CONFIRM_RESTORE_SERVICE_CONFIGS=YES \
NACOS_USERNAME='<受控发布账号>' NACOS_PASSWORD='<从 Secret 注入>' \
  scripts/manage-nacos-internal-auth.sh restore-service-configs \
  dev 324ad8dc-58d0-4d0d-b264-24a9f951a2b0 \
  /absolute/path/to/runtime/nacos-backups/dev-20260902T131806Z-XXXXXX
```

恢复命令可安全重试，但必须始终使用同一批完整备份。恢复完成后再执行 `verify` 和 `plan-service-configs`，确认调用边一致且
业务模板差异符合预期，才能恢复部署。

Nacos 的 `refresh-enabled` 只表示配置变更事件可以下发，不代表所有 Bean 都支持无重启热更新。线程池、HTTP Client、
调度器、内部鉴权路径、Gateway 过滤器和启动条件发生变化时，必须按服务依赖顺序滚动重启并执行健康检查。

迁移先发布并校验 `{service-name}-{env}.yaml`，再修改应用导入并完成内部接口验收。确认普通服务 DataId 可用后，才允许
清理旧 `cipher-acqaesgcm-{service-name}-{env}.yaml` 和 `cipher-acqaesgcm-internal-auth-{edge}-{env}.yaml`：

```bash
CONFIRM_DELETE_LEGACY_CONFIGS=YES \
  scripts/manage-nacos-internal-auth.sh cleanup-legacy dev 324ad8dc-58d0-4d0d-b264-24a9f951a2b0
```

本地 Docker Compose 的 Nacos 服务必须同时满足：

- 通过 `env_file` 加载 `.nacos-runtime.env`，该文件权限必须为 `600`，且不得提交 Git；
- 挂载 `./runtime/nacos/plugins:/home/nacos/plugins`；
- 仅发布 `127.0.0.1:8848`、`127.0.0.1:9848`、`127.0.0.1:9849`，不得暴露到所有网卡；
- 只重建 Nacos 服务，不联动重建 MySQL、Redis、RocketMQ 或业务服务。

`test`、`uat`、`prod` 必须传入各自真实 namespace ID；禁止复用 dev namespace、Nacos 用户或调用边密钥。
`sample` 只用于独立示例环境，同样要求单独 namespace 和完整服务配置，不能指向任何正式环境。

### 无停机密钥轮换

1. 执行 `rotate-prepare <env> <namespace-id> <edge>`：只更新验签方服务配置，新 active 生效，旧 active 进入 previous。
2. 先滚动重启验签方；新实例接受新旧密钥，尚未更新的调用方继续使用旧密钥。
3. 执行 `rotate-activate <env> <namespace-id> <edge>` 更新调用方服务配置，再滚动重启调用方。
4. 执行 `verify <env> <namespace-id>`，核对调用边一致性、签名失败率、nonce 防重放和调用审计。
5. 所有实例确认使用新版本后执行 `retire-previous <env> <namespace-id> <edge>`，再滚动重启参与服务，清除旧密钥接受窗口。
6. 轮换记录只保存服务 DataId、调用边、环境、操作人、审批号、发布时间和验证结果，不保存密钥正文。

回滚时只允许在已批准的短期窗口恢复上一版本 DataId，并同步回滚调用方和验签方；不得关闭 HMAC、扩大
`allowed-paths`、把内部路径加入 whitelist，或恢复全局共享弱密钥。

## Hosted Checkout Gateway 入口

`/api/rest/checkout/**`、`/checkout/api/**`、`/checkout/config/**` 和 `/checkout/health`
只允许通过 `service-gateway` 访问。Gateway 会清除客户端伪造的入口头，并使用
`gateway-checkout` edge 的 `active-secret` 生成短时 HMAC-SHA256 签名；`service-openapi` 和
`service-checkout` 在控制器前完成验签。同一密钥分别进入 Gateway、OpenAPI、Checkout 三个服务的原有服务 DataId，
发布脚本负责一致性校验，仓库不提供开发默认密钥。

滚动发布顺序：

1. 先迁移并验证 Gateway、OpenAPI、Checkout 三个普通服务 DataId，确保调用边密钥一致。
2. 先部署 `service-gateway`，确认转发请求已经携带新签名头。
3. 再部署 `service-openapi` 和 `service-checkout`，启用下游直连拒绝。
4. 最后通过网络策略、安全组或容器 Service 类型禁止公网直接访问下游端口；WAF 只指向 Gateway。

密钥缺失或少于 32 个字符时，收银台入口返回 503 并失败关闭，不允许降级为直连。

## Redis

Redis 按集群模式配置，禁止在业务服务本地写死单节点 Redis 地址。

只有实际使用 Redis 的服务导入 `redis-{env}.yaml`。`service-checkout` 使用 `StringRedisTemplate`，
`service-job` 和 `service-payout` 使用共享 Redis 防并发/缓存失效能力，因此这三个服务都必须引入
`component-redis` 并导入 `redis-{env}.yaml`；`service-gateway` 不使用业务 Redis，也不导入该 DataId。

## RocketMQ

`rocketmq-{env}.yaml` 中的声明式 Topic 默认使用 RocketMQ 5.x `NORMAL` 消息类型。使用绝对投递时间的
Topic 必须显式配置 `message-type: DELAY`；初始化器会逐 Broker 读取已有 `TopicConfig`，声明类型与
已有类型不一致时拒绝复用或自动覆盖，Broker 配置读取失败时也不能当作 Topic 不存在继续执行。

`rocketmq-dev.yaml` 当前只增加 `acquiring_payment_clearing_delay_topic` 的本地 DELAY 候选声明，
尚未连接真实 Broker 创建或核验该 Topic，也未创建尚不存在的 `service-clearing` 消费组。真实变更必须
单独完成 Broker 变更审批、类型核验、绝对定时投递和重试/DLQ 验收。

## 清分服务

`service-clearing` 的五套本地 Profile 只负责连接各环境 Nacos，并统一导入清分服务 DataId 和五个公共基础设施
DataId。UAT、生产 Nacos 凭据没有仓库默认值；内部 HMAC 密钥与清分业务参数保存在同一服务 DataId。

清分专属 DataId 不提供业务启停、商户白名单、比例过滤或候选模式配置，只保留消费容量、PROCESSING 超时、
最大重试次数和指标周期。数据库、完整 28 表拓扑、RocketMQ 资源和清分服务 DataId 必须先就绪；
`service-clearing` 启动后自动注册终态/到期重试消费者并处理全部合法终态事件，指标调度器也自动运行。任一强制
依赖不满足时服务必须启动失败，不能通过缺省配置静默空跑。

指标调度器按 `physical-nodes` 逐季度查询 `transaction_finance_state` 和
`transaction_reserve_clearing_state`，全部季度成功后才更新 Gauge；Redis 不参与最终统计。停止清分只能暂停两个
消费者组或下线/缩容全部服务实例，不能依赖 Nacos 动态业务开关。当前生成的 `settlement_candidate` 固定为
`shadow_mode=1`，真实结算扫描必须排除，不允许通过配置切换为真实结算。

完整数据库、MQ、监控、数据验收和停止恢复步骤见
[`交易清分发布与运行手册`](../clearing-rollout-runbook.md)。该手册不授权发布 Nacos、执行 SQL 或创建 Broker 资源。

## 结算服务

`service-settlement` 的五套本地 Profile 只负责连接各环境 Nacos，并统一导入结算服务 DataId 和 `common`、
`dataSource`、`sharding`、`redis`、`rocketmq` 五个公共基础设施 DataId。结算服务不使用 Seata 或 XXL-JOB，
不得为了配置形式统一而导入无实际依赖的 DataId。

结算专属 DataId 不提供业务启停、影子比例、商户白名单、结算汇率、费用或保证金规则。服务启动后自动执行候选激活、
日批创建、批次处理、交易投影和 Outbox 发布；停止处理必须下线服务实例，不得依赖 Nacos 动态开关。内部管理接口的
HMAC 密钥随 Admin 与 Settlement 各自的服务 DataId 注入，脚本校验 `admin-settlement` 调用边一致；Redis 只用于 nonce 防重放，不能替代结算批次、余额流水或 Outbox 的数据库幂等约束。
当前清分生成的 `shadow_mode=1` 候选仍必须被真实结算扫描排除，不能通过配置切换为真实资金处理。

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
`transaction-sharding` 根节点，否则后出现的根节点会覆盖前一个。所有直接访问交易分表的服务都只导入
`sharding-{env}.yaml`，不得把草案文件名直接作为未导入的新 DataId 发布。

直接访问交易分表的服务没有独立迁移模式，必须直接使用 `transaction` 逻辑数据源并加载相同规则版本。

发布约束：

- `physical-nodes` 只登记已经存在且当前规则声明的全部正式表通过 schema、`DATETIME(3)`、字符集和号段校验的季度。
- 清分迁移兼容版本只接受完整旧 25 表或完整新 28 表；26/27 表半拓扑、任意缺表、未知表和重复表均拒绝启动。
- `sharding-dev.yaml` 保留当前已发布 25 表基线；`transaction-sharding-dev-draft.yaml` 是尚未真实 Dry Run 的 28 表候选，禁止直接覆盖已发布基线。
- `data.card-vault.enabled` 默认关闭；开启前必须先创建各季度 `transaction_card_vault` 物理表并发布包含该表的完整规则，否则 `service-data` 拒绝启动。
- Job 先 Dry Run，再建表并校验，最后只生成候选 `rule-version` 和 SHA-256 checksum；应用不会自动发布 Nacos。
- 所有直接访问服务必须加载相同版本后才能开放新季度。
- `/actuator/info` 的 `transactionSharding` 节点必须显示所有直接访问服务一致的 `ruleVersion` 和
  `ruleChecksumPrefix`。
- 回滚只能使用仍识别当前全部节点的上一版 ShardingSphere 制品和规则，不能恢复旧物理路由，禁止双写。

第 24 张卡资料表的推荐发布顺序为：先部署同时识别 23/24 表的兼容代码，确认所有服务继续加载
旧 23 表规则；再预建并验证卡资料物理表；随后发布 24 表规则及对应 checksum；最后才允许开启
`service-payment` 生产消息和 `service-data` 消费开关。回滚时先关闭卡资料生产/消费，再回退到完整
23 表规则，禁止在已有卡资料写入期间直接移除逻辑表。

第 25 张 `transaction_shipping_info` 的发布必须先部署识别 25 表拓扑的代码，再创建并核对模板表及
所有已发布季度物理表，随后发布 `2026.08.14-001` 规则和对应 checksum。任一季度缺表时不得把该
季度加入 `physical-nodes`，也不得先开启收货快照写入。

第 26～28 张清分表的发布必须先部署严格识别完整 25/28 表的兼容代码，再按
`docs/sql/20260825_02` 至 `20260825_05` 草案完成前检、兼容字段、模板/物理表和后检。Job 生成的
候选必须以 28 表为目标；旧 25 表规则中的季度不能直接继承为已验证节点，必须对三张新表重新完成
schema、`DATETIME(3)`、字符集、唯一索引和自增号段核验。全部直连服务滚动加载相同 28 表版本后，
才允许在后续阶段创建并启动 `service-clearing`。

完整的候选规则生成、全部直连服务滚动加载、单写验收、季度边界和回滚门禁见
[`ShardingSphere 发布、验收与回滚手册`](../shardingsphere-rollout-rollback-runbook.md)。该手册是
变更单模板，不授权 Nacos 实际发布、数据库 DDL/Drop 或生产重启。
