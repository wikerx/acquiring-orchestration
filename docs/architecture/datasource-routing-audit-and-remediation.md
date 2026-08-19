# 全系统数据源路由审计与整改记录

## 1. 最终结论

审计日期：2026-08-19
审计分支：`feat_scott_fee`

本次覆盖 `service-gateway`、`service-admin`、`service-merchant`、`service-checkout`、
`service-openapi`、`service-payment`、`service-risk`、`service-data`、`service-payout`、
`service-job`、`component-library` 和 `channel-library` 的生产 Java 代码。

整改后的结论：

1. 生产代码中的 33 个类级 `@DS` 已全部移除，当前类级路由数量为 0。
2. 当前 446 个生产 `@DS` 均位于方法声明；类中的注解目标均为 `public` 实例方法，
   Mapper 接口方法按 Java 规则隐式公开。
3. checkout 主库事务内切换 `TRANSACTION` 的跨数据源事务风险已拆分：checkout 状态先在
   `MASTER` 短事务提交，交易分片收敛在事务提交后通过独立服务入口执行。
4. 已将管理后台、商户后台、任务管理、基础资料、监控、日志、邮件、渠道和风控管理等普通查询迁移到
   `SLAVE`；本轮复审补充修复了管理端 IP 白名单分页、导出和详情查询遗漏。
5. 密钥、安全策略、登录会话、支付路由、结算汇率、实时风控、任务抢占、锁查询、状态推进和
   Outbox 收敛继续使用 `MASTER`。
6. 交易季度分表读写继续使用 `TRANSACTION`，未机械替换为普通主库或从库。
7. 已增加全仓架构门禁和 Admin、Job、Merchant、Payment 路由契约，防止类级注解、不可代理方法
   注解和关键主从边界回归。

## 2. 路由模型

`docs/deployment/nacos/dataSource-dev.yaml` 的路由约束如下：

- `master`：默认数据源，承担写操作及要求读后立即一致的查询。
- `slave`：`slave_1`、`slave_2` 组成的只读组，承担允许复制延迟的页面、导出、字典和日志查询。
- `transaction`：ShardingSphere 交易逻辑复合数据源，承担季度分表、交易单表和事务路由。
- `strict: true`：阻止不存在的数据源名称，但不能替代业务层对主从一致性的判断。

项目使用 `dynamic-datasource 4.5.0`。`@DS` 与 `@Transactional` 同时存在时必须放在同一个可代理的
`public` 入口方法上，使数据源切面先于事务切面完成选库。同一事务已经绑定 JDBC 连接后，后续方法
即使声明其他 `@DS` 也不能切换物理连接。

## 3. 统一路由规则

| 场景 | 数据源 | 说明 |
| --- | --- | --- |
| 管理页面分页、列表、详情、导出 | `SLAVE` | 允许短暂复制延迟，不参与同事务写入 |
| 商户页面字典、操作日志、财务只读查询 | `SLAVE` | 减少主库管理查询压力 |
| 基础国家、币种、BIN、MCC、通知、邮件记录 | `SLAVE` | 缓存未命中的普通参考数据查询 |
| 新增、修改、删除、审批、状态推进 | `MASTER` | 写操作及写前校验保持同库一致性 |
| 登录、MFA、密钥、OpenAPI 访问策略 | `MASTER` | 复制延迟会扩大安全窗口 |
| 支付路由、结算汇率、实时风控 | `MASTER` | 交易决策必须读取最新配置 |
| 任务抢占、超时扫描、锁和心跳 | `MASTER` | 避免重复执行和状态覆盖 |
| Outbox 插入、抢占、发送状态收敛 | `MASTER` | 要求幂等和最新版本判断 |
| 交易逻辑表、季度分表读写 | `TRANSACTION` | 由 ShardingSphere 完成实际节点路由 |

`service-gateway`、`service-checkout`、`service-payout` 和 `channel-library` 当前没有直接数据库访问，
不存在 `@DS` 属于正常结果。

## 4. 已关闭问题

### 4.1 checkout 跨数据源事务

原问题：`DefaultPaymentCheckoutService` 在已绑定 `MASTER` 连接的本地事务中调用交易分片服务，
方法级 `TRANSACTION` 无法替换当前事务连接。

整改：checkout 超时 CAS、会话失败和事件记录保留在 `MASTER` 短事务；事务提交后再调用
`DefaultPaymentAuthenticationRecordService` 和交易失败收敛服务。交易侧失败保留可重试记录，
后续状态轮询通过幂等逻辑继续收敛。

### 4.2 类级路由覆盖整个服务

原问题分布：

| 模块 | 原类级数量 |
| --- | ---: |
| `component-library` | 3 |
| `service-data` | 5 |
| `service-payment` | 23 |
| `service-risk` | 2 |
| 合计 | 33 |

整改：所有真实数据库入口改为方法级路由；`DefaultApprovedRefundChannelExecutor` 等纯编排器不再声明
数据源；ISO 和 Reference Data 缓存回源通过公开方法进入 `SLAVE`；交易持久化公开入口使用
`TRANSACTION`；实时风控和限额预占继续使用 `MASTER`。

### 4.3 普通查询隐式使用主库

已迁移的主要范围：

- Admin：国家、币种、BIN、IP、MCC、区域币种、字典、部门、岗位、菜单、通知、日志、邮件、渠道、
  风控管理、分片治理、在线用户、商户资料、费用、账户和节假日日历。
- Merchant：字典、操作日志、费用、账户和余额流水。
- Job：任务分页、节点列表、运行日志分页和导出列表。
- Payment：BIN 缓存回源。

本轮复审新增修复：

- `AdminMerchantIpWhitelistServiceImpl.pageWhitelists` 使用 `SLAVE`。
- `AdminMerchantIpWhitelistServiceImpl.listWhitelists` 使用 `SLAVE`。
- `AdminMerchantIpWhitelistServiceImpl.getWhitelist` 使用 `SLAVE`。
- 商户内部 IP 白名单和来源网址查询显式使用 `MASTER`，保留安全配置强一致性。
- Job 的任务详情、待调度扫描和超时扫描显式使用 `MASTER`，避免依赖默认数据源配置。

### 4.4 缺少全仓门禁

`DataSourceAnnotationArchitectureTest` 当前扫描全部 `src/main/java`：

1. 禁止类、接口、record、enum 声明类级 `@DS`。
2. 验证每个 `@DS` 目标是方法声明。
3. 类中的注解目标必须为 `public` 非静态方法。
4. 允许 Mapper 接口方法按语言规则省略 `public`。

Admin、Job、Merchant、Payment 另有反射契约测试固定关键 `MASTER`、`SLAVE` 路由。

## 5. 最终注解统计

生产代码共有 446 个方法级 `@DS`：

| 模块 | `MASTER` | `SLAVE` | `TRANSACTION` | 合计 |
| --- | ---: | ---: | ---: | ---: |
| `component-library` | 28 | 13 | 2 | 43 |
| `service-admin` | 63 | 138 | 0 | 201 |
| `service-data` | 5 | 0 | 7 | 12 |
| `service-job` | 3 | 4 | 0 | 7 |
| `service-merchant` | 31 | 24 | 0 | 55 |
| `service-openapi` | 15 | 2 | 0 | 17 |
| `service-payment` | 4 | 1 | 78 | 83 |
| `service-risk` | 26 | 0 | 2 | 28 |
| 合计 | 175 | 182 | 89 | 446 |

类级 `@DS`：0。
非方法目标或不可代理类方法：0。
显式 `SLAVE` 与写事务组合：0。

## 6. 验证记录

### 6.1 路由架构门禁和关键契约

执行命令：

```text
mvn -pl component-library/component-db,service-admin,service-job,service-merchant,service-payment -am \
  -DskipITs -Dsurefire.failIfNoSpecifiedTests=false \
  -Dtest=DataSourceAnnotationArchitectureTest,AdminMerchantAccessDataSourceContractTests,\
JobDataSourceRoutingContractTests,MerchantOperationalReadDataSourceContractTests,\
PaymentReferenceDataSourceContractTests test
```

结果：19 个 Reactor 模块构建成功；8 个目标测试通过，失败 0，错误 0，跳过 0。

### 6.2 数据源专项回归

数据源路由相关测试扩大到现有架构、配置、分片及各服务契约测试后，22 个 Reactor 模块构建成功；
99 个测试通过，失败 0，错误 0，跳过 0。

### 6.3 全后端编译

执行全 Reactor 编译，28 个模块全部构建成功，Maven 结果为 `BUILD SUCCESS`。

### 6.4 完整单元测试

执行命令：

```text
mvn -DskipITs test
```

结果：28 个 Reactor 模块全部构建成功，Maven 结果为 `BUILD SUCCESS`；OpenAPI 模块 6 个需要
外部 MPGS 环境的 live flow 测试和 Job 模块 1 个既有 acceptance test 按原有条件跳过，其余测试
无失败、无错误。测试过程使用本地 dev 环境的 Nacos、MySQL 和 RocketMQ 配置，本次整改未新增或
修改 Nacos 配置。

## 7. 剩余验证边界

现有架构和反射契约能够固定注解位置与声明值，但不能替代真实双物理数据源运行时验证。部署前仍应在
具备主从 MySQL 与 ShardingSphere 的集成环境执行连接级验证，至少断言普通查询进入 replica、写操作
进入 master、交易逻辑 SQL 进入对应季度物理表，并验证主从复制延迟下的安全和任务抢占场景。

## 2026-08-19 08:26

## 代码审查报告

### 审查目标

- 类型：本地改动复审
- 范围：全系统生产 Java 数据源路由及本轮路由整改
- 审查时间：2026-08-19 08:26

### 审查输入证据

- 全仓生产 `@DS` 静态扫描和模块分布统计。
- 原 33 个类级路由类的迁移差异。
- Admin、Job、Merchant、Payment、Risk、Data 和公共数据库组件关键调用链。
- 架构门禁、方法反射契约、99 个数据源专项测试、28 模块编译和完整 Maven 单元测试结果。

### 问题清单

#### 已修复建议问题

**管理端 IP 白名单普通查询仍使用默认主库**

- 位置：`service-admin/.../AdminMerchantIpWhitelistServiceImpl.java`
- 风险：分页、导出和详情查询持续占用主库，和管理查询迁移规则不一致。
- 处理：三个后台查询使用 `SLAVE`；商户内部安全查询显式保留 `MASTER`。

### 结论

- 结论：`Approved`
- 未解决严重问题：0
- 未解决建议问题：0
- 可选优化：在真实双数据源集成环境补充连接级路由验证。

---
