# ShardingSphere SQL 兼容矩阵

## 1. 当前基线

| 项目 | 当前值 |
|---|---|
| 扫描日期 | 2026-08-04 |
| 后端分支 / HEAD | `feat_scott_redisMQupdate` / `130bc25e` |
| 前端分支 / HEAD | `feat_scott_redisMQupdate` / `d20a218` |
| SDK 分支 / HEAD | `feature_scott_payment` / `4429eb5` |
| 目标架构 | ShardingSphere-JDBC、单库季度分表、原地接管现有物理表 |
| 分片键 / 时区 | `transaction_date_time` / `Asia/Shanghai` |
| 写入方式 | ShardingSphere 单写；禁止双写 |
| 直接访问服务 | Payment、Admin、Merchant、Risk、Data |
| 治理直连服务 | Job；Admin 仅执行治理查询和受控任务调用 |
| 不直接接入服务 | Gateway、OpenAPI、Checkout、Payout |

本项目是第一版直接重构，不提供 `LEGACY`、`COMPARE` 或旧物理表路由运行模式。数据库、Nacos
默认只读；本文档和仓库草案均不授权执行 DDL、Drop、Nacos 发布或生产操作。

## 2. 逻辑表覆盖

23 张表必须使用同一个规则版本、checksum、Binding 规则和已验证物理节点集合。业务尚未实现不等于
可以从拓扑移除，否则后续启用该业务时会形成不完整季度。

| 分类 | 逻辑表 | 当前生产代码状态 | 迁移结论 |
|---|---|---|---|
| 核心主链路 | `transaction_order`、`transaction_operation`、`transaction_payment_method_info` | Payment 写入和热链路查询；Admin/Merchant/Risk 查询 | 已使用逻辑表；锁和详情查询携带真实分片时间 |
| 渠道链路 | `transaction_channel_request`、`transaction_channel_interaction_log`、`transaction_channel_callback`、`transaction_channel_callback_log` | Payment 请求、响应、回调及 CAS | 已使用逻辑表；更新带分片键和状态/version 条件 |
| 状态与事件 | `transaction_flow_event`、`transaction_status_history`、`transaction_amount_change_log`、`transaction_event_outbox` | Payment 状态、金额、Outbox | 已使用逻辑表；Outbox 按已发布节点扫描 |
| 商户交互 | `transaction_merchant_notification`、`transaction_merchant_notification_log`、`transaction_merchant_api_interaction_log` | Payment/Data 通知任务、逐条 CAS、交互日志 | 已使用逻辑表；MQ/命令恢复 `transaction_date_time` |
| 正式模板，当前业务未实现 | `transaction_merchant_snapshot`、`transaction_payer_info`、`transaction_billing_info`、`transaction_additional_info`、`transaction_authentication_info`、`transaction_product_item`、`transaction_finance_state`、`transaction_currency_conversion`、`transaction_abnormal_event` | 当前没有生产 Java CRUD；仅存在于架构契约、规则、Binding、治理模板和测试 | 不是漏迁移；不得伪造 CRUD。启用对应业务前必须补 DO/Mapper/Service、分片键 SQL 和测试 |

其中 `transaction_additional_info`、`transaction_authentication_info` 已被 Checkout/3DS 架构文档引用，
`transaction_finance_state` 已被清分、结算和对账设计引用；这些能力当前尚未落地到生产 Java 代码。

## 3. SQL 路由矩阵

| SQL 类型 | 当前约束 | 阻断条件 | 验证口径 |
|---|---|---|---|
| Insert | 交易逻辑表 Insert 必须写 `transaction_date_time` | 分片键为空或时区不明确 | Mapper 契约测试和真实 MySQL POC |
| Update / CAS | 必须带 `transaction_date_time`；状态写同时带当前状态、version 或等价幂等条件 | 广播 Update、无条件状态覆盖 | Mapper 扫描、CAS 冲突/重复消费测试 |
| `FOR UPDATE` | 必须由 `transaction_date_time` 精确路由到单季度 | 多分片锁或依赖物理表名 | MySQL 8.4 事务与锁 POC |
| 详情查询 | Admin/Merchant 从列表行传原始 `transactionDateTime`，后端按交易号和时间查询 | 在线详情依赖交易号解析时间 | Controller 契约测试、浏览器 Network 验收 |
| 范围查询 | 使用半开时间范围，由 ShardingSphere 路由和归并 | 恢复固定六季度限制或手工跨表归并 | Admin/Merchant 分页、Count、排序测试 |
| Risk 聚合 | 必须带 `merchant_id`，金额按币种分组 | 物理 `UNION ALL`、跨商户聚合、混币种求和 | Risk 仓储测试 |
| 商户通知 | MQ、命令、任务和 CAS 必须携带/恢复真实分片时间 | 广播 CAS、重复抢占、时间缺失继续执行 | Data 通知重复消费、超时恢复测试 |

同步后续动作中的冲突检查同样强制传入生命周期起止时间。`beginTime`、`endTime` 缺失时直接拒绝，
不再以 `sourceTransactionId` 解析时间兜底；仅外部渠道异步回调允许在缺少显式时间时使用受控解析器恢复唯一季度。

## 4. 已删除的旧实现

生产交易链路不得重新出现以下实现：

- `ShardingDataTemplate`、单表/范围 Context、Physical Callback、`ShardingTableRangeResolver`；
- 旧 `PaymentOrderShardingAlgorithm`、`TransactionShardingMode` 和运行时模式分支；
- 交易动态物理 Mapper、`${physicalTableName}`、Admin/Merchant 手工分页归并；
- Risk 交易 `UNION ALL`、交易业务 `@DS(MASTER/SLAVE)`；
- `global-payment.sharding`、四条 `test_*` Nacos 规则和六季度查询限制。

允许保留的物理表能力仅限 Job/Admin 治理、DDL 草案、schema/号段检查和测试。Risk 名单表、Admin
风控配置表及 IP 库的白名单动态表名不属于交易季度分表，不按 `${physicalTableName}` 口径统计。

## 5. 配置一致性门禁

1. `actualDataNodes` 只能由已经建表且 23 张表全部通过 schema、字符集、`DATETIME(3)` 和号段校验的季度生成。
2. 五个直接访问服务必须加载相同 `rule-version` 和 checksum；服务没有独立迁移 mode。
3. `transaction` 数据源同时注册 Sharding、Binding、读写分离和普通单表规则，防止同一事务访问普通表时报表不存在。
4. 新季度必须先由 Job Dry Run/预建/校验，再生成候选规则；应用和脚本不得自动发布 Nacos。
5. `test_*` 基线为空时仍只生成版本化清理 SQL；未经明确确认不得执行 Drop。

## 6. 当前验收门禁

| 门禁 | 完成标准 |
|---|---|
| 代码残留 | 生产交易动态物理 SQL、旧模式、旧交易数据源注解均为 0 |
| 23 表一致性 | 代码默认表集、Nacos 候选、Binding、Job 治理表集完全一致 |
| POC | Spring Boot 3.5.14、Java 17、MyBatis-Plus 3.5.16、dynamic-datasource 4.5.0、MySQL 8.4、自增回填、事务、`FOR UPDATE`、Binding、读写分离和通知 CAS 有新鲜测试证据 |
| 后端 | component-db、Payment、Data、Risk、Admin、Merchant、Job 测试及根工程 `mvn verify` 通过 |
| 前端 | Admin/Merchant typecheck、build 通过，详情请求携带原始 `transactionDateTime` |
| SDK dev | OpenAPI 安全、创建幂等、Redis、分片落库、渠道、MQ/Outbox、Data 通知、Admin/Merchant 查询形成可追踪证据 |
| 回滚 | 仅回滚到上一个可工作的 ShardingSphere 规则/制品；不恢复旧物理路由，不双写，不删除交易事实 |

只有全部门禁具备本轮新鲜证据，才能宣布全量迁移完成。环境不可用或 POC 被跳过必须如实记录为未验收。

## 7. 2026-08-04 本机验收实绩

三个仓库均有用户既有修改和未跟踪文件，本轮未执行清理、回退或发布。后端正式验证显式固定
Java 17.0.19；系统默认 Maven 仍指向 Java 26，因此后续后端命令必须继续显式指定 Java 17。

| 验收项 | 本轮结果 | 结论 |
|---|---|---|
| 交易 Mapper SQL | 17 个 Mapper；共 91 条 Select，其中交易逻辑表 Select 60 条（Payment 54、Data 4、Risk 2）；22 条 Update、12 条 Insert；60 条交易 Select、22 条 Update 和 12 条 Insert 的分片键缺口均为 0；1 条 `FOR UPDATE` 精确携带分片键；无 `WHERE` 的交易 Update 为 0 | 通过 |
| 旧实现残留 | 业务动态物理表名、旧 Context/Callback/Algorithm/Mode、Risk 交易 `UNION ALL`、交易 Mapper 和交易查询服务的 `@DS(MASTER/SLAVE)` 均为 0；6 个物理表名命中均属于 Job/Admin 治理 | 通过 |
| 23 表配置 | Java 默认表集、逻辑路由草案、治理草案和数据库物理表集合一致；`202603`、`202604` 各 23 张物理表，字段、类型、默认值、字符集和表选项差异均为 0；checksum 回滚演练 4/4 通过 | 通过 |
| 后端 | Java 17 根工程 `mvn clean verify` 退出 0，24 个 Reactor 模块全部成功；241 份 Surefire 报告汇总 1037 项测试、0 失败、0 错误、31 跳过 | 代码与自动化契约通过 |
| ShardingSphere POC | Spring Boot、MyBatis-Plus、dynamic-datasource、MySQL 8.4、自增回填、事务、普通表同事务、`FOR UPDATE`、Binding、读写分离和 Data 通知 CAS 均由未跳过测试覆盖 | 通过 |
| 交易与通知 live | 同一验收链已通过 SDK 在 dev 完成真实支付、查询和退款；只读核对得到主单 1、操作单 1、幂等记录 2、交易 Outbox 2/2 已发送、流程事件 6、商户 API 交互日志 1、渠道请求和交互日志各 1、通知任务 1 且成功、通知投递日志 2/2 均为 HTTP 200 且成功 | 通过；全部按真实 `transaction_date_time` 路由，未输出交易报文、JWT 或凭证正文 |
| 风控数据链路 | 同一交易产生风险评估记录 1，决策为 `PASS`；限额预留 3 条，均为 `CONFIRMED` | 通过；风控决策和支付终态数据一致 |
| 通用可靠 MQ | dev `sys_mq_outbox` 后续环境已具备，验收时已有 4 条 `SENT`；此前缺表阻断不再是当前环境事实 | 通过；数据库结构变更来源属于外部环境，本轮未执行 DDL |
| 异步数据边界 | Admin/Merchant 操作日志、登录日志、风控/安全审计和非交易邮件使用可靠 Outbox；交易流程、商户/渠道交互和商户通知仍由交易分片表及交易 Outbox 保证事务事实 | 通过；未把核心交易事实改成通用异步落库 |
| 异步邮件 | 新增恢复窗口硬门禁：`processingTimeoutSeconds` 必须严格大于 SMTP 建连、读取和写入超时预算；公共、Admin、Merchant 定向测试 14 项及全仓测试通过 | 配置竞态已关闭；SMTP 仍是 at-least-once，不宣称 exactly-once |
| Redis | Stage 9 一次性 Cluster 15 项、连接失败 2 项；Risk Cluster 2 项；Stage 10 淘汰、大 Key、热 Key、10 万请求、无持久化重启和主节点故障恢复均通过 | 通过；均为隔离容器，未访问共享 Redis，临时容器已清理 |
| 前端 | workspace typecheck、Admin/Merchant/Hosted Checkout build 均退出 0；Hosted Checkout 桌面、移动端、拦截态和控制台已完成浏览器验收 | 通过；仅保留既有分包和大 chunk 警告 |
| Admin 邮件发送记录 | 页面首次加载和点击查询均返回正常结果，未再出现 `Internal Server Error` | 通过 |
| Admin 今日风险事件 | 页面具备商户号、商户订单号、平台订单号、风险等级、决策结果和时间范围查询，实际按商户号查询成功；`risk_evaluation_record` 不参与交易季度分表，今日查询命中 `idx_risk_eval_time_id(evaluation_time,id)` 且无 filesort | 通过；当前分表边界和索引适配查询模型 |
| Admin/Merchant 详情 | 列表点击固定传递 `transactionDateTime`、`rootTransactionDateTime`；后端按交易号和真实时间精确查询，不从交易号解析在线详情时间；Merchant SQL 强制认证商户隔离 | 通过 |
| SDK | Java 8 fresh 测试 75 项，0 失败、0 错误、17 项 live 默认跳过；package 和 P3C/PMD 均退出 0；dev 写入型支付/退款全流程另行完成 | 通过；package 仅有既有 Javadoc 自定义标签警告 |
| 日志与代码质量 | MPGS 正文改为长度和 SHA-256 摘要；1559 个 Java 文件的注释、敏感日志、必需事件和 Trace 缺口均为 0；生产代码和文档的 PEM 私钥正文、完整 JWT 正文扫描为 0，SDK 仅保留不进入主产物的隔离测试 fixture；三仓 `git diff --check` 退出 0 | 通过 |
| 回滚 | 候选 checksum 失败后恢复上一版 ShardingSphere 规则的纯代码/配置演练 4/4 通过 | 通过；未执行 Nacos 实际回退 |
| 外部操作 | 本轮未执行数据库 DDL/Drop、Nacos 发布、生产操作或新增权限；当前 dev `test_*` 表扫描为 0，版本化清理 SQL 继续保留用于其他环境执行前复核 | 保持审批边界 |

因此可以确认第一版代码、配置草案和 dev 功能验收已经完成；生产业务 CRUD 均已收口到逻辑表，
不存在双写或旧路由兼容分支。真实数据库清理、Nacos 实际发布/退役和生产部署仍是独立外部变更，
不能用代码验收替代审批和发布验收。
