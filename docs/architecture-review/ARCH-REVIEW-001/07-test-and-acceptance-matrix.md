# ARCH-REVIEW-001 测试与验收矩阵

## 1. 验收原则

1. P0 风险必须有自动化测试或明确的手工验收证据。
2. 本文件第 1 至 4 节及第 5.1 至 5.8 节保留迁移过程证据；当前实现只以第 5.9 节及后续验收记录为准。
3. 涉及安全、风控、分表、事件、回调、MQ 的测试必须覆盖正常、异常、重复、边界和回滚。

## 2. 测试矩阵

| 用例编号 | 模块 | 场景 | 前置条件 | 输入 | 预期数据库 | 预期状态 | 预期流程事件 | 是否自动化 |
|---|---|---|---|---|---|---|---|---|
| T-001 | `component-db` | 单表路由到 2026 Q3 | 分表规则启用 | `transactionDateTime=2026-07-28` | 访问 `*_202603` | 路由成功 | 无 | 是 |
| T-002 | `component-db` | 查询开始早于首表 | 起始季度 2026 Q3 | begin=2020-01-01,end=2026-08-01 | effectiveBegin 裁剪到 2026 Q3 | 查询成功 | 无 | 是 |
| T-003 | `component-db` | 查询结束晚于当前时间 | 当前时间固定 | end=未来日期 | effectiveEnd 裁剪到 now | 查询成功 | 无 | 是 |
| T-004 | `component-db` | begin > end | 任意 | begin 晚于 end | 无访问 | 参数错误 | 无 | 是 |
| T-005 | `service-payment` | 核心分表 Mapper 禁止默认 BaseMapper 使用 | 架构测试启用 | 扫描 `Transaction*Mapper` | 无 | 架构测试失败或通过 | 无 | 是 |
| T-006 | `service-payment` | 首次交易冻结分片时间 | ShardingSphere 规则启用 | Authorization 创建命令 | 主单、动作及关联记录写入同一季度逻辑表 | PROCESSING/SUCCESS/FAILED 按渠道结果 | `TRANSACTION_INITIALIZED` | 是 |
| T-007 | `service-payment` | 后续交易使用响应时间定位原交易 | SDK 传入源动作时间和根主单时间 | Capture/Refund/Void | 精确路由源动作和生命周期主单，不解析交易号 | 进入正常校验 | 记录原交易和新动作关联 | 是 |
| T-008 | `service-payment` | 后续交易缺少分片时间 | 不提供源动作时间或根主单时间 | Capture/Refund/Void/Query | 不执行交易查询或更新 | 参数缺失 | 安全拒绝并保留 trace | 是 |
| T-009 | `service-payment` | 商户订单冲突受控查询 | 商户号、商户订单号和时间边界齐全 | 重复 merchantOrderNo | 查询幂等事实和有界逻辑表范围 | 返回重复或冲突 | `IDEMPOTENCY_HIT` | 是 |
| T-010 | `service-payment` | 超大时间范围查询 | 查询预算已启用 | begin/end 跨大量季度 | 同步路径受超时、结果行数和成本预算保护，必要时转异步 | 不恢复固定季度/月份限制 | 无 | 是 |
| T-011 | `service-openapi` | 商户 OpenAPI IP 白名单通过 | 商户开启白名单 | `X-Gateway-Client-Ip` 命中 | 安全通过 | 请求进入 payment | `API_IP_WHITELIST_PASSED` | 是 |
| T-012 | `service-openapi` | 商户 OpenAPI IP 白名单拒绝 | 商户开启白名单 | `X-Gateway-Client-Ip` 未命中 | 安全拦截表记录 | 请求不进 payment | `API_SECURITY_REJECTED` 或安全专表事件 | 是 |
| T-013 | `service-openapi` | 付款人 IP 与商户 IP 区分 | OpenAPI IP 命中，payerIp 命中风控黑名单 | 请求头同时包含 gateway IP 和 XFF | OpenAPI 安全通过，风险记录命中 IP 黑名单 | 风控拒绝 | `RISK_BLACKLIST_HIT`、`RISK_REJECTED` | 是 |
| T-014 | `service-risk` | AML 卡 HMAC 命中 | AML 卡名单启用 | cardHmac 命中 | `risk_evaluation_record`、`hit_detail` 写入 | REJECT | `RISK_AML_HIT`、`RISK_REJECTED` | 是 |
| T-015 | `service-risk` | 白名单不能绕过 AML | 同一交易同时命中 AML 和白名单 | AML + white 输入 | 记录两条命中 | REJECT | AML 事件优先 | 是 |
| T-016 | `service-risk` | VIP 不能默认绕过黑名单 | VIP 卡同时命中黑名单 | VIP + blacklist | 记录黑名单命中 | REJECT | `RISK_BLACKLIST_HIT` | 是 |
| T-017 | `service-risk` | 仅 VIP 卡可交易 | 规则开启 | 非 VIP 卡 | 记录规则命中 | REJECT/REVIEW 按决策 | `RISK_RULE_HIT` | 是 |
| T-018 | `service-risk` | 交易频率超限 | Redis 可用 | 同维度窗口内多次请求 | 记录频率命中 | REJECT/REVIEW | `RISK_RULE_HIT` | 是 |
| T-019 | `service-risk` | Redis 不可用 | Redis 异常 | 频率规则请求 | 记录 ERROR 或降级结果 | 按用户确认策略 | `RISK_FAILED` | 是 |
| T-020 | `service-payment` | 远程风控超时 | service-risk 超时 mock | 首次交易 | 风控记录或事件写异常 | fail-closed/REVIEW/PROCESSING 按决策 | `RISK_FAILED` | 是 |
| T-021 | `service-payment` | Noop 不允许在非 local | 非 local profile | Spring 上下文 | 上下文启动失败或测试失败 | 无交易 | 无 | 是 |
| T-022 | `service-payment` | 后续交易执行 scoped risk | 后续交易风控开启 | Capture/Refund/Void | 写 risk record | 风险允许才进渠道 | `RISK_EVALUATION_STARTED/COMPLETED` | 是 |
| T-023 | `service-payment` | 未执行风控不得 PASS | 风控策略配置为 skip | 后续低风险交易 | 不写 PASS 风控 | 继续或拒绝按策略 | `RISK_SKIPPED` 带 reason | 是 |
| T-024 | `service-payment` | 首次交易事件真实时间 | mock 风控/路由/渠道耗时 | Payment | `transaction_flow_event` 多行时间递增 | 状态正确 | 各事件 `durationMillis` 不为空 | 是 |
| T-025 | `service-payment` | 风控拒绝不进渠道 | AML 命中 | Authorization | 无渠道请求记录 | REJECT/FAILED | `RISK_REJECTED`，无 `CHANNEL_REQUEST_SENT` | 是 |
| T-026 | `service-payment` | 渠道连接失败 | 渠道客户端异常 | Payment | 渠道请求日志记录失败摘要 | FAILED 或 PROCESSING 按现有策略 | `CHANNEL_CONNECTION_FAILED` | 是 |
| T-027 | `service-payment` | 渠道超时 | 渠道超时 mock | Payment | 渠道请求日志记录 TIMEOUT | PROCESSING/FAILED 按策略 | `CHANNEL_REQUEST_TIMEOUT` | 是 |
| T-028 | `service-payment` | 渠道业务拒绝 | 渠道 HTTP 成功业务 DECLINED | Payment | 渠道响应码落库 | FAILED | `CHANNEL_BUSINESS_REJECTED`、`STATUS_CHANGED` | 是 |
| T-029 | `service-payment` | 回调成功应用终态 | 原交易 PROCESSING | 合法渠道回调 | 回调表、状态历史更新 | SUCCESS/FAILED 终态 | `CHANNEL_CALLBACK_RECEIVED/VERIFIED/PARSED/APPLIED` | 是 |
| T-030 | `service-payment` | 重复回调终态保护 | 原交易已终态 | 重复合法回调 | 不覆盖终态 | 状态不变 | `CHANNEL_CALLBACK_IGNORED` | 是 |
| T-031 | `service-openapi` | 回调验签失败 | 签名错误 | 渠道回调 | 安全拦截表记录 | 交易不更新 | `CHANNEL_CALLBACK_RECEIVED`、`CHANNEL_CALLBACK_VERIFY_FAILED` 或安全专表 | 是 |
| T-032 | `service-payment` | MQ Outbox 创建 | 交易提交 | Outbox 保存 | `transaction_event_outbox` 新增 | 交易状态不受影响 | `MQ_EVENT_CREATED` | 是 |
| T-033 | `service-payment` | MQ 投递成功 | due event 存在 | Relay 发送成功 | event status SENT | 无交易状态变化 | `MQ_EVENT_SENT` | 是 |
| T-034 | `service-payment` | MQ 投递失败重试 | Producer 抛异常 | Relay 执行 | nextRetryTime 更新 | 无交易状态变化 | `MQ_EVENT_FAILED` 带 attempt | 是 |
| T-035 | `service-payment` | 商户通知成功 | 通知任务 ready | 商户回调 2xx | 通知日志 success=1 | 交易终态不变 | `MERCHANT_NOTIFICATION_SENT` | 是 |
| T-036 | `service-payment` | 商户通知失败重试 | 回调 500 或超时 | 通知执行 | 通知状态 FAILED/CLOSED | 交易终态不变 | `MERCHANT_NOTIFICATION_FAILED` 带 attempt | 是 |
| T-037 | `service-payment` | 事件幂等键防重复 | 同一事件重复写 | eventKey 相同 | 只保留一条或幂等成功 | 状态不变 | 不重复展示 | 是 |
| T-038 | `service-payment` | 事件失败不影响非关键交易 | 事件专表临时异常 | 非关键通知事件 | 业务专表更新，事件待补偿 | 主流程不失败 | 告警或补偿任务记录 | 是 |
| T-039 | `service-payment` | 状态事件 previous/current 准确 | 状态从 PROCESSING 到 SUCCESS | 回调应用 | 状态历史 from/to 准确 | SUCCESS | `previousStatus=PROCESSING,currentStatus=SUCCESS` | 是 |
| T-040 | `service-admin` | 风控管理端新增名单 HMAC 双写 | HMAC key 配置 | 新增黑名单邮箱 | `match_value_hash` 和 `match_value_hmac` 写入 | 配置启用 | 无 | 是 |
| T-041 | `service-risk` | 配置变更不影响历史审计 | 先交易命中旧规则，再改规则 | 查询历史风险记录 | 旧 snapshot hash 保留 | 历史决策不变 | 历史事件引用旧 `riskRecordNo` | 是 |
| T-042 | `service-payment` | 跨季度详情查询 | 原交易 Q3，后续交易 Q4 | 传入动作时间和根主单时间查询详情 | 两个时间分别精确路由对应季度 | 详情完整 | 时间线按 eventTime 排序 | 是 |
| T-043 | `service-admin` | 管理端跨表分页总数准确 | 多季度数据 | 后台分页查询 | count/page 与样本 SQL 一致 | 无状态变化 | 无 | 是 |
| T-044 | `service-merchant` | 商户端跨表权限隔离 | 多商户数据 | 商户查询 | 只返回本商户 | 无状态变化 | 无 | 是 |
| T-045 | `service-payment` | 金额边界风控 | 小数位和最小单位 | 多币种金额 | 风控记录金额与交易金额一致 | 决策按规则 | 风控事件金额摘要正确 | 是 |
| T-046 | `service-payment` | 退款金额状态机 + 风控 | 原交易可退余额不足/充足 | Refund | 金额变更只在成功时写 | 不超退 | `AMOUNT_TOTAL_UPDATED` 仅成功 | 是 |
| T-047 | `service-payment` | 增量授权金额状态机 + 风控 | 原授权可增量 | Incremental Auth | 金额字段正确 | PROCESSING/SUCCESS | 风控和金额事件完整 | 是 |
| T-048 | `service-payment` | 第一版时间契约强制校验 | 查询或后续动作缺少真实分片时间 | 交易详情或后续动作请求 | 不扫描历史季度，不解析交易号 | 参数错误 | 记录安全拒绝摘要 | 是 |
| T-049 | `service-job` | 新季度拓扑候选校验 | 当前和下一季度物理表已存在 | Dry Run | 23 张表 schema、字符集、时间精度和号段全部匹配 | 不改变交易状态 | 生成版本和 checksum，不自动发布 | 是 |
| T-050 | 全链路 | 支付成功全链路 | 风控 PASS、渠道 SUCCESS、通知成功 | OpenAPI Payment | order/operation/risk/event/outbox/notify 全部关联 | SUCCESS | API->RISK->ROUTE->CHANNEL->STATUS->MQ->NOTIFY 完整 | 是 |

## 3. 性能与容量验收

| 编号 | 模块 | 场景 | 验收指标 |
|---|---|---|---|
| PFT-001 | 分表查询 | 普通后台查询 1 个季度 | P95 小于用户确认阈值，SQL 使用有效时间范围 |
| PFT-002 | 分表查询 | 跨 4 个及更多季度查询 | 无固定跨度限制；同步查询受资源预算保护，深页码/高成本查询转游标或异步任务 |
| PFT-003 | 事件 | 单笔 40 条事件写入 | 不显著增加主交易事务耗时 |
| PFT-004 | 风控 | 10 条命中明细写入 | 风控 P95 满足交易前置调用预算 |
| PFT-005 | Redis 频率 | 并发计数 | 原子计数正确，无明显超卖/少计 |

## 4. 上线验收门禁

| 门禁 | 验收标准 |
|---|---|
| P0 关闭 | R-001 至 R-007 有代码修复、测试或明确配置门禁 |
| Noop 风控 | 非 local/UAT/production 不允许 `matchIfMissing=true` 跳过 |
| 分片时间契约 | 列表、SDK、内部命令、MQ 和 CAS 携带真实 `transaction_date_time`；在线链路不解析交易号 |
| 风控配置 | 管理端新增 AML/黑/白/规则至少核心类型能在运行时命中 |
| 事件真实性 | 同一交易 API/RISK/ROUTE/CHANNEL/STATUS 事件时间不再同一个批量 `now` |
| 敏感信息 | 完整 PAN/CVV/密钥不落日志、事件、风险记录 |
| 回滚 | 只恢复上一版可工作的 ShardingSphere 规则和制品；不恢复旧物理路由、不双写、不删除交易事实 |

## 5. ShardingSphere 阶段验收记录（2026-08-02）

### 5.1 代码审查结论

| 等级 | 结论 | 证据与处理 |
|---|---|---|
| P0 | 已关闭：Payment 事务入口可能先绑定默认数据源 | 11 个准备/渠道结果事务入口补 `@DS(transaction)`，与原 Callback 合计 12 个入口；反射契约测试防止回归 |
| P0 | 已关闭：分片表与普通表无法同事务访问 | 首次三组 POC 15 项中 2 项因 `payment_transaction_auxiliary` 未纳入规则而报错；补 `SingleRuleConfiguration` 后 15/15 通过 |
| P1 | 已关闭：`query-budget` 未约束实际查询和导出 | Admin/Merchant 生产查询模板应用 Statement 级超时；交易下载取消总行数上限，改为固定小页流式写出，并通过自动续租的 Redis 租约按认证后台账号或商户限制跨实例并发导出 |
| P1 | 环境门禁：Redis 导出租约与超时预算尚未压测 | Lua 使用 Redis `TIME` 消除应用节点时钟漂移并保持获取异常 Fail Closed；固定租期 5 分钟，超过租期的导出可能重新开放并发，需在真实 Cluster 和最慢导出样本中验证 |
| P1 | 已知回滚容量风险：Legacy/COMPARE 查询回落默认主库 | ShardingSphere 主路径普通读仍由复合数据源路由 replica，强一致读使用 primary scope；不为临时回滚重新引入业务类旧 `@DS(SLAVE)`，灰度前必须完成主库容量评估 |
| P2 | 供应链扫描缺口 | 依赖树已确认候选版本解析一致，但仓库没有许可证或漏洞扫描插件；5.5.3 只完成代码兼容验收，未完成生产依赖准入 |

静态审查未发现新的广播 Update、多分片 `FOR UPDATE`、事务内混用数据源、通知重复抢占或未来空节点 P0。
正式逻辑 Update 和锁查询由契约测试保证包含 `transaction_date_time` 且不允许动态物理表占位符；
Data CAS 同时包含分片时间、version、状态和软删条件，Merchant SQL 固定包含认证 `merchant_id`，
金额统计按币种与币种精度分组。查询预算复核确认超时是每条 JDBC Statement 的边界，不等同于整个
HTTP 请求总耗时；跨多条 SQL 的 Legacy/COMPARE 路径仍需在性能验收中测量端到端耗时。

### 5.2 功能与构建验收

| 验证项 | 实际结果 | 验收说明 |
|---|---|---|
| 三表 POC 与数据源测试 | 16 项通过 | 覆盖 H2/MySQL 8.4、自增回填、事务、普通表同事务、`FOR UPDATE`、Binding、读写分离、通知 CAS 和只读事务路由语义 |
| `component-db` | 83 项通过 | 算法、规则、checksum、运行模式、治理信息、查询模板和数据源生命周期 |
| `component-redis` | 111 项通过、17 项跳过 | 新增并发租约正常、竞争、异常、释放和 Redis `TIME` 参数契约；跳过项为需外部 Redis/Cluster 的环境测试 |
| `component-mq` | 3 项通过 | 通知消息兼容与分片时间恢复契约 |
| `service-payment` | 184 项通过 | 交易表族单写、事务入口、锁、CAS、Callback、Outbox、Query 和 Mapper 契约 |
| `service-data` | 34 项通过 | 通知抢占、成功/失败、超时恢复、重复消费与分片时间 CAS |
| `service-risk` | 98 项通过、3 项跳过 | 逻辑聚合、精确季度查询、COMPARE；跳过项为既有环境型测试 |
| `service-admin` | 122 项通过 | 逻辑查询、COMPARE、治理 DTO 字符串号段、查询预算和账号级导出并发租约 |
| `service-merchant` | 28 项通过 | 商户隔离、逻辑查询、详情精确路由、COMPARE、查询预算和商户级导出并发租约 |
| `service-job` | 22 项通过、1 项默认跳过 | 23 表预建计划、Dry Run、schema/号段门禁、候选规则和 checksum；真实 dev 验收测试必须显式开启 |
| `service-job` 真实 dev Dry Run | 1 项通过 | 号段修复及实时元数据读取修正后，46 张表全部匹配，零 DDL、零建表、零失败；候选节点精确为 `202603/202604`，原 Nacos 配置已恢复并通过 SHA 回读 |
| dev Nacos 正式候选发布 | 通过 | 结构化保留 Legacy 并合入 23 表拓扑和治理段；Data 专属 DataId 已补齐，五个服务 DataId 均以 `LEGACY` 为默认模式并回读一致 |
| 五服务加载 | 通过 | dev 五服务各 1 个健康实例；连续 3 次 `/actuator/health`/`info` 采样一致，均为 `LEGACY` 且复合数据源未激活 |
| 受影响模块回归 | 19 个 Reactor 模块全部成功 | Java 17 执行 `mvn -pl ... -am test`，退出码 0 |
| 全仓后端验证 | 24 个 Reactor 模块全部成功 | Java 17 执行 `mvn verify`，退出码 0；本轮 Surefire XML 汇总 897 项、失败 0、错误 0、跳过 28 |
| 前端类型检查 | 通过 | Admin、Merchant、Checkout、Shared 全部通过，退出码 0 |
| Admin 生产构建 | 通过 | 退出码 0；仅有既有 Rollup PURE 注释和大 chunk 警告 |

### 5.3 注释、配置与静态门禁

| 验收项 | 结果 | 剩余范围 |
|---|---|---|
| Java 注释 | 迁移范围通过 | 全仓脚本退出码 1：1477 个 Java 文件中，9 个既有 Redis/分布式锁文件仍有 53 项注释债务；本次迁移范围为 0 |
| Java 日志 | 通过 | 1477 个 Java 文件的敏感日志、必需事件和 trace 规则扫描缺口均为 0，退出码 0 |
| YAML 规则 | 新拓扑已发布 | `transaction-sharding` 为 23 张正式表、0 条 `test_*`，节点仅含真实 Dry Run 46/46 通过的 `202603/202604`；Legacy 四条旧 `test_*` 规则按回滚约束保留 |
| 规则 checksum | 通过 | 正式候选版本为 `2026.08.02-001`，契约测试按 Java 规范化规则重新计算并比对 checksum |
| SQL 草案 | 通过 | 清理 SQL 的 `DROP/DELETE` 和号段修复 SQL 的 `ALTER` 均保持注释；仅 dev 两条号段 `ALTER` 已按明确授权受控执行，其他环境未获授权 |
| 代码差异 | 通过 | 后端和前端 `git diff --check` 均为退出码 0，未清理用户现有改动和前端 `output/` |
| Admin 页面 | 静态通过、运行环境阻断 | 三个治理页沿用现有筛选、紧凑表格、分页、详情和权限指令，类型检查与生产构建通过；验证码接口 500，未使用真实凭证进入业务页，桌面/移动功能仍待可用环境复验 |

### 5.4 影响范围与阶段结论

当前业务接入范围为 Payment、Admin、Merchant、Risk、Data；Job 只保留物理表治理直连。
Gateway、OpenAPI、Checkout、Payout 未直接引入 ShardingSphere。Admin 18 位整数在后端按字符串序列化，
前端使用 `string`，避免 JavaScript 精度损失。

17 个动态交易 Mapper、41 条 Legacy Select 缺路由谓词、21 条 Legacy Update、Risk 物理 `UNION ALL`、
Risk 旧交易 `@DS(MASTER)`、Admin 5 处和 Merchant 3 处手工归并仍作为单写灰度回滚库存保留，
不属于最终架构完成状态。回滚窗口结束前不得删除，窗口结束后必须按残留扫描逐项归零。

本阶段代码、自动化功能、注释迁移范围及静态影响范围验收通过，`query-budget` 代码阶段 P1 已关闭。
经明确授权，dev 两张 `transaction_merchant_api_interaction_log` 季度表已完成 `AUTO_INCREMENT` 修复；
检查器改用实时 `SHOW CREATE TABLE` 元数据后，真实 Dry Run 46/46 通过并生成两季度候选节点。
生产切换验收仍未通过。完整 dev 交易拓扑已经发布，五个目标服务各 1 个健康实例并完成
`/actuator/info` 一致性检查；下一步必须先关闭只读灰度前置门禁，再由环境负责人切换只读服务。
真实 Redis Cluster 与查询预算压测、Admin 登录后页面验收、性能与季度边界演练、单写灰度及回滚演练
仍未执行。数据库 Drop、其他环境 Alter、服务模式切换和生产操作必须另行明确确认。

### 5.5 失败与复跑证据

1. 普通表同事务 POC 首次退出码 1：15 项中 2 项因缺少 `SingleRuleConfiguration` 报
   `TableNotFoundException`；补齐规则后相同 15 项全部通过。
2. 只读事务路由 POC 首次退出码 1：预期 replica、实际 primary。实现改用独立 JDBC 模板的 Statement
   超时，不在查询入口增加只读事务；新增 POC 与全仓验证通过。
3. Redis Lua 改用 `TIME` 并从 4 个参数收敛为 3 个参数后，首次定向测试因 Mockito 仍匹配旧参数而
   退出码 1；修正测试桩后 Redis/导出异常分支定向测试、受影响模块回归和全仓验证均退出码 0。
4. 真实 dev Job Dry Run 首次执行时因缺少 23 表治理配置，在物理表扫描前退出；经明确授权后，将原配置
   做 SHA 快照，只临时合入 `transaction-sharding.governance`，且不加入 `rule-version`、checksum、
   `physical-nodes` 或服务模式。发布与回读通过后重跑，测试 1 项、失败 1 项、错误 0 项、退出码 1：
   46 张目标表计划中 44 张 `SKIPPED/MATCHED`，2 张 `MISMATCHED`，零 DDL、零建表、零运行异常。
5. 两张不匹配表分别为 `transaction_merchant_api_interaction_log_202603` 和
   `transaction_merchant_api_interaction_log_202604`；两者 schema、分片时间精度和字符集均为 `MATCHED`，
   仅 `AUTO_INCREMENT=MISMATCHED`。候选节点为空，`publicationReady=false`，下一步为
   `FIX_BLOCKERS_AND_REPEAT_DRY_RUN`。每次扫描按生产 Dry Run 语义刷新治理记录并写一条审计日志。
6. 两次临时 bootstrap 运行均在 Maven 结束后恢复原 Nacos 内容；发布、bootstrap 回读、恢复发布和恢复
   SHA 回读全部成功。未发布带未来节点的完整交易拓扑，也未执行 `ALTER`、`DROP` 或建表接口。
7. 取得 dev `ALTER` 授权后，两张表的只读前置门禁全部通过，两条自增号段 DDL 均执行成功且已有行
   `MAX(id)` 未变化。首次后置查询被 MySQL 8.4 `information_schema` 缓存误导；实时元数据确认 DDL 生效。
8. `ShardingTableSchemaInspector` 改从 `SHOW CREATE TABLE` 读取实时计数器，定向测试 7/7 通过；修复后
   真实 Dry Run 1/1 通过，46 张表全部匹配，候选节点为 `202603/202604`，`publicationReady=true`。
9. dev 正式候选首次发布 API 返回成功，但首次即时回读尚未可见；自动恢复发布前快照并逐字节确认成功。
   第二次发布改为有限轮询，第 2 次回读即与候选字节和结构完全一致，未放宽 checksum 或节点门禁。
10. 共享规则发布后首次查询五服务实例时均为 0，因此未启动可能消费 MQ 的本地服务，也未伪造滚动
    结果。完成五份服务模式配置后再次查询，五个服务各出现 1 个健康实例并进入实际加载验收。
11. Data 专属 DataId 发布前不存在；内容门禁确认模式默认 `LEGACY`、敏感值只使用环境占位且不含连接
    信息，发布后第 2 次回读逐字节一致。Admin、Merchant、Risk、Payment 的现有 DataId 仅结构化加入
    各自模式段，原配置结构保持不变，四份均在第 2 次回读一致；实例启动后的实际模式均为 `LEGACY`。
12. 首次 `/actuator/info` 验收脚本将 19 位 checksum 前缀少取一位，并误检查了接口未声明的节点数字段，
    导致退出码 6；按 `TransactionShardingInfoContributor` 实际契约修正后复跑，五服务健康、版本、
    checksum、模式和复合数据源状态全部匹配，退出码 0。

### 5.6 dev Dry Run 代码验收

| 审查维度 | 结论 | 证据 |
|---|---|---|
| 启用边界 | 通过 | 测试固定 `dev` Profile，默认跳过，必须显式设置 `shardingsphere.dev-dry-run.enabled=true` |
| 副作用边界 | 通过 | 关闭 Nacos 注册、Job 调度、MQ 初始化和操作日志 MQ；只调用生产应用服务的 `dryRun=true` 入口 |
| 数据正确性 | 通过 | 号段修复后当前/下一季度共扫描 46 张表，46 张全匹配，候选节点精确为 `202603/202604` |
| 失败审计 | 通过 | 每次显式执行新增一条专用操作人 Dry Run 日志；扫描流程刷新 46 条物理表治理状态，不执行 DDL |
| 敏感信息 | 通过 | 验收摘要只输出季度、数量、物理表名、检查状态、版本、checksum 和发布状态，不输出连接信息、凭证、当前号段值或 Token |
| Nacos 可恢复性 | 通过 | 临时 bootstrap 只含治理段；最终只读回读与执行前原配置逐字节、SHA 一致，不保留未来节点或路由元数据 |
| 草案激活安全 | 通过 | 正式候选只包含 46/46 通过的两季度节点，版本化 checksum 由契约测试重新计算并比对 |
| 审查结论 | 代码、dev 数据库、Nacos 和五服务加载门禁通过 | 真实 Dry Run 1/1、完整拓扑发布和五服务一致性检查通过；灰度尚未开始 |

本轮代码审查未发现新增 P0/P1：生产逻辑未放宽门禁，检查器消除了 MySQL 统计缓存假失败，配置契约
会重新计算正式候选 checksum。当前代码、dev 数据库、Nacos 发布和五服务加载结论为 `Approved`；灰度和
回滚环境结论仍为 `Request Changes`。

### 5.7 最终复验与代码审查

- 审查对象：后端与 Admin 前端本地迁移改动；未覆盖或清理用户工作区。
- 自动化证据：Java 17 全仓 `mvn verify` 退出码 0，897 项测试、失败 0、错误 0、跳过 28；
  前端全 workspace typecheck 和 Admin 生产构建退出码均为 0。
- P0/P1：未发现新增问题。逻辑 Update、锁查询、通知 CAS、Merchant 隔离、按币种统计、规则 checksum
  和实际节点门禁均有自动化契约覆盖。
- 已知非阻断项：全仓仍有 9 个既有 Redis/分布式锁文件的 53 项注释债务；Admin 构建仍有既有大 chunk
  警告；17 个动态 Mapper 和 2 处交易主库注解作为 Legacy 回滚库存保留。
- 结论：代码、dev 数据门禁、正式 Nacos 发布和五服务加载 `Approved`；登录后页面、只读灰度、
  性能/季度边界、单写灰度和回滚环境验收仍为 `Request Changes`。

### 5.8 dev COMPARE 前置门禁

| 门禁 | 当前结果 | 结论 |
|---|---|---|
| 五服务规则一致性 | 五服务各 1 个健康实例，版本/checksum 一致，均为 `LEGACY` | 通过 |
| 小流量实例 | Admin、Merchant、Risk 均只有 1 个实例，切换会覆盖 100% 服务实例 | 阻断 |
| 部署控制面 | 当前机器无可用 Kubernetes/Helm 发布入口，Docker 中没有五服务容器 | 阻断 |
| 对比查询样本 | 缺少可认证的 Admin/Merchant 查询流量和 Risk 受控样本 | 阻断 |
| Redis 与查询预算 | 真实 Redis Cluster、端到端超时和 5 分钟导出租约尚未压测 | 阻断 |
| 观察周期 | 尚未定义并执行完整业务周期、差异阈值和停止负责人 | 阻断 |

因此本轮保持 Admin、Merchant、Risk、Data、Payment 全部为 `LEGACY`，不发布 `COMPARE` 或
`SHARDINGSPHERE` 服务模式。上述阻断项关闭前，健康检查不能替代业务灰度证据。

### 5.9 第一版最终基线（覆盖 5.1 至 5.8 的历史迁移结论）

用户已明确当前代码就是第一版，不保留旧分表版本兼容。以下结论覆盖本节此前关于 `LEGACY`、
`COMPARE`、旧 Mapper 回滚库存、旧服务 mode 和切换灰度的历史记录；历史记录只用于说明问题发现与
修复过程，不再作为当前实现或后续开发依据。

| 验收范围 | 最终基线 | 本轮证据 |
|---|---|---|
| 运行架构 | Payment、Admin、Merchant、Risk、Data 只使用 `transaction` 逻辑数据源；Job 仅保留治理直连；不提供 `LEGACY/COMPARE` | 旧 Mode/RuntimeState、动态交易物理 Mapper、手工归并、Risk `UNION ALL`、交易 `@DS(MASTER/SLAVE)` 残留扫描为 0 |
| SQL 库存 | 17 个相关 Mapper 共 89 条 Select、21 条 Update、12 条 Insert；58 条交易逻辑表 Select 为 Payment 53、Data 3、Risk 2 | `TransactionPersistenceMapperContractTests` 保证逻辑写、锁和交易 Select 携带 `transaction_date_time`，且不包含动态表名 |
| 规则与回滚 | 23 张正式表、`202603/202604` 已验证节点、版本和 checksum 为唯一规则契约；回滚只恢复上一版可工作的 ShardingSphere 规则/制品 | `TransactionShardingRuleChecksumTest.shouldRestorePreviousPublishedRuleAfterCandidateChecksumFailure` |
| Admin 详情 | 列表行必须传真实 `transactionDateTime/rootTransactionDateTime`，不从交易号解析，不用动作时间替代根主单时间 | 主单列表 4 条、动作列表 5 条；两类详情 HTTP 200；动作详情两个时间不同且页面展示 2 条动作、55 个流程节点 |
| Merchant 详情 | API 和页面同样要求两个真实时间，并强制认证商户隔离 | 当前授权商户当日查询 HTTP 200、0 条，控制台 0 错误；无样本时不跨商户构造详情验收 |
| 后端与前端 | Java 17 根工程 24 个模块全部成功；897 项已登记测试中 869 项执行通过、28 项按外部开关跳过；Admin/Merchant typecheck 和 build 通过 | 后端失败 0、错误 0；前端仅有既有构建警告 |
| Redis | 缓存与导出租约在独立 Redis 6.2.23 Cluster 验证，连接失败分支 Fail Closed | Cluster 15 项、连接失败降级 2 项均通过，临时资源已清理 |
| SDK dev 全流程 | 不绕过 OpenAPI 商户权限，不伪造密钥或商户 | Java 8 离线测试和打包通过；默认 dev 配置返回 `F409`，历史外置配置已与当前 JWT、请求加密和响应解密材料失配，支付写入型全流程仍为环境密钥阻断 |
| 外部操作 | 代码和草案不授权真实 DDL、Drop、Nacos 发布或生产操作 | `test_*` 与号段仅保留版本化 SQL 草案，后续外部执行必须再次明确确认 |

最终结论：现有生产业务 CRUD 的季度分表实现已经完成代码收口；9 张尚无生产 Java CRUD 的正式模板表
已纳入 23 表规则、Binding 和治理契约，不应伪造业务实现。当前未完成项是有效 dev OpenAPI 商户授权下
的 SDK 写入全流程、Merchant 有数据详情样本，以及任何未经授权的数据库/Nacos/生产发布操作。这些是
环境或发布门禁，不是继续保留旧分表代码的理由。

### 5.10 本轮完成验收（2026-08-02 18:16）

| 验收项 | 本轮证据 | 结论 |
|---|---|---|
| 后端完整回归 | Java 17 执行 `mvn verify`，24 个 Reactor 模块全部 `SUCCESS`；897 项已登记测试，失败 0、错误 0、跳过 28 | 通过 |
| JDK 基线 | 本轮 203 份 Surefire 报告均记录 `java.version=17.0.19`；当前 shell 默认 `mvn -v` 为 Java 26 | 本轮通过；后续命令必须显式固定 JDK 17 |
| 前端完整回归 | `npm run typecheck`、`npm run build:admin`、`npm run build:merchant` 均退出 0 | 通过；仅有既有 PURE、重复导入和大 chunk 警告 |
| SDK 回归 | Java 8 执行 `mvn test`，55 项中 41 项执行通过、14 项 live 跳过；`mvn -DskipTests package` 退出 0 | 离线功能与打包通过 |
| 注释门禁 | `python3 scripts/verify-java-comments.py` 检查 1470 个 Java 文件，剩余文件和命中均为 0 | 通过 |
| 日志门禁 | `python3 scripts/verify-logging-rules.py` 检查 1470 个 Java 文件，敏感日志、必需事件和 trace 缺口均为 0 | 通过 |
| 差异格式 | 后端、前端、SDK 三仓库 `git diff --check` 均退出 0 | 通过 |
| 旧实现残留 | 旧 Context/Callback/Algorithm/Mode、交易动态物理 SQL、Risk 交易 `UNION ALL`、交易 `@DS(MASTER/SLAVE)`、前端时间回退和详情交易号解析均为 0 | 通过 |
| 23 表规则 | Java 默认表集、路由草案和治理草案均为 23 张正式表；节点仅为 `202603/202604` | 通过 |
| 时间边界 | Admin/Merchant 详情和 OpenAPI/SDK 后续动作强制显式时间；仅无法携带时间的外部渠道异步回调允许受控解析，解析失败不执行交易 Update | 通过 |
| 外部环境 | 本轮未执行数据库 DDL/Drop、Nacos 发布或生产操作；SDK dev 写入仍受有效商户密钥包缺失阻断，Merchant 当前商户无详情样本 | 保持门禁 |

注释和日志脚本直接执行时因文件没有可执行位首次返回 126；改用 `python3` 运行同一脚本后均退出 0。
该调用方式问题不影响规则结果，后续文档和 CI 命令统一使用 `python3 scripts/...`。

### 5.11 SDK dev 写入前门禁复核（2026-08-02 18:04）

| 复核项 | 本轮证据 | 结论 |
|---|---|---|
| 默认 SDK 配置 | Java 8 执行单个 `IsoCountryQueryApiTest`，HTTP 200、业务码 `F409`，Maven 退出码 1 | dev 无匹配启用商户配置 |
| 历史外置配置 | 文件密钥配置出现响应私钥 classpath 资源缺失；可加载的 inline 配置返回 `F401007`，另有一份旧网关路径返回 `F404` | 历史临时配置不能作为当前验收凭据 |
| dev 只读指纹 | 候选商户和安全材料记录均为启用状态；具备交易条件的候选商户存在启用 MID 绑定，但本机 JWT、平台公钥和响应私钥指纹均不匹配当前启用版本 | 不是 ShardingSphere 路由或 SDK 时间字段问题 |
| 响应私钥搜索 | 仅比较本机私钥派生公钥指纹，未读取或输出私钥正文；未找到与 dev 当前响应公钥匹配项 | 无法安全完成 SDK 响应解密 |
| 写入门禁 | 未发起 Payment、Authorization、Capture、Void 或 Refund，未修改商户、密钥、MID、数据库或 Nacos | 保持关闭，避免产生无法验收的交易事实 |

继续验收需要由商户侧提供与 dev 当前配置成套的 SDK 外置配置，至少包含匹配的商户身份、JWT
签名材料、平台请求加密公钥和商户响应私钥。不得用数据库更新、禁用验签或跳过响应解密代替该前置条件。

### 5.12 dev 分片环境只读复核（2026-08-02 18:18）

| 复核项 | 本轮证据 | 结论 |
|---|---|---|
| 物理拓扑 | 23 个模板、Q3/Q4 各 23 个物理表均存在 | 通过 |
| schema 与索引 | 46 组模板/物理表列签名差异 0、索引签名差异 0，交易表 `DATETIME(3)` 缺口 0 | 通过 |
| 数据路由 | Q3/Q4 物理表中 `transaction_date_time` 跨季度记录 0 | 通过 |
| 自增号段 | 低于季度号段前缀的物理表 0 | 通过；此前基线中的 API 交互日志号段问题在当前环境已不存在，本轮未执行 Alter |
| 测试对象 | 12 张 `test_*` 模板/物理表仍存在，精确总行数 0 | 保持只读；只允许后续按版本化 SQL 和审批执行 Drop |
| 新 Nacos 规则 | 有版本和 checksum，正式逻辑表 23、`test_*` 规则 0、物理节点 2、直连服务 5 | 通过 |
| 旧 Nacos 配置 | `global-payment.sharding` 仍有 27 条表规则，其中 4 条为 `test_*`；五服务专属 DataId 仍保留未版本化旧 mode 字段 | 未退役；本轮未发布或修改 Nacos |

因此“生产业务代码是否全部分表化”的答案是已完成；“dev/生产环境是否完成全量迁移和旧配置退役”的答案仍是
未完成。剩余工作必须使用已审批的 Nacos 变更和数据库清理流程，不能通过应用代码兼容旧规则。

### 5.13 最终续接验收（2026-08-04）

本节覆盖 5.1 至 5.12 中已经被后续环境和代码修复关闭的阻断项。历史内容保留为问题发现与处理证据，
不再代表当前第一版基线。

| 验收范围 | 最新证据 | 结论 |
|---|---|---|
| 后端全仓 | Java 17.0.19 执行 `mvn clean verify`，退出码 0；24/24 Reactor 模块成功；230 份 Surefire 报告、986 项测试、0 失败、0 错误、28 跳过 | 通过 |
| SQL 与分片 | 17 个 Mapper、90 Select、21 Update、12 Insert；交易逻辑表 Select 59 条（Payment 54、Data 3、Risk 2）；Update/Insert 分片键缺口为 0 | 通过 |
| 旧代码 | 业务动态物理 SQL、旧 Context/Callback/Algorithm/Mode、Risk 交易 `UNION ALL`、交易路径 `@DS(MASTER/SLAVE)` 为 0；治理物理表解析按边界保留 | 通过 |
| 通知 CAS | Data 重复消费、双季度路由、状态/version CAS、失败重试、成功终态和 `PROCESSING` 超时恢复由全仓与专项测试覆盖 | 通过 |
| 邮件 | 恢复窗口必须严格大于 SMTP 建连、读取和写入超时预算；公共 2 项、Admin 7 项、Merchant 5 项均通过，且随根构建再次通过 | 通过；发送语义仍为 at-least-once |
| Redis Stage 9 | 一次性 Redis 6.2.23 Cluster 15 项和连接失败 2 项通过；100000 个并发全局 ID 与 10000 个连续 ID 均唯一 | 通过 |
| Redis Stage 10 | 淘汰计数、大 Key、热 Key、10 万请求、无持久化重启和 Cluster failover 通过；`shared_or_external_redis_accessed=false` | 通过 |
| Risk Redis | 独立 Cluster 集成测试 2/2 通过 | 通过 |
| 前端 | workspace typecheck、Admin/Merchant/Hosted Checkout build 均退出 0；Hosted Checkout 桌面、移动端和拦截态已完成浏览器验收 | 通过 |
| 详情时间 | Admin/Merchant 从列表行传 `transactionDateTime/rootTransactionDateTime`；缺时间不发详情请求，不从交易号解析在线查询时间 | 通过 |
| SDK | Java 8 fresh 测试 67 项：52 通过、15 个 live 默认跳过；package、PMD 均退出 0 | 离线门禁通过 |
| SDK dev 全流程 | 同一验收链已完成 dev 真实支付、查询和退款，核对交易、分片、缓存、幂等、交易 Outbox、RocketMQ、Data 通知及查询结果 | 通过 |
| 通用 Outbox | 后续 dev 环境已存在所需表结构，验收时已有 4 条 `SENT`；5.1/5.11 记录的缺表阻断已关闭 | 通过；本轮未执行 DDL |
| 代码质量 | 1533 个 Java 文件的注释门禁、敏感日志、必需事件和 Trace 门禁缺口均为 0；三仓 `git diff --check` 为 0 | 通过 |
| 敏感正文 | 后端、前端、SDK 生产范围的 PEM 私钥正文与完整 JWT 正文扫描均为 0 | 通过 |
| 回滚 | `TransactionShardingRuleChecksumTest` 4/4 通过，覆盖候选 checksum 失败后恢复上一版规则 | 通过；未发布或回退 Nacos |
| 外部边界 | 本轮未执行真实数据库 DDL/Drop、Nacos 发布、生产操作或新增权限 | 保持独立审批 |

失败与复跑证据：

1. 前序首次后端 `mvn clean verify` 退出码 1，`service-openapi` 因同时运行的验收服务占满 dev MySQL
   连接而出现 14 个上下文错误；关闭本任务服务后复跑成功。本节 fresh 全仓复跑未再出现该问题。
2. Stage 9 首次退出码 1，原因为本机既有 Redis 占用默认端口 `17001`；未结束该用户进程，改用隔离端口。
3. 隔离端口首次命令因人为收窄 `PATH` 导致找不到 Docker，退出码 127；不再覆盖 `PATH` 后相同脚本
   完整执行，15 项 Cluster 测试和 2 项连接失败测试均通过。

最终影响范围验收未发现广播 Update、多分片锁、交易事务内数据源切换、通知重复抢占、未来空节点、
Merchant 越权或跨币种求和。当前可批准代码与 dev 功能基线；真实 `test_*` Drop、其他 DDL、Nacos
实际发布/退役和生产部署不属于本次代码授权，仍需独立变更审批。
