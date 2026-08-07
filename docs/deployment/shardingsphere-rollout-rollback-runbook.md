# ShardingSphere 发布、验收与回滚手册

## 1. 适用范围

本手册用于 ShardingSphere-JDBC 第一版直接启用：单库季度分表、`transaction_date_time` 分片键、
`Asia/Shanghai` 路由时区、原地接管现有物理表。不存在 `LEGACY`、`COMPARE`、双写或旧物理表业务路由。

本手册是变更单模板，不授权真实数据库 DDL/Drop、Nacos 发布、生产重启或外部权限申请。

## 2. 阻断原则

出现任一情况必须停止发布或扩表，先修根因：

- 广播 Update、无分片键 CAS 或多分片 `FOR UPDATE`；
- 同一事务混用 `transaction` 与底层 `master/slave` 交易数据源；
- 商户通知重复抢占、重复发送或丢失 `transaction_date_time`；
- `actualDataNodes` 指向未建表或未通过全量校验的季度；
- Merchant SQL 缺少认证 `merchant_id`；
- 统计跨币种直接求和；
- Admin/Merchant 详情依赖交易号在线解析时间。

## 3. 发布前准备

| 步骤 | 操作 | 证据 | 失败处理 |
|---|---|---|---|
| 1 | 固定后端、前端、SDK 分支、HEAD 和 dirty worktree 清单 | `git status`、`git rev-parse` | 保护现有修改，不 reset/rebase |
| 2 | 运行 23 表 schema、字符集、`DATETIME(3)`、自增号段只读检查 | Job Dry Run 和检查结果 | 不生成该季度节点 |
| 3 | 运行 MySQL 8.4 POC 和受影响模块测试 | 命令、退出码、测试数 | 停止发布并修复 |
| 4 | 生成版本化 Nacos 候选和 checksum | 候选文件、checksum 测试 | 不发布不一致配置 |
| 5 | 核对五服务加载相同规则 | `/actuator/info` 脱敏摘要 | 任一服务不一致则不放流量 |

`actualDataNodes` 必须只包含已存在且 23 张表全部验证通过的季度。未来季度即使已写入治理范围，
在建表校验完成前也不能加入业务拓扑。

## 4. 第一版启用顺序

1. 在无业务流量实例加载候选规则，验证复合数据源初始化、普通单表访问和健康检查。
2. 运行三表 POC：`transaction_order`、`transaction_operation`、`transaction_merchant_notification`。
3. 验证 Payment 单写、自增主键回填、本地事务回滚、Binding、`FOR UPDATE` 和读写分离。
4. 验证 Data 通知逐条 CAS、超时恢复、重复 MQ 消费和状态/version 保护。
5. 验证 Risk、Admin、Merchant 逻辑查询；Merchant 强制 `merchant_id`，金额按币种分组。
6. 运行 SDK dev 支付全流程，串联 OpenAPI、Redis、分片表、渠道、Outbox/MQ、Data 通知和查询端。
7. 验证季度边界 `2026-09-30 23:59:59.999` 与 `2026-10-01 00:00:00.000` 的路由和事务。
8. 连续观测无路由、CAS、重复通知、慢查询和配置漂移告警后，再扩大实例流量。

## 5. 功能验收

| 链路 | 必验内容 |
|---|---|
| Admin | 主单列表到详情、动作列表到详情；Network 参数包含列表原始 `transactionDateTime` |
| Merchant | 认证商户列表到详情；跨商户交易返回不可见，SQL 始终带 `merchant_id` |
| Payment | 创建幂等、状态机终态保护、后续动作按源交易时间定位、回调 CAS、Outbox 事务一致性 |
| Data | MQ 重复消费、通知重复抢占、成功/失败重试、超时恢复、分片时间缺失拒绝执行 |
| Risk | 交易状态读取、商户+币种聚合、无物理 `UNION ALL` |
| Job | 当前/下一季度 Dry Run、预建、schema 对比、号段检查；不自动 ALTER 既有表 |
| 配置 | 五服务规则版本/checksum 一致；节点不包含未来未建表 |

## 6. 回滚策略

第一版不保留旧路由代码，因此不能切换回 `global-payment.sharding` 或物理 Mapper。

### 6.1 放量前回滚

1. 停止候选实例接流量。
2. 回退到上一个已验证的 ShardingSphere 应用制品和 Nacos 规则版本。
3. 保持数据库表和交易事实不变，不执行 Drop、数据回灌或反向双写。
4. 重新核对五服务的规则版本/checksum 后再恢复流量。

### 6.2 已产生交易后的回滚

1. 立即停止新交易或将流量切到仍使用同一 ShardingSphere 拓扑的健康实例。
2. 只回滚到能够识别当前全部已发布节点的 ShardingSphere 制品；不得回滚到旧物理路由版本。
3. 保留 Outbox、通知任务和 CAS 状态，恢复后按原 `transaction_date_time` 续跑。
4. 对重复请求、渠道回调、MQ 和商户通知执行幂等核对，禁止手工覆盖终态。
5. 数据修复、DDL 或 Nacos 实际回退必须另行审批并保留审计证据。

## 7. 新季度接入

1. Job 对 23 张模板表和目标季度执行 Dry Run。
2. 经明确授权后预建物理表；第一版只允许 `CREATE TABLE ... LIKE` 和受控号段设置，不自动 ALTER 既有表。
3. 校验全部 23 张表；任一失败则整个季度不得加入拓扑。
4. 生成新 `rule-version` 和 checksum，人工评审差异。
5. 经 Nacos 发布审批后滚动加载五服务，并确认版本一致。
6. 执行季度边界路由、写入、读取、锁、CAS 和回滚演练。

## 8. 变更记录模板

| 项目 | 必填证据 |
|---|---|
| 代码基线 | 仓库、分支、HEAD、工作区状态 |
| 数据库检查 | 节点、23 表检查摘要、失败项；不得记录 JDBC URL 或密码 |
| Nacos 候选 | 规则版本、完整 checksum、差异和审批单 |
| 自动化测试 | 命令、JDK/Maven/Node 版本、退出码、测试数 |
| 业务验收 | Admin/Merchant 浏览器证据、SDK dev traceId/业务号脱敏摘要 |
| 回滚演练 | 制品/规则版本回退、恢复时间、数据一致性核对 |

任何证据缺失均标记为“未验收”，不得写成“通过”。

## 9. 本机只读回滚演练边界

本轮通过 `TransactionShardingRuleChecksumTest.shouldRestorePreviousPublishedRuleAfterCandidateChecksumFailure`
演练候选规则 checksum 失败后重新校验上一版规则。该演练只覆盖应用启动门禁和版本化配置选择，未连接
Nacos、未切换运行实例、未执行数据库 DDL/Drop，也未恢复旧物理 Mapper。真实环境回滚仍必须按第 6 节
另行审批，并使用能够识别全部已发布季度节点的 ShardingSphere 制品与规则。
