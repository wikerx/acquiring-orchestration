# ARCH-REVIEW-001 数据库与迁移设计

> 已废止（2026-08-02）：本文的 locator、历史回填和双写设计未进入第一版实现，不得执行其中 DDL 或恢复交易号解析回退。
> 当前数据库规则仅以版本化 ShardingSphere 草案、23 表治理契约和已确认 SQL 草案为准。

本文件是设计草案，不执行 DDL，不修改现有 SQL 文件。

## 1. 拟新增表

### 1.1 `transaction_shard_locator`

用途：永久保存交易、动作、商户订单、渠道编号到交易季度分表的定位关系。它不是幂等表，不能因请求幂等 TTL 过期而消失。

建议字段：

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `id` | `BIGINT` | PK AUTO_INCREMENT | 物理主键 |
| `transaction_id` | `VARCHAR(64)` | NOT NULL | 平台当前交易号 |
| `operation_id` | `VARCHAR(64)` | NULL | 生命周期操作号 |
| `merchant_id` | `VARCHAR(32)` | NOT NULL | 商户号 |
| `merchant_order_no` | `VARCHAR(128)` | NULL | 商户原始订单号 |
| `merchant_operation_no` | `VARCHAR(128)` | NULL | 后续交易动作号 |
| `transaction_type` | `VARCHAR(64)` | NOT NULL | 交易类型 |
| `source_transaction_id` | `VARCHAR(64)` | NULL | 源交易号 |
| `source_operation_id` | `VARCHAR(64)` | NULL | 源操作号 |
| `transaction_date_time` | `DATETIME(3)` | NOT NULL | 当前交易分表时间 |
| `operation_date_time` | `DATETIME(3)` | NULL | 生命周期主单分表时间 |
| `transaction_quarter` | `VARCHAR(6)` | NOT NULL | `yyyyQQ` |
| `order_quarter` | `VARCHAR(6)` | NULL | 主单所在季度 |
| `channel_order_no` | `VARCHAR(128)` | NULL | 渠道订单号 |
| `channel_transaction_id` | `VARCHAR(128)` | NULL | 渠道交易号 |
| `status` | `VARCHAR(32)` | NOT NULL | 当前状态摘要 |
| `create_time` | `DATETIME(3)` | NOT NULL | 创建时间 |
| `update_time` | `DATETIME(3)` | NOT NULL | 更新时间 |
| `deleted` | `BIGINT` | NOT NULL DEFAULT 0 | 软删除字段，仅用于兼容风格，不建议物理删除 locator |

建议索引：

| 索引 | 字段 | 目的 |
|---|---|---|
| `uk_locator_transaction` | `transaction_id, deleted` | 单笔交易精确定位 |
| `idx_locator_operation` | `operation_id, deleted` | 生命周期主单/动作定位 |
| `idx_locator_source` | `source_transaction_id, transaction_type, deleted` | 后续交易关联 |
| `idx_locator_merchant_order` | `merchant_id, merchant_order_no, transaction_type, transaction_date_time` | 商户订单查询和冲突校验 |
| `idx_locator_channel` | `channel_order_no, channel_transaction_id, deleted` | 渠道回调定位 |
| `idx_locator_time` | `transaction_date_time, id` | 回填和校验 |

### 1.2 `risk_hmac_key_metadata`

用途：记录 HMAC key 版本元数据，不保存真实密钥。真实密钥应由 K8s Secret、环境变量、密钥管理服务或配置中心加密项提供。

| 字段 | 类型 | 说明 |
|---|---|---|
| `key_version` | `VARCHAR(32)` | 版本号 |
| `algorithm` | `VARCHAR(32)` | `HMAC_SHA256` |
| `status` | `TINYINT` | 1 启用，0 停用 |
| `effective_time` | `DATETIME(3)` | 生效时间 |
| `expire_time` | `DATETIME(3)` | 失效时间 |
| `remark` | `VARCHAR(500)` | 备注 |

## 2. 拟修改表

### 2.1 `transaction_flow_event`

当前字段见 `docs/sql/transaction-core-schema.sql:562-590`。建议新增：

| 字段 | 类型 | 是否必填 | 说明 |
|---|---|---|---|
| `event_key` | `VARCHAR(191)` | 是 | 业务幂等键 |
| `event_sequence` | `INT` | 否 | 同一 operation 内展示顺序 |
| `result_code` | `VARCHAR(64)` | 否 | 稳定结果码 |
| `result_message` | `VARCHAR(512)` | 否 | 业务结果说明 |
| `trace_id` | `VARCHAR(128)` | 否 | 链路 traceId |
| `request_id` | `VARCHAR(128)` | 否 | OpenAPI/内部请求 ID |
| `duration_millis` | `INT` | 否 | 节点耗时 |
| `attempt_no` | `INT` | 否 | 回调/MQ/通知尝试序号 |
| `risk_record_no` | `VARCHAR(64)` | 否 | 风控记录号 |
| `detail_json` | `VARCHAR(4096)` 或 `JSON` | 否 | 小型摘要，敏感字段必须脱敏 |

建议索引：

| 索引 | 字段 | 目的 |
|---|---|---|
| `uk_flow_event_key` | `event_key` | 事件幂等 |
| `idx_trace_time` | `trace_id, event_time` | 链路排障 |
| `idx_request_time` | `request_id, event_time` | 单次 OpenAPI 请求追踪 |
| `idx_risk_record` | `risk_record_no` | 风控记录关联 |
| `idx_operation_sequence` | `operation_id, event_sequence, event_time` | 时间线展示 |

兼容策略：

1. 新字段先允许 NULL，应用双写稳定后再评估 NOT NULL。
2. 旧历史事件 `event_key` 可按 `LEGACY:{flow_event_id}` 回填。
3. 旧详情页继续兼容旧字段，前端逐步读取新字段。

### 2.2 `risk_evaluation_record`

当前管理端 DDL 已存在 `risk_evaluation_record`，但运行时未写。建议补齐或确认字段：

| 字段 | 说明 |
|---|---|
| `risk_record_no` | 唯一风控记录号 |
| `transaction_id`、`operation_id`、`merchant_id` | 交易关联 |
| `transaction_type`、`risk_scenario` | 首次/请款/退款/撤销等场景 |
| `decision`、`reason_code`、`reason_message` | 决策结果 |
| `risk_level` | 风险级别 |
| `config_snapshot_version`、`config_snapshot_hash` | 配置版本 |
| `request_snapshot_json_masked` | 输入快照脱敏摘要 |
| `start_time`、`decision_time`、`duration_millis` | 真实时间 |
| `transaction_date_time` | 与交易分表时间一致 |

建议索引：

| 索引 | 字段 |
|---|---|
| `uk_risk_record_no` | `risk_record_no` |
| `idx_risk_transaction` | `transaction_id, operation_id` |
| `idx_risk_merchant_time` | `merchant_id, decision_time` |
| `idx_risk_decision_time` | `decision, decision_time` |

### 2.3 `risk_evaluation_hit_detail`

建议字段：

| 字段 | 说明 |
|---|---|
| `risk_record_no` | 关联评估记录 |
| `module_type` | AML/BLACK/WHITE/RULE |
| `function_code` | `card`、`ip`、`emailDomain` 等 |
| `rule_id`、`list_record_id` | 规则或名单主键 |
| `hit_field` | 命中字段 |
| `hit_value_masked` | 脱敏展示 |
| `hit_value_hmac` | HMAC 匹配值 |
| `hmac_key_version` | 密钥版本 |
| `decision_action` | REJECT/REVIEW/PASS/REQUIRE_3DS |
| `priority` | 决策优先级 |
| `snapshot_hash` | 配置快照 |

建议索引：

| 索引 | 字段 |
|---|---|
| `idx_hit_record` | `risk_record_no` |
| `idx_hit_rule` | `module_type, function_code, rule_id` |
| `idx_hit_value` | `function_code, hit_value_hmac` |

### 2.4 风控名单表

当前名单表普遍有 `match_value_hash`，建议新增：

| 字段 | 类型 | 说明 |
|---|---|---|
| `match_value_hmac` | `VARCHAR(128)` | HMAC-SHA256 匹配值 |
| `hmac_key_version` | `VARCHAR(32)` | 密钥版本 |
| `normalized_value_version` | `VARCHAR(32)` | 标准化算法版本 |

迁移期双读：

1. 新增/编辑名单双写 `match_value_hash` 和 `match_value_hmac`。
2. 运行时优先 HMAC；历史未回填记录可降级 SHA-256 只读，命中明细标记 `legacyHashMatched=true`。
3. 回填完成后关闭 SHA-256 运行时匹配。

## 3. 分表规则设计

| 表 | 是否分表 | 分表字段 | 说明 |
|---|---|---|---|
| `transaction_shard_locator` | 建议不按季度分表，或按 `transaction_date_time` 后续评估 | `transaction_date_time` | 初期数据量可控时单表利于定位；若分表则必须有二级索引或全局索引设计 |
| `transaction_flow_event` | 是 | `transaction_date_time` | 与交易事实同季度 |
| `risk_evaluation_record` | 可先不分表，数据量高后按月/季度评估 | `decision_time` 或 `transaction_date_time` | 需能按 `riskRecordNo` 精确查询 |
| `risk_evaluation_hit_detail` | 跟随 `risk_evaluation_record` | `decision_time` 或 `transaction_date_time` | 命中明细量大，需归档策略 |

建议：不要一开始把所有风控记录纳入交易季度分表。交易详情通过 `riskRecordNo` 精确查风险记录，风险后台按风控时间查询；数据量增长后再按风控记录表独立分区或分表。

## 4. 历史数据回填

### 4.1 Locator 回填

来源表：

| 来源 | 用途 |
|---|---|
| `transaction_operation_yyyyQQ` | 回填 transactionId、operationId、sourceTransactionId、merchant 信息、交易时间 |
| `transaction_order_yyyyQQ` | 回填主单 operationDateTime、生命周期状态 |
| `transaction_channel_request_yyyyQQ` | 回填 channelOrderNo、channelTransactionId |
| `transaction_channel_callback_yyyyQQ` | 校验渠道回调定位覆盖 |

回填顺序：

1. 按季度从旧到新扫描 `transaction_operation_yyyyQQ`。
2. 插入 `transaction_shard_locator`，冲突时比对字段并记录差异。
3. 补充主单季度和渠道字段。
4. 校验 transactionId 覆盖率、operationId 覆盖率、channel 定位覆盖率。
5. 开启新交易双写后，再开启 locator 读取。

校验 SQL 草案：

```sql
-- 每季度动作单交易数与 locator 覆盖数对账
SELECT COUNT(1) AS operation_count
FROM transaction_operation_202603
WHERE deleted = 0;

SELECT COUNT(1) AS locator_count
FROM transaction_shard_locator
WHERE transaction_quarter = '202603'
  AND deleted = 0;

-- 检查 locator 重复交易号
SELECT transaction_id, COUNT(1)
FROM transaction_shard_locator
WHERE deleted = 0
GROUP BY transaction_id
HAVING COUNT(1) > 1;
```

### 4.2 风控 HMAC 回填

1. 只在授权环境运行，密钥来自 Secret。
2. 对每个风险名单表按主键分批读取密文或原始规范值。
3. 计算 `match_value_hmac` 和 `hmac_key_version`。
4. 校验启用记录 HMAC 覆盖率。
5. 运行时 HMAC 双读，稳定后禁用 SHA-256。

### 4.3 流程事件兼容回填

1. 旧事件不伪造真实历史时间。
2. `event_key` 回填为 `LEGACY:{flow_event_id}`。
3. `trace_id/request_id/duration_millis` 无法确定时保持 NULL。
4. 新详情页展示时标记 legacy event。

## 5. 灰度兼容

| 能力 | 灰度开关 | 默认建议 |
|---|---|---|
| locator 双写 | `payment.shard-locator.write-enabled` | true in test，灰度生产 |
| locator 读优先 | `payment.shard-locator.read-priority-enabled` | false -> true 分阶段 |
| 风控运行时配置 | `risk.runtime-config.enabled` | record-only 先开启 |
| 风控强拦截 | `risk.runtime-config.enforce-enabled` | 按商户/规则灰度 |
| HMAC 匹配 | `risk.hmac-match.enabled` | 双读后开启 |
| 新流程事件 | `payment.flow-event.v2-enabled` | 双写灰度 |
| 旧事件批量补写 | `payment.flow-event.legacy-batch-enabled` | 新事件稳定后关闭 |

## 6. 回滚方案

| 变更 | 回滚策略 |
|---|---|
| locator 读优先 | 关闭读优先，回退交易号解析；保留双写数据 |
| locator 双写 | 关闭双写，不删除 locator 表 |
| 风控配置运行时读取 | 切回 record-only 或关闭具体规则；Noop 不允许在生产作为回滚方案，除非用户明确授权 |
| HMAC 匹配 | 临时恢复 SHA-256 双读，保留 HMAC 字段 |
| 新流程事件 | 关闭 v2 写入，保留旧事件展示 |
| DDL 字段新增 | 不物理删除字段；应用回退到不读取新字段 |

## 7. 数据量与保留策略

| 数据 | 建议 |
|---|---|
| `transaction_flow_event` | 摘要型事件，按季度分表；单笔 15-40 条为目标；大明细不进 `detail_json` |
| `risk_evaluation_hit_detail` | 命中明细可多条，按风控审计周期归档 |
| 通知/MQ/回调明细 | 专表保留，流程事件只保留摘要和 referenceId |
| locator | 长期保留，不应跟随幂等 TTL 清理 |
