# ARCH-REVIEW-001 分阶段整改计划

> 已废止（2026-08-02）：本文是迁移前备选计划，其中 locator、双写、Legacy 回退和旧 Mapper 保留步骤不再执行。
> 当前阶段与完成标准以 `07-test-and-acceptance-matrix.md` 第 5.9 节及 SQL 兼容矩阵为准。

## 1. 阶段总览

| 阶段 | 目标 | 审核门禁 |
|---|---|---|
| Phase 0 | 冻结规则、补测试基线、确认生产配置快照 | 只读验证和测试计划确认 |
| Phase 1 | 分表访问边界收口 | Repository/API 设计确认 |
| Phase 2 | 永久分片定位 | DDL、回填和双写方案确认 |
| Phase 3 | 风控公共模型和敏感值能力 | 字段采集、HMAC、脱敏和密钥方案确认 |
| Phase 4 | AML、黑白名单运行时接入 | 规则优先级和审计记录确认 |
| Phase 5 | 规则风控和 Redis 频率控制 | 异常策略、限额/频率窗口确认 |
| Phase 6 | 交易流程事件重构 | 事件枚举、字段、事务边界确认 |
| Phase 7 | 回调、MQ、通知事件补齐 | 异步事件幂等和数据量预算确认 |
| Phase 8 | 历史兼容与数据迁移 | 回填脚本、灰度、回滚确认 |
| Phase 9 | 全链路测试与验收 | 回归结果、性能结果、上线门禁确认 |

## 2. 详细计划

| 阶段 | 目标 | 涉及模块 | 预计修改文件 | 数据库变更 | 配置变更 | 兼容策略 | 风险 | 回滚方式 | 单元测试 | 集成测试 | 验收门禁 | 前置依赖 | 完成后暂停点 |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| Phase 0 | 固化当前行为和风险基线 | 全模块 | 新增扫描测试、风险测试，不改业务逻辑 | 无 | 读取生产/UAT Nacos 快照，不直接修改 | 只增加测试和文档 | 测试暴露现有失败需分类 | 删除新增测试即可 | Mapper 访问扫描、Noop 装配扫描、事件伪造扫描 | 不启动服务，仅本地单测 | 用户确认风险清单和优先级 | 本审计文档审核 | 暂停确认是否进入代码修复 |
| Phase 1 | 收口分表访问边界 | `service-payment`、`service-admin`、`service-merchant`、`component-db` | `TransactionRepository`、`ShardingTableAccess`、各查询 Service、Mapper 包可见性调整 | 无 | 可增加最大查询跨度配置草案 | 保留旧 Mapper 方法，Repository 先适配主路径 | 改动面较广，容易漏查询 | 开关回退旧查询实现 | Repository 单表/范围路由测试、BaseMapper 禁用扫描 | 跨季度查询和边界裁剪测试 | 所有核心分表访问只经过 Repository | Phase 0 | 暂停审核分表边界 |
| Phase 2 | 新增永久分片定位 | `service-payment`、`component-db`、`docs/sql` | `TransactionShardLocatorDO/Mapper/Repository`、创建/回调/查询定位改造 | 新增 `transaction_shard_locator`，索引和唯一约束 | 新增 locator 双写开关 | 先双写，读取优先 locator，失败回退解析 | 回填不完整会影响历史定位 | 关闭读取开关，保留双写；必要时回退解析 | locator 写入/查询/唯一约束测试 | 后续交易、回调、商户订单查询定位测试 | 新交易 locator 覆盖率 100%，历史回退可控 | Phase 1 | 暂停审核回填和切流 |
| Phase 3 | 建立风控公共上下文和敏感值能力 | `service-openapi`、`service-payment`、`service-risk`、`service-admin`、`component-common` | `RiskPaymentContext`、字段标准化器、HMAC 服务、DTO 扩展 | 可新增 HMAC key version 字段或密钥表 | HMAC key、keyVersion、脱敏策略 | 旧 SHA-256 字段双读，新增 HMAC 字段双写 | 敏感字段处理错误风险高 | 关闭新规则读取，保留旧骨架 | 标准化、HMAC、脱敏、字段映射测试 | OpenAPI 到 payment 到 risk 字段透传测试 | 不泄露 PAN/CVV/密钥，字段覆盖矩阵通过 | Phase 0，密钥决策 | 暂停审核敏感值策略 |
| Phase 4 | 接入 AML、黑白名单运行时 | `service-risk`、`service-admin` | 风控规则 Repository、缓存、命中引擎、记录写入 | 写 `risk_evaluation_record`、`risk_evaluation_hit_detail` | 缓存 TTL、刷新、运行时开关 | 灰度按商户/规则维度开启，只记录不拦截到强拦截 | 误杀真实交易 | 灰度开关切回 record-only 或禁用具体规则 | 各名单类型命中/有效期/商户范围测试 | 初始交易和后续交易风控链路测试 | 管理端配置能影响测试交易决策 | Phase 3 | 暂停审核规则生效范围 |
| Phase 5 | 接入限额、频率、3DS、VIP 等规则 | `service-risk`、`component-redis`、`service-payment` | Rule engine、Redis counter、3DS 决策映射 | 规则记录和命中明细复用 | Redis key 前缀、窗口、阈值、异常策略 | record-only 到 enforce 灰度 | Redis 计数偏差或异常策略影响交易 | 规则开关回退；清理 Redis 测试 key | 限额/频率窗口、VIP、仅 VIP 可交易测试 | 并发频率、Redis 不可用测试 | 决策优先级符合用户确认 | Phase 4，异常策略决策 | 暂停审核限额/频率 |
| Phase 6 | 重构流程事件真实节点 | `service-openapi`、`service-payment`、`service-risk` | `TransactionFlowEventRecorder`、事件枚举、事件 DTO、交易准备/记录节点 | 扩展 `transaction_flow_event` 字段和唯一键 | 事件开关、detail 大小限制 | 旧事件保留；新事件双写或替换需灰度 | 事件失败影响主交易策略需谨慎 | 关闭新事件写入，回退旧 `recordFlowEvents` | 事件 key、顺序、耗时、trace/request 测试 | 风控拒绝、路由失败、渠道超时全链路事件测试 | 不再出现伪 PASS；时间不再全相同 | Phase 2-4 | 暂停审核事件模型 |
| Phase 7 | 补齐回调、MQ、通知事件 | `service-payment`、`service-openapi`、`service-job` | 回调安全支持、Outbox relay、Merchant notification service | 可能扩展回调/通知日志索引 | 事件失败告警配置 | 明细仍在专表，流程事件只写摘要 | 事件量上升 | 按事件类型关闭写入 | 回调重复、MQ 重试、通知重试事件幂等测试 | 回调验签失败、终态忽略、通知失败重试测试 | 时间轴可覆盖回调/MQ/通知 | Phase 6 | 暂停审核异步事件 |
| Phase 8 | 历史兼容与迁移 | `docs/sql`、`service-job`、`service-payment` | 回填 job/script、校验 SQL、回滚 SQL | locator 回填、HMAC 回填、事件字段兼容 | 迁移批次、限速、只读校验 | 先小范围、再季度批次；保留旧字段 | 大数据量回填影响库性能 | 按批次回滚新增数据或停用新读取 | 回填转换函数测试 | 样本季度回填验证 | 数据校验 SQL 通过，误差归零或可解释 | Phase 2-7 | 暂停审核迁移结果 |
| Phase 9 | 全链路测试和验收 | 全模块 | 测试补齐、验收报告 | 不新增，使用前述变更 | 上线开关确认 | 灰度商户、灰度规则、逐步扩大 | 覆盖不足导致线上回归 | 功能开关、配置回退、DB 向后兼容 | 全部单元/Mapper/Repository 测试 | OpenAPI 全交易、回调、MQ、通知、性能测试 | P0 关闭，P1 有计划，P2 入治理 | Phase 8 | 暂停等待上线授权 |

## 3. 不建议一次性处理的范围

| 范围 | 原因 | 建议 |
|---|---|---|
| 直接切换 ShardingSphere | 分表访问、MyBatis Plus、跨表分页、事务和配置兼容风险高 | 单独 PoC，不能和本次 P0 修复混做 |
| 一次性删除所有 BaseMapper 继承 | 影响所有 Mapper 编译和测试，风险较高 | 先对核心分表 Mapper 收口，再治理其他表 |
| 一次性强拦截所有风控规则 | 当前字段、配置、命中明细和误杀策略未完备 | 先 record-only，再灰度 enforce |
| 立即删除旧流程事件 | 历史详情页和查询可能依赖旧字段 | 兼容读取，新增字段向后兼容 |
| 直接修改生产 Nacos | 本轮无授权，且需配置快照和审批 | 只提出配置门禁，由用户授权后执行 |

## 4. 第一批建议执行顺序

1. 先做 Phase 0：补架构守护测试和配置快照审计，确认本审计结论。
2. 并行设计 Phase 1/2：Repository 边界和 locator DDL，这是分表、事件、风控关联的基础。
3. 再做 Phase 3/4：风控字段和管理端配置运行时接入，关闭 Noop 风险。
4. 最后做 Phase 6/7：事件真实节点改造，避免先修文案后改业务导致二次返工。
