# 交易清分发布与运行手册

## 1. 适用范围

本文用于 `service-clearing` 一期交易清分的部署、自动运行、监控、补偿、异常处置和停止恢复。当前阶段形成：

- 动作级清分状态与当前修订；
- 交易本金和费用清分明细；
- 独立保证金扣留、返还、释放明细及剩余状态；
- 保证金到期自动释放、标签币种差额双人复核和独立结算候选；
- 商户不可变费用版本的月度阶梯期间重放和失败恢复；
- 只读结算候选，候选固定写入 `shadow_mode=1`；
- 清分完成 Outbox、业务延时重试和数据库补偿事实；
- Admin 查询、人工重试、人工复核和受限重算入口。

本手册的清分步骤不直接执行结算；`service-settlement` 已在独立边界内实现结算批次、统一汇率、商户余额净额入账和保证金资金化。
对账和 SDK 改造仍不属于本次范围。

`service-clearing` 不提供业务启停、商户白名单、比例过滤或候选模式配置。数据库、28 表分片拓扑、RocketMQ、
内部 HMAC 和监控依赖必须在部署服务前完成；服务一旦启动，就会自动注册两个消费者并处理全部合法终态事件，
指标刷新、保证金到期释放和已批准阶梯期间重放调度器也会自动运行。必要依赖不满足时必须启动失败，禁止静默空跑。

需要紧急停止自动清分时，只能暂停两个清分消费者组，或下线/缩容全部 `service-clearing` 实例；不得依赖动态
业务开关。已经形成的清分、幂等、异常和 Outbox 事实必须保留。

发布操作不得增加 `CLOSED` 清分或交易状态。清分状态固定为 `NOT_CLEARED`、`PENDING`、`PROCESSING`、
`WAITING_SOURCE`、`FAILED`、`MANUAL_REVIEW`、`CLEARED`、`NOT_REQUIRED`。

## 2. 权威事实和边界

| 事实 | 权威来源 | Redis / MQ 的职责 | 禁止行为 |
|---|---|---|---|
| 交易终态和金额 | `transaction_operation` | MQ 只通知动作身份与分片时间 | 不用消息体覆盖数据库终态或金额 |
| 动作清分状态 | `transaction_finance_state` | Gauge 只展示数据库聚合值 | 不用 Redis 锁或缓存代替状态 CAS |
| 交易费用和本金 | 当前修订 `transaction_clearing_detail` | 完成消息只触发下游刷新 | 不从汇总 JSON 或 MQ 计算结算净额 |
| 保证金明细 | `transaction_reserve_clearing_detail` | 不缓存资金事实 | 不把保证金写入交易清分明细 |
| 保证金剩余 | `transaction_reserve_clearing_state` | Gauge 按标签币种展示 | 不跨币种相加，不绕过行锁和版本 CAS |
| 保证金人工调整 | `clearing_reserve_adjustment`、追加 `ADJUSTMENT` 明细 | 指标只记录有限审批结果 | 不覆盖原保证金事实，不让浏览器传操作人 |
| 阶梯期间重放 | `clearing_tier_period_replay*`、数据库累计行 | Redis 仅保存重建后的只读镜像 | 不用 Redis 累计替代数据库，不跳过稳定序号 |
| 费用版本 | 动作冻结快照及确切版本 ID | Redis 缓存不可变精确版本 | 不用当前活动费率替代历史版本 |
| 消费幂等 | `transaction_idempotency` 唯一键 | Broker 允许重复投递 | 不只依赖 Redis 去重 |
| 延时恢复 | `transaction_event_outbox` | Delay Topic 负责到期投递 | 不用普通/FIFO Topic 模拟延时消息 |
| 后续结算输入 | 当前 ACTIVE 清分修订和非影子 READY 候选 | MQ 只触发候选激活，结算服务按数据库事实认领 | 清分阶段不读取汇率或修改余额；结算资金幂等不依赖 Redis |

费用口径保持现有商户费用模板和费用配置：百分比按标签金额、标签币种计算；固定单笔费、最低费和最高费仍使用
USD 配置。标签币种非 USD 且需要 USD 固定费或上下限换算时，清分只保存原子组件和待结算求值状态，结算批次
统一使用同一批次汇率。

## 3. 部署依赖

### 3.1 数据库和分片

必须先完成以下项目并保留审批证据：

1. 使用 `docs/sql/20260825_02_transaction_clearing_precheck_draft.sql` 完成只读前检。
2. 评审并受控执行兼容字段、三张清分表、阶梯累计表、三张清分工作流固定表及 `settlement_candidate` 候选表迁移；仓库 SQL 是草案，
   不构成生产执行授权。
3. 使用 `docs/sql/20260825_05_transaction_clearing_postcheck_draft.sql` 核对表、列、索引、CHECK、字符集和季度号段。
4. `transaction-sharding.logic-tables` 必须一次性从完整 25 表切换到完整 28 表，禁止发布 26 或 27 表半拓扑。
5. 每个 `physical-nodes` 季度必须已存在三张清分物理表，并通过 `DATETIME(3)`、唯一索引和自增号段核验。
6. 所有直接访问交易数据源的服务必须加载相同 `rule-version` 和 `rule-checksum`。
7. 数据库运行账号不得拥有清分表 DDL 权限；指标和数据验收使用只读账号。

`service-clearing` 的启动门禁只接受完整 28 表拓扑。服务启动前必须完成上述迁移和滚动切换，不能依靠应用启动后
再补表或热替换 ShardingSphere 规则。

### 3.2 Nacos DataId

`service-clearing-{env}.yaml` 只保存必要的容量、主链路重试和内部认证参数；连接信息继续复用：

- `common-{env}.yaml`
- `dataSource-{env}.yaml`
- `sharding-{env}.yaml`
- `redis-{env}.yaml`
- `rocketmq-{env}.yaml`

UAT 和生产必须通过 Secret 注入 Nacos 账号和密码；所有环境都必须显式注入独立的
`INTERNAL_SERVICE_AUTH_SECRET`。内部清分接口不得加入认证白名单，不得使用仓库共享弱密钥。

允许调整的清分运行参数只有容量或故障恢复参数，例如消费线程数、PROCESSING 超时和主链路最大重试次数。
这些参数不能改变“服务启动即消费全部合法终态事件”的语义，也不能用于跳过指定商户。

保证金到期释放固定在服务启动 30 秒后开始、每轮完成后间隔 60 秒；阶梯期间重放固定在启动 5 秒后开始、
每轮完成后间隔 5 秒。两者均为有界扫描并逐项独立短事务，不提供 yml/Nacos 开关或商户过滤配置。

指标调度器随服务自动启动。每轮对每个不晚于当前季度的已发布节点执行两条分片聚合 SELECT。积压等待秒数使用
UTC，季度边界仍按 `Asia/Shanghai` 路由时区判断。保证金 Gauge 统计所有 `remaining_amount > 0` 的标签币种
负债，不以 `reserve_status='OPEN'` 排除调整态；三张保证金状态表必须通过 `idx_reserve_state_metrics` 列顺序后检。
任一季度失败时保留上一轮完整 Gauge，不发布部分结果。

### 3.3 RocketMQ 资源

必须由 Broker 管理流程创建并核验，应用自动初始化候选不能替代变更审批：

| 资源 | 类型 | 用途 | 关键约束 |
|---|---|---|---|
| `acquiring_payment_transaction_fifo_topic` | RocketMQ 5.x FIFO | 交易终态与清分完成 | `operation_id` 为 message group |
| `acquiring_payment_clearing_delay_topic` | RocketMQ 5.x DELAY | 清分业务等待重试 | 使用绝对 `deliverAt`，不与 FIFO 混用 |
| `service-clearing-transaction-terminal` | Consumer Group | 顺序消费终态事件 | `maxReconsumeTimes=16`，核验重试和 DLQ |
| `service-clearing-transaction-retry-due` | Consumer Group | 并发消费到期重试 | 独立消费进度，核验重试和 DLQ |

Broker 验收至少包括：Topic 消息类型、读写队列数、ACL、消费者组、重试次数、消费超时、DLQ、同一
`operation_id` 顺序、绝对定时投递误差和重复投递。

### 3.4 Prometheus 和 Grafana

1. Prometheus 只能从内网采集 `/actuator/prometheus`。
2. 加载 `docs/deployment/prometheus/clearing-alert-rules.yml` 并使用 `promtool check rules` 校验。
3. 导入 `docs/deployment/grafana/clearing-dashboard.json`，选择正确 Prometheus 数据源。
4. 确认固定 `application=service-clearing` 和环境标签存在。
5. 确认 `clearing_duration_seconds_bucket`、`clearing_tier_lock_seconds_bucket` 已产生直方图。
6. 金额面板必须按 `currency` 分组；禁止新增无币种的保证金金额总计。

## 4. 发布前门禁

| 门禁 | 通过标准 | 证据 | 不通过动作 |
|---|---|---|---|
| 构建与定向测试 | `service-clearing` 及依赖全部成功 | Maven 日志和 Surefire 报告 | 停止发布 |
| 完整回归 | 用户审批后全仓测试成功 | Reactor 汇总和失败数 | 停止发布 |
| SQL 前检/后检 | 所有要求为空或为 0 的结果符合预期 | 脱敏查询结果 | 不发布 28 表规则 |
| ShardingSphere | 所有直连服务版本和 checksum 一致 | `/actuator/info` 截图或采集记录 | 不启动清分服务 |
| 费用快照 | 每个终态动作都有不可变版本身份与 hash | 抽样 SQL | 不启动清分服务 |
| RocketMQ | FIFO、DELAY、Group、重试、DLQ 全部实测 | Broker 配置和联调记录 | 不启动清分服务 |
| 内部接口 | HMAC、防重放、调用方白名单通过 | 401/403/成功用例 | 不开放 Admin/Job 调用 |
| 监控 | Dashboard 可接收数据，P0/P1 规则可触发和恢复 | 告警演练记录 | 不启动清分服务 |
| 余额隔离 | 无余额流水、真实结算批次或资金化保证金写入 | SQL 核对 | P0 停止清分 |

## 5. 发布顺序

### 5.1 兼容代码和 28 表拓扑

1. `service-clearing` 尚未运行时，先部署可识别 25/28 表拓扑的其他直连服务兼容版本。
2. 核对已有支付、退款、订单查询、费用模板、商户费用配置和余额查询没有回归。
3. 在数据库变更审批内完成兼容字段和清分表迁移。
4. 运行后检并确认所有活动季度完整。
5. 生成新的 `rule-version` 和 checksum，先在隔离环境 Dry Run。
6. 滚动发布全部直连服务到相同 28 表规则；任一实例 checksum 不一致时停止发布。

### 5.2 MQ 和监控

1. 创建并验收 FIFO Topic、Delay Topic 和两个消费者组。
2. 部署 Prometheus 规则和 Dashboard。
3. 核对数据库、Redis、RocketMQ、Nacos、HMAC、分片和只读指标查询所需参数完整。
4. 在隔离环境使用同一制品完成启动门禁验证；缺少 28 表或安全密钥时应明确启动失败。

### 5.3 启动清分服务

1. 最后部署并启动 `service-clearing`；启动即注册两个消费者以及指标、保证金释放、阶梯重放调度器。
2. 确认两个消费者组均在线、分配队列正常、无异常重平衡，指标首次刷新成功，三个调度器无扫描级异常。
3. 确认启动后的全部合法商户终态事件都进入数据库幂等领取，不存在白名单或比例过滤。
4. 发起一笔审批测试支付，核对交易清分明细、保证金明细、finance state、`shadow_mode=1` 的 READY 候选和
   清分完成 Outbox。
5. 发起部分退款，核对退款手续费、原费用返还策略、保证金按原比例返还和剩余上限。
6. 重放同一终态消息，确认只增加 duplicate 指标，不新增 ACTIVE 明细或重复保证金。
7. 模拟 Slave 不可见，确认按确切版本 ID 回源 Master，不读取当前活动费率。
8. 模拟源清分等待，确认进入 `WAITING_SOURCE`、写 Delay Outbox，且到期消息过期校验有效。
9. 观察至少一个完整日清周期并完成按币种数据核对，再评审扩大实例数或消费线程数。
10. 构造一笔已到期且 `remaining_amount > 0` 的保证金，确认只追加一条 `RELEASE`、状态 CAS 为
    `FULLY_RELEASED`，重复扫描返回 `ALREADY_FINAL/NOT_DUE` 且不重复生成候选。
11. 分别提交保证金 `DEBIT/CREDIT` 调整，确认同一人不能复核、币种固定为原标签币种、批准后追加独立明细；
    拒绝不得写保证金状态、候选或余额。
12. 对无结算和无保证金事实的阶梯月份执行双人重放，确认候选 `READY -> REPLAY_HOLD -> SUPERSEDED`，
    新修订候选回到 `READY`；第 8 次失败转 `MANUAL_REVIEW`，服务重启后按数据库游标续跑。

服务扩容只改变并发能力，不改变处理商户范围。线程数必须结合数据库连接池、阶梯热点、Broker 队列数和真实压测
结果调整。

## 6. 交易验收矩阵

| 场景 | 必须核对的交易清分明细 | 必须核对的保证金明细 | 状态和幂等 |
|---|---|---|---|
| 支付成功 100 标签币种 | 本金、交易费、内外风控费、3DS 费、必要的结算换汇费组件 | 10% 配置时 HOLD 10 标签币种 | 当前修订唯一，完成 Outbox 唯一 |
| 授权成功 | 只按明确授权收费规则生成费用；不得误计应结本金 | 仅配置明确要求时处理 | 授权类不因同币种生成 FX 费 |
| 请款 90 | 请款本金和请款触发费用 | 按配置计提或沿用明确规则 | 与授权同生命周期但动作 transactionId 独立 |
| 部分退款 20 | 退款本金、退款费、配置要求的费用返还/换汇组件 | RETURN 2 标签币种，引用原 HOLD | 源清分未完成时等待，累计返还不超过 HOLD |
| 最后一笔退款 | 退款费用与本金按规则 | 返还舍入尾差，remaining 归零 | 状态 `FULLY_RETURNED`，不得负数 |
| 拒付 30 | 拒付本金和拒付费组件 | 按明确保证金政策处理 | 不与退款动作混用状态或费用规则 |
| 重复 MQ | 不新增明细 | 不重复返还 | 数据库唯一键 ACK 重复消息 |
| 未知技术异常 | 当前事务回滚 | 当前事务回滚 | 抛给 RocketMQ 原生重试 |
| 受控业务失败 | 不写半份完成数据 | 不写半份完成数据 | `FAILED/WAITING_SOURCE/MANUAL_REVIEW` 与失败码一致 |

所有金额断言使用 `BigDecimal` 和币种 ISO exponent。JPY、USD、KWD 等必须分别验证 0、2、3 位精度；不使用
`double`、`float` 或固定两位假设。

## 7. 数据验收

1. 使用受控只读 SQL，按单个自然季度和半开时间范围核对，不跨季度无界扫描。
2. 首次按审批测试商户或明确交易 ID 抽样，不直接导出全量交易。
3. 在同结构环境执行 `EXPLAIN`，确认命中分片时间、状态或业务唯一索引。
4. 在只读副本逐段执行，记录执行时间、扫描行数和复制延迟。
5. 金额一致性、重复 ACTIVE 明细、修订一致性、费用快照、币种语义、无清分汇率和已结算重算检查必须通过。
6. 状态分布、按币种统计和失败码分布是观察结果，需保存基数和人工解释。
7. 抽样结果不得导出账单明文、卡号、CVV、有效期、密钥或完整请求报文。

任何 P0 结果都必须立即暂停消费者组并阻断后续结算。不得通过直接 UPDATE、删除明细或清空 Redis 让验收
结果“变绿”。

## 8. 告警处置

| 告警 | 首次动作 | 核对重点 | 恢复条件 |
|---|---|---|---|
| `ClearingFinancialAmountImbalance` | 暂停两个消费者组，阻断后续结算 | finance state、当前 ACTIVE 交易/保证金明细、币种 | 根因明确，修复方案审批，数据验收通过 |
| `ClearingReserveReturnExceeded` | 暂停消费者并停止退款清分恢复 | 原 HOLD、累计 RETURN/RELEASE、行锁和 CAS | remaining 非负且守恒，重复消费测试通过 |
| `ClearingFinancialMismatchCaseOpened` | 查询异常案件并暂停自动消费 | 重复 ACTIVE、修订、已结算重算 | 案件关闭且复核留痕完整 |
| `ClearingManualReviewBacklog` | 按最老时间和失败码分派 | 重试耗尽、费用歧义、快照 hash | 无超 SLA 案件，处置有审计原因 |
| `ClearingFeeSnapshotHashMismatch` | 暂停消费者，禁止换用新费率 | 动作快照 JSON、版本 ID、规范化 hash | 历史版本恢复且 hash 一致 |
| `ClearingOldestPendingTooLong` | 检查消费者、DB、Delay Topic | processing lease、next retry、Broker backlog | 最老等待低于阈值且持续两个周期 |
| `ClearingTechnicalFailureDetected` | 检查应用异常类型和依赖健康 | 数据库、Redis、RocketMQ、序列化 | 原生重试恢复且 DLQ 无新增 |
| `ClearingMetricsRefreshFailed` | 不信任当前 Gauge 新鲜度 | 28 表、只读副本、分片范围 | 连续三个 SUCCESS 刷新 |
| `ClearingFeeVersionRedisHitRateLow` | 检查缓存容量和 TTL | REDIS/SLAVE/MASTER 来源比例 | 15 分钟命中率恢复到 90% 以上 |
| `ClearingCompensationBatchRepeatedFailure` | 停止 Job 调用并核查失败 | 单季度参数、候选扫描、逐条恢复异常 | 补偿成功且无重复写入 |
| `ClearingTierLockP95High` | 检查同商户同月热点 | COUNT/AMOUNT 锁顺序和慢 SQL | P95 低于 1 秒且无死锁 |
| `ClearingDuplicateMessageRatioHigh` | 检查消息生产和重试链路 | Outbox、Broker 重试、Delay、补偿重复 | 比例低于 5%，数据库无重复财务事实 |

## 9. 补偿和人工操作

### 9.1 定时补偿

1. Job 必须按单个自然季度和 `transaction_date_time + id` 游标扫描。
2. 先运行 `DRY_RUN`，记录候选数量和原因。
3. `SHADOW_WRITE` 对全部候选执行数据库幂等恢复，不按商户进行过滤。
4. 每条恢复在独立短事务执行；外层扫描不得持有长事务。
5. `SKIPPED_STALE` 和 `ALREADY_SCHEDULED` 属于幂等跳过，不得计为写入失败。
6. 批次失败向调用方传播，同时增加失败指标；不得吞异常后返回成功。

### 9.2 人工重试

- 只允许 `PENDING/FAILED/WAITING_SOURCE/MANUAL_REVIEW`。
- 必须携带精确 `transaction_id`、`transaction_date_time` 和 `expected_version`。
- 只创建受控重试事实或 Delay Outbox，不同步绕过 MQ 链路。
- 操作人来自 Admin 登录上下文，原因必填，不能接受浏览器伪造 operator。

### 9.3 人工重算

- 只允许 `CLEARED/NOT_REQUIRED + NOT_SETTLED`。
- 必须校验当前版本、当前修订和指定的不可变目标费用版本。
- 当前只允许非阶梯且不影响保证金的动作。
- 阶梯、保证金、已结算数据必须拒绝直接重算，走期间重放或审批后的差额调整方案。
- 旧修订只标记 `SUPERSEDED`，不物理删除；新修订追加写入并替换未认领的只读结算候选。

### 9.4 保证金差额调整

- 申请必须携带业务 `request_key`、原支付分片时间、预期保证金状态版本、标签币种绝对金额和原因。
- 操作人和复核人均来自 Admin 登录上下文，必须不同；浏览器请求不得携带或覆盖 operator。
- `DEBIT` 必须给出不早于当前业务日的释放日；`CREDIT` 只能减少 `OPEN` 状态且不能超过 remaining。
- 批准事务追加 `ADJUSTMENT` 明细、CAS 更新借/贷调整累计与 remaining、创建独立候选并结束审批；拒绝只结束审批。
- 已结算事实、原 `HOLD/RETURN/RELEASE` 明细和余额不得修改；任何失败回滚本次全部资金事实。

### 9.5 阶梯期间重放

- 申请必须指定商户、不可变费用版本、触发阶梯规则、合法 `yyyyMM` 和唯一 `request_key`，并经过双人复核。
- 准备阶段锁定完整阶梯规则闭包和整月动作；存在已结算、活动保证金事实或非 READY 候选时整体转人工复核。
- 可重放候选先冻结为无批次归属的 `REPLAY_HOLD`；结算扫描仍只允许认领 `READY`。
- 动作按原清分完成时间和交易号稳定排序，每项独立短事务生成新修订；不得手工修改 completed_count 或跳项。
- 普通失败按 next_retry_time 恢复，最多 8 次；确定性资金门禁或耗尽后进入 `MANUAL_REVIEW`。
- 数据库累计、控制表、重放项和修订明细是恢复依据；Redis 镜像丢失不得改变计费或重放结果。

## 10. 停止、恢复和回滚

### 10.1 紧急停止自动清分

1. 优先在 Broker 管理面暂停 `service-clearing-transaction-terminal` 和
   `service-clearing-transaction-retry-due` 两个消费者组，记录暂停时刻和消费位点；这只能停止新终态和到期重试消息。
2. 需要停止全部自动财务处理时必须下线或缩容全部 `service-clearing` 实例；仅暂停消费者组时，保证金释放、
   已批准阶梯重放和指标调度仍会继续运行，因为它们没有业务启停开关。
3. 暂停 `service-job` 的清分补偿调用，禁止人工重试或重算继续制造新的到期消息。
4. 不删除清分表、候选、明细、幂等记录、异常案件或 Outbox。
5. 保留交易终态 Outbox，修复后通过原消费位点和数据库补偿恢复。
6. 核对支付、退款、订单查询缓存和商户订单查询仍正常。

### 10.2 恢复自动清分

1. 完成根因修复、定向测试和审批后的回归，确认 28 表、Broker、HMAC 和数据库门禁仍满足。
2. 先启动单个 `service-clearing` 实例并确认健康、指标查询和消费者注册正常。
3. 恢复两个消费者组，从保留位点继续处理；不得重置位点跳过历史终态消息。
4. 对 PROCESSING 超时、WAITING_SOURCE、FAILED、RUNNING 阶梯重放和 Outbox 积压执行受控补偿。
5. 核对重复消息只命中数据库幂等，不产生第二份 ACTIVE 明细或重复保证金。
6. 连续观察告警、积压和按币种守恒后，再恢复正常实例数。

### 10.3 分片规则回滚

只有尚未产生任何三张清分季度表业务数据时，才能评审回退完整 25 表规则。已经产生清分事实时，必须保留 28 表
路由；禁止让旧制品加载无法识别的数据拓扑。

### 10.4 数据异常

1. 立即暂停消费者组并阻断后续结算。
2. 保存异常交易、修订、明细、消息、日志和告警证据。
3. 使用只读 SQL 确定影响范围，按币种分别统计。
4. 形成经财务、研发、DBA 和审计审批的数据修复脚本。
5. 修复使用追加修订或反向明细表达，不覆盖历史金额，不物理删除审计事实。
6. 修复后重新执行幂等、并发退款、数据守恒和完整日清观察。

## 11. 安全和日志

- MQ、Redis、清分表、日志和监控 Tag 不得包含完整 PAN、CVV、有效期、账单明文、JWT 或密钥。
- Metrics Tag 禁止使用 `merchantId`、`transactionId`、`messageId` 或异常正文。
- 日志可包含用于定位的交易身份，但日志平台必须实施权限、脱敏、保留周期和导出审计。
- 内部接口固定使用 HMAC-SHA256、时间戳、nonce、请求体 hash 和调用方白名单。
- Prometheus、Grafana、Nacos、RocketMQ Console 和数据库只读账号必须按环境隔离。

## 12. 发布记录

每次发布至少记录：

| 项目 | 内容 |
|---|---|
| 变更单号 | 数据库、Nacos、RocketMQ、应用、监控对应审批号 |
| 代码版本 | Git commit、制品 checksum、JDK 版本 |
| 分片规则 | `rule-version`、完整 checksum、physical nodes |
| 自动运行范围 | 全部合法终态事件；记录实例数、消费线程数和启动时间 |
| MQ 证据 | Topic 类型、Group、ACL、重试、DLQ、定时误差和起始位点 |
| 测试证据 | 定向测试、完整回归、交易验收矩阵 |
| SQL 证据 | 前检、后检、按币种数据验收脱敏结果 |
| 监控证据 | Dashboard、P0/P1 告警演练、Gauge 对账 |
| 观察窗口 | 开始/结束时间、交易量、失败和人工复核数量 |
| 停止负责人 | 应用、DBA、MQ、财务和运营联系人 |

只有数据库、MQ、监控、数据验收和完整日清观察均通过，才可以在 UAT 或生产放行真实清分与结算。结算必须由
`service-settlement` 自动链路执行，禁止绕过批次状态 CAS、唯一 `LEDGER_POSTING` 和资金流水幂等键直接写余额。
