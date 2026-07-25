-- BUG-VERIFY-001-001 / 05G DDL 与持久化模型对齐迁移草案
--
-- 重要说明：
-- 1. 本文件只提供人工执行前检查、兼容迁移、校验与回滚草案；本轮 Codex 不执行任何 DDL。
-- 2. 执行前必须完成变更评审、备份、窗口确认和真实库名替换。
-- 3. 当前基准以 service-payment 的 DO/Mapper 持久化模型和已完成交易一致性修复为准：
--    - operation_id：平台内部生命周期关联标识，同一原始交易生命周期内保持不变。
--    - transaction_id：平台当前交易唯一标识，每一笔授权、请款、退款、Void 等动作各不相同。
--    - transaction_idempotency：非分表全局幂等兜底表。
-- 4. 本文件不得通过脚本自动执行；请逐段复制到经审批的数据库会话执行。
-- 5. MySQL UNIQUE KEY 允许多行 NULL 不冲突；创建包含可空列的唯一索引前必须先确认业务是否接受该语义。
-- 6. 本文件中的 ALTER 语句不是幂等脚本：ADD COLUMN 仅在字段缺失时执行，DROP INDEX 仅在旧索引存在时执行，ADD INDEX/UNIQUE 仅在索引缺失且历史数据检查为 0 行时执行。

SET NAMES utf8mb4;

-- ============================================================
-- 0. 执行前人工参数
-- ============================================================
-- 请人工替换目标库名。
SET @schema_name := DATABASE();

-- 当前季度与下一季度物理表。2026-07-24 属于 2026Q3，下一季度是 2026Q4。
SET @current_quarter_suffix := '202603';
SET @next_quarter_suffix := '202604';

-- 参与 05G 的核心逻辑表。
-- transaction_idempotency 不分表，其余表依赖模板表和季度物理表同步。

-- ============================================================
-- 1. 版本与表存在检查
-- ============================================================
SELECT 'schema' AS check_item, @schema_name AS value;

SELECT table_name
FROM information_schema.tables
WHERE table_schema = @schema_name
  AND table_name IN (
    'transaction_idempotency',
    'transaction_order',
    'transaction_operation',
    'transaction_channel_request',
    'transaction_event_outbox',
    'transaction_status_history',
    'transaction_order_202603',
    'transaction_operation_202603',
    'transaction_channel_request_202603',
    'transaction_event_outbox_202603',
    'transaction_status_history_202603',
    'transaction_order_202604',
    'transaction_operation_202604',
    'transaction_channel_request_202604',
    'transaction_event_outbox_202604',
    'transaction_status_history_202604'
  )
ORDER BY table_name;

-- 若以上任一目标表不存在，先根据 docs/sql/transaction-core-schema.sql 与物理表脚本建表评审；
-- 不要跳过模板表检查直接 ALTER 物理表。

-- ============================================================
-- 2. 字段存在、类型、空值和默认值检查
-- ============================================================
SELECT table_name, column_name, column_type, is_nullable, column_default, extra
FROM information_schema.columns
WHERE table_schema = @schema_name
  AND (
    (table_name = 'transaction_idempotency'
      AND column_name IN ('merchant_order_id', 'idempotency_scope', 'idempotency_key', 'transaction_id', 'operation_id', 'request_fingerprint', 'result_snapshot', 'expire_time', 'version'))
    OR (table_name REGEXP '^transaction_order(_202603|_202604)?$'
      AND column_name IN ('operation_id', 'root_transaction_id', 'latest_transaction_id', 'merchant_order_id', 'root_operation_id', 'latest_operation_id', 'merchant_transaction_id', 'version'))
    OR (table_name REGEXP '^transaction_operation(_202603|_202604)?$'
      AND column_name IN ('operation_id', 'transaction_id', 'source_transaction_id', 'source_operation_id', 'merchant_order_id', 'merchant_operation_no', 'version'))
    OR (table_name REGEXP '^transaction_channel_request(_202603|_202604)?$'
      AND column_name IN ('request_id', 'transaction_id', 'operation_id', 'channel_id', 'channel_order_no', 'channel_transaction_id', 'request_status', 'version'))
    OR (table_name REGEXP '^transaction_event_outbox(_202603|_202604)?$'
      AND column_name IN ('event_no', 'message_key', 'event_status', 'retry_count', 'max_retry_count', 'next_retry_time', 'version'))
    OR (table_name REGEXP '^transaction_status_history(_202603|_202604)?$'
      AND column_name IN ('status_history_id', 'transaction_id', 'operation_id', 'from_status', 'to_status', 'version_before', 'version_after'))
  )
ORDER BY table_name, column_name;

-- 2.1 新模型字段缺失清单。若 status=MISSING，才允许执行后续对应 ADD COLUMN。
SELECT expected.table_name, expected.column_name,
       CASE WHEN actual.column_name IS NULL THEN 'MISSING' ELSE 'EXISTS' END AS status
FROM (
  SELECT 'transaction_idempotency' AS table_name, 'merchant_order_id' AS column_name
  UNION ALL SELECT 'transaction_order', 'operation_id'
  UNION ALL SELECT 'transaction_order', 'root_transaction_id'
  UNION ALL SELECT 'transaction_order', 'latest_transaction_id'
  UNION ALL SELECT 'transaction_order', 'merchant_order_id'
  UNION ALL SELECT 'transaction_operation', 'source_operation_id'
  UNION ALL SELECT 'transaction_operation', 'merchant_operation_no'
  UNION ALL SELECT 'transaction_channel_request', 'request_id'
  UNION ALL SELECT 'transaction_channel_request', 'channel_order_no'
  UNION ALL SELECT 'transaction_channel_request', 'channel_transaction_id'
  UNION ALL SELECT CONCAT('transaction_order_', @current_quarter_suffix), 'operation_id'
  UNION ALL SELECT CONCAT('transaction_order_', @current_quarter_suffix), 'root_transaction_id'
  UNION ALL SELECT CONCAT('transaction_order_', @current_quarter_suffix), 'latest_transaction_id'
  UNION ALL SELECT CONCAT('transaction_order_', @current_quarter_suffix), 'merchant_order_id'
  UNION ALL SELECT CONCAT('transaction_operation_', @current_quarter_suffix), 'source_operation_id'
  UNION ALL SELECT CONCAT('transaction_operation_', @current_quarter_suffix), 'merchant_operation_no'
  UNION ALL SELECT CONCAT('transaction_channel_request_', @current_quarter_suffix), 'request_id'
  UNION ALL SELECT CONCAT('transaction_channel_request_', @current_quarter_suffix), 'channel_order_no'
  UNION ALL SELECT CONCAT('transaction_channel_request_', @current_quarter_suffix), 'channel_transaction_id'
  UNION ALL SELECT CONCAT('transaction_order_', @next_quarter_suffix), 'operation_id'
  UNION ALL SELECT CONCAT('transaction_order_', @next_quarter_suffix), 'root_transaction_id'
  UNION ALL SELECT CONCAT('transaction_order_', @next_quarter_suffix), 'latest_transaction_id'
  UNION ALL SELECT CONCAT('transaction_order_', @next_quarter_suffix), 'merchant_order_id'
  UNION ALL SELECT CONCAT('transaction_operation_', @next_quarter_suffix), 'source_operation_id'
  UNION ALL SELECT CONCAT('transaction_operation_', @next_quarter_suffix), 'merchant_operation_no'
  UNION ALL SELECT CONCAT('transaction_channel_request_', @next_quarter_suffix), 'request_id'
  UNION ALL SELECT CONCAT('transaction_channel_request_', @next_quarter_suffix), 'channel_order_no'
  UNION ALL SELECT CONCAT('transaction_channel_request_', @next_quarter_suffix), 'channel_transaction_id'
) expected
LEFT JOIN information_schema.columns actual
  ON actual.table_schema = @schema_name
 AND actual.table_name = expected.table_name
 AND actual.column_name = expected.column_name
ORDER BY expected.table_name, expected.column_name;

-- 2.2 索引存在检查。后续 DROP/ADD INDEX 必须以该结果为准。
SELECT table_name, index_name, non_unique,
       GROUP_CONCAT(column_name ORDER BY seq_in_index) AS columns
FROM information_schema.statistics
WHERE table_schema = @schema_name
  AND table_name IN (
    'transaction_idempotency',
    'transaction_order',
    'transaction_operation',
    'transaction_channel_request',
    CONCAT('transaction_order_', @current_quarter_suffix),
    CONCAT('transaction_operation_', @current_quarter_suffix),
    CONCAT('transaction_channel_request_', @current_quarter_suffix),
    CONCAT('transaction_order_', @next_quarter_suffix),
    CONCAT('transaction_operation_', @next_quarter_suffix),
    CONCAT('transaction_channel_request_', @next_quarter_suffix)
  )
  AND index_name IN (
    'uk_scope_key',
    'idx_merchant_order_id',
    'uk_operation_id',
    'uk_root_transaction_id',
    'idx_latest_transaction_id',
    'uk_transaction_id',
    'idx_operation_time',
    'idx_source_operation',
    'uk_merchant_operation',
    'uk_request_id',
    'idx_channel_transaction_identity'
  )
GROUP BY table_name, index_name, non_unique
ORDER BY table_name, index_name;

-- 阻断条件：
-- 1. transaction_operation 存在旧持久化列 merchant_order_id 且缺 merchant_operation_no 或 source_operation_id。
-- 2. transaction_order 仍使用 root_operation_id/latest_operation_id/merchant_transaction_id 且缺 root_transaction_id/latest_transaction_id/merchant_order_id。
-- 3. transaction_idempotency 缺 merchant_order_id。
-- 4. transaction_channel_request.channel_id 为 NOT NULL，但真实写入路径允许 routeResult 为空。

-- ============================================================
-- 3. 历史重复数据与空值检查
-- ============================================================
-- 3.1 幂等表全局唯一检查。
SELECT idempotency_scope, idempotency_key, COUNT(*) AS cnt
FROM transaction_idempotency
WHERE deleted = 0
GROUP BY idempotency_scope, idempotency_key
HAVING COUNT(*) > 1;

-- 3.2 幂等表商户请求 ID 空值检查。
SELECT COUNT(*) AS idempotency_merchant_order_id_null_cnt
FROM transaction_idempotency
WHERE deleted = 0
  AND merchant_order_id IS NULL
  AND idempotency_scope IN ('TRANSACTION_OPERATION', 'PAYMENT_CREATE', 'AUTHORIZATION', 'CAPTURE', 'REFUND', 'VOID', 'INCREMENTAL_AUTHORIZATION');

-- 3.3 主单生命周期唯一检查。
SELECT operation_id, COUNT(*) AS cnt, MIN(transaction_date_time) AS min_time, MAX(transaction_date_time) AS max_time
FROM transaction_order
WHERE deleted = 0
GROUP BY operation_id
HAVING COUNT(*) > 1;

-- 当前/下一季度物理表同样检查。历史季度请按实际 suffix 扩展。
SELECT operation_id, COUNT(*) AS cnt
FROM transaction_order_202603
WHERE deleted = 0
GROUP BY operation_id
HAVING COUNT(*) > 1;

SELECT operation_id, COUNT(*) AS cnt
FROM transaction_order_202604
WHERE deleted = 0
GROUP BY operation_id
HAVING COUNT(*) > 1;

-- 3.4 动作单平台交易 ID 唯一检查。
SELECT transaction_id, COUNT(*) AS cnt, MIN(transaction_date_time) AS min_time, MAX(transaction_date_time) AS max_time
FROM transaction_operation
WHERE deleted = 0
GROUP BY transaction_id
HAVING COUNT(*) > 1;

SELECT transaction_id, COUNT(*) AS cnt
FROM transaction_operation_202603
WHERE deleted = 0
GROUP BY transaction_id
HAVING COUNT(*) > 1;

SELECT transaction_id, COUNT(*) AS cnt
FROM transaction_operation_202604
WHERE deleted = 0
GROUP BY transaction_id
HAVING COUNT(*) > 1;

-- 3.5 后续动作商户动作号唯一检查。
-- 注意：source_transaction_id 可为空，MySQL 唯一索引对 NULL 不互斥。
-- 若需要首笔 Payment/Auth 也强唯一，必须先经业务确认生成非空 source_transaction_id 或引入 generated column。
SELECT merchant_id, source_transaction_id, transaction_type, merchant_operation_no, COUNT(*) AS cnt,
       MIN(transaction_date_time) AS min_time, MAX(transaction_date_time) AS max_time
FROM transaction_operation
WHERE deleted = 0
  AND transaction_type IN ('CAPTURE', 'REFUND', 'VOID', 'INCREMENTAL_AUTHORIZATION')
GROUP BY merchant_id, source_transaction_id, transaction_type, merchant_operation_no
HAVING COUNT(*) > 1;

SELECT merchant_id, source_transaction_id, transaction_type, merchant_operation_no, COUNT(*) AS cnt
FROM transaction_operation_202603
WHERE deleted = 0
  AND transaction_type IN ('CAPTURE', 'REFUND', 'VOID', 'INCREMENTAL_AUTHORIZATION')
GROUP BY merchant_id, source_transaction_id, transaction_type, merchant_operation_no
HAVING COUNT(*) > 1;

SELECT merchant_id, source_transaction_id, transaction_type, merchant_operation_no, COUNT(*) AS cnt
FROM transaction_operation_202604
WHERE deleted = 0
  AND transaction_type IN ('CAPTURE', 'REFUND', 'VOID', 'INCREMENTAL_AUTHORIZATION')
GROUP BY merchant_id, source_transaction_id, transaction_type, merchant_operation_no
HAVING COUNT(*) > 1;

-- 3.6 新字段空值检查。
SELECT 'transaction_operation.source_operation_id' AS check_item, COUNT(*) AS null_cnt
FROM transaction_operation
WHERE deleted = 0
  AND transaction_type IN ('CAPTURE', 'REFUND', 'VOID', 'INCREMENTAL_AUTHORIZATION')
  AND source_operation_id IS NULL;

SELECT 'transaction_operation.merchant_operation_no' AS check_item, COUNT(*) AS null_cnt
FROM transaction_operation
WHERE deleted = 0
  AND merchant_operation_no IS NULL;

SELECT 'transaction_order.merchant_order_id' AS check_item, COUNT(*) AS null_cnt
FROM transaction_order
WHERE deleted = 0
  AND merchant_order_id IS NULL;

-- 3.7 渠道请求恢复身份重复检查。
SELECT channel_code, channel_order_no, channel_transaction_id, COUNT(*) AS cnt,
       MIN(transaction_date_time) AS min_time, MAX(transaction_date_time) AS max_time
FROM transaction_channel_request
WHERE deleted = 0
  AND channel_code IS NOT NULL
  AND channel_order_no IS NOT NULL
  AND channel_transaction_id IS NOT NULL
GROUP BY channel_code, channel_order_no, channel_transaction_id
HAVING COUNT(*) > 1;

-- ============================================================
-- 4. 兼容迁移 DDL 草案：逻辑模板表
-- ============================================================
-- 执行策略：
-- 1. 对模板表先加字段、回填、校验，再改 NOT NULL 和索引。
-- 2. 对物理表重复同样顺序。
-- 3. 若线上数据量较大，使用 gh-ost/pt-online-schema-change 或云厂商在线 DDL；不要在业务高峰直接锁表。
-- 4. 本节 ALTER 语句必须逐条人工选择执行：
--    - ADD COLUMN：仅当第 2.1 节对应字段为 MISSING。
--    - MODIFY NOT NULL：仅当第 3 节对应空值检查为 0。
--    - DROP INDEX：仅当第 2.2 节确认旧索引存在。
--    - ADD INDEX/UNIQUE：仅当第 2.2 节确认新索引缺失，且第 3 节重复数据检查为 0 行。

-- 4.1 transaction_idempotency：补 merchant_order_id。
-- 仅当 transaction_idempotency.merchant_order_id 缺失、idx_merchant_order_id 缺失时执行。
ALTER TABLE transaction_idempotency
  ADD COLUMN merchant_order_id VARCHAR(128) NULL COMMENT '商户本次API请求唯一标识，来自 orderInfo.orderId，用于资金类幂等。' AFTER merchant_order_no,
  ADD KEY idx_merchant_order_id (merchant_id, merchant_order_id, transaction_type);

-- 4.2 transaction_order：旧 v1.6 口径兼容迁移。
-- 若旧字段存在，可先 ADD 新字段，然后从旧字段兼容回填。
-- 仅对第 2.1 节显示 MISSING 的字段执行 ADD COLUMN；已存在字段不得重复 ADD。
ALTER TABLE transaction_order
  ADD COLUMN operation_id VARCHAR(64) NULL COMMENT '平台内部生命周期关联标识，同一原始交易生命周期内保持不变，不返回商户。' AFTER id,
  ADD COLUMN root_transaction_id VARCHAR(64) NULL COMMENT '生命周期内首个平台开户交易ID。' AFTER operation_id,
  ADD COLUMN latest_transaction_id VARCHAR(64) NULL COMMENT '最近一次平台开户交易ID。' AFTER root_transaction_id,
  ADD COLUMN merchant_order_id VARCHAR(128) NULL COMMENT '商户本次API请求唯一标识，来自 orderInfo.orderId，用于幂等和排查。' AFTER merchant_order_no;

UPDATE transaction_order
SET operation_id = COALESCE(operation_id, transaction_id),
    root_transaction_id = COALESCE(root_transaction_id, root_operation_id, transaction_id),
    latest_transaction_id = COALESCE(latest_transaction_id, latest_operation_id, transaction_id),
    merchant_order_id = COALESCE(merchant_order_id, merchant_transaction_id, merchant_order_no)
WHERE operation_id IS NULL
   OR root_transaction_id IS NULL
   OR latest_transaction_id IS NULL
   OR merchant_order_id IS NULL;

-- 校验通过后再设置 NOT NULL 和索引。若存在重复，必须先人工处理。
-- 仅当空值检查为 0 且 uk_operation_id/uk_root_transaction_id/idx_latest_transaction_id 缺失时执行对应 MODIFY/ADD。
ALTER TABLE transaction_order
  MODIFY COLUMN operation_id VARCHAR(64) NOT NULL COMMENT '平台内部生命周期关联标识，同一原始交易生命周期内保持不变，不返回商户。',
  MODIFY COLUMN root_transaction_id VARCHAR(64) NOT NULL COMMENT '生命周期内首个平台开户交易ID。',
  MODIFY COLUMN latest_transaction_id VARCHAR(64) NOT NULL COMMENT '最近一次平台开户交易ID。',
  MODIFY COLUMN merchant_order_id VARCHAR(128) NOT NULL COMMENT '商户本次API请求唯一标识，来自 orderInfo.orderId，用于幂等和排查。',
  ADD UNIQUE KEY uk_operation_id (operation_id),
  ADD UNIQUE KEY uk_root_transaction_id (root_transaction_id),
  ADD KEY idx_latest_transaction_id (latest_transaction_id);

-- 旧字段不要在同一发布内物理删除；先保留一版，待回滚窗口结束后另行评审 DROP。
-- ALTER TABLE transaction_order DROP COLUMN root_operation_id, DROP COLUMN latest_operation_id, DROP COLUMN merchant_transaction_id;

-- 4.3 transaction_operation：补源动作和商户动作号，移除旧动作单 operation_id 唯一键。
-- 仅对第 2.1 节显示 MISSING 的字段执行 ADD COLUMN；已存在字段不得重复 ADD。
ALTER TABLE transaction_operation
  ADD COLUMN source_operation_id VARCHAR(64) NULL COMMENT '源平台内部生命周期关联标识；请款关联授权、退款关联请款或原授权时使用。' AFTER source_transaction_id,
  ADD COLUMN merchant_operation_no VARCHAR(128) NULL COMMENT '商户动作单号，如 captureNo、refundNo；首笔可等于 merchant_order_no。' AFTER merchant_order_no;

UPDATE transaction_operation
SET merchant_operation_no = COALESCE(merchant_operation_no, merchant_order_id, merchant_order_no),
    source_operation_id = COALESCE(source_operation_id, operation_id)
WHERE merchant_operation_no IS NULL
   OR (transaction_type IN ('CAPTURE', 'REFUND', 'VOID', 'INCREMENTAL_AUTHORIZATION') AND source_operation_id IS NULL);

-- 仅当 merchant_operation_no 空值为 0、旧 uk_operation_id 存在、新索引缺失且重复数据检查为 0 行时执行。
ALTER TABLE transaction_operation
  MODIFY COLUMN merchant_operation_no VARCHAR(128) NOT NULL COMMENT '商户动作单号，如 captureNo、refundNo；首笔可等于 merchant_order_no。',
  DROP INDEX uk_operation_id,
  ADD UNIQUE KEY uk_transaction_id (transaction_id),
  ADD KEY idx_operation_time (operation_id, transaction_date_time),
  ADD KEY idx_source_operation (source_operation_id, transaction_date_time),
  ADD UNIQUE KEY uk_merchant_operation (merchant_id, source_transaction_id, transaction_type, merchant_operation_no);

-- 旧 merchant_order_id 已不再被 TransactionOperationDO 持久化；建议回滚窗口后另行评审删除。
-- ALTER TABLE transaction_operation DROP COLUMN merchant_order_id;

-- 4.4 transaction_channel_request：允许无路由结果时 channel_id 为空，并补渠道身份检索索引。
-- 仅当 channel_id 为 NOT NULL 或 idx_channel_transaction_identity 缺失时执行对应 MODIFY/ADD。
ALTER TABLE transaction_channel_request
  MODIFY COLUMN channel_id BIGINT NULL COMMENT '渠道信息ID；路由失败或仅记录异常链路时可为空。',
  ADD KEY idx_channel_transaction_identity (channel_code, channel_order_no, channel_transaction_id, transaction_date_time);

-- ============================================================
-- 5. 当前季度和下一季度物理表同步草案
-- ============================================================
-- 当前季度 2026Q3：_202603；下一季度 2026Q4：_202604。
-- 以下以核心 5 张表示例；历史季度请按实际 sys_sharding_physical_table 扩展执行。
-- 每张物理表同样必须先看第 2.1/2.2/3 节结果；以下 ALTER 不得整段重复执行。

-- 5.1 transaction_order_202603 / transaction_order_202604。
-- 仅对字段缺失的物理表执行 ADD COLUMN。
ALTER TABLE transaction_order_202603
  ADD COLUMN operation_id VARCHAR(64) NULL COMMENT '平台内部生命周期关联标识，同一原始交易生命周期内保持不变，不返回商户。' AFTER id,
  ADD COLUMN root_transaction_id VARCHAR(64) NULL COMMENT '生命周期内首个平台开户交易ID。' AFTER operation_id,
  ADD COLUMN latest_transaction_id VARCHAR(64) NULL COMMENT '最近一次平台开户交易ID。' AFTER root_transaction_id,
  ADD COLUMN merchant_order_id VARCHAR(128) NULL COMMENT '商户本次API请求唯一标识，来自 orderInfo.orderId，用于幂等和排查。' AFTER merchant_order_no;

ALTER TABLE transaction_order_202604
  ADD COLUMN operation_id VARCHAR(64) NULL COMMENT '平台内部生命周期关联标识，同一原始交易生命周期内保持不变，不返回商户。' AFTER id,
  ADD COLUMN root_transaction_id VARCHAR(64) NULL COMMENT '生命周期内首个平台开户交易ID。' AFTER operation_id,
  ADD COLUMN latest_transaction_id VARCHAR(64) NULL COMMENT '最近一次平台开户交易ID。' AFTER root_transaction_id,
  ADD COLUMN merchant_order_id VARCHAR(128) NULL COMMENT '商户本次API请求唯一标识，来自 orderInfo.orderId，用于幂等和排查。' AFTER merchant_order_no;

UPDATE transaction_order_202603
SET operation_id = COALESCE(operation_id, transaction_id),
    root_transaction_id = COALESCE(root_transaction_id, root_operation_id, transaction_id),
    latest_transaction_id = COALESCE(latest_transaction_id, latest_operation_id, transaction_id),
    merchant_order_id = COALESCE(merchant_order_id, merchant_transaction_id, merchant_order_no)
WHERE operation_id IS NULL
   OR root_transaction_id IS NULL
   OR latest_transaction_id IS NULL
   OR merchant_order_id IS NULL;

UPDATE transaction_order_202604
SET operation_id = COALESCE(operation_id, transaction_id),
    root_transaction_id = COALESCE(root_transaction_id, root_operation_id, transaction_id),
    latest_transaction_id = COALESCE(latest_transaction_id, latest_operation_id, transaction_id),
    merchant_order_id = COALESCE(merchant_order_id, merchant_transaction_id, merchant_order_no)
WHERE operation_id IS NULL
   OR root_transaction_id IS NULL
   OR latest_transaction_id IS NULL
   OR merchant_order_id IS NULL;

-- 仅当空值检查为 0 且目标索引缺失时执行对应 MODIFY/ADD。
ALTER TABLE transaction_order_202603
  MODIFY COLUMN operation_id VARCHAR(64) NOT NULL COMMENT '平台内部生命周期关联标识，同一原始交易生命周期内保持不变，不返回商户。',
  MODIFY COLUMN root_transaction_id VARCHAR(64) NOT NULL COMMENT '生命周期内首个平台开户交易ID。',
  MODIFY COLUMN latest_transaction_id VARCHAR(64) NOT NULL COMMENT '最近一次平台开户交易ID。',
  MODIFY COLUMN merchant_order_id VARCHAR(128) NOT NULL COMMENT '商户本次API请求唯一标识，来自 orderInfo.orderId，用于幂等和排查。',
  ADD UNIQUE KEY uk_operation_id (operation_id),
  ADD UNIQUE KEY uk_root_transaction_id (root_transaction_id),
  ADD KEY idx_latest_transaction_id (latest_transaction_id);

ALTER TABLE transaction_order_202604
  MODIFY COLUMN operation_id VARCHAR(64) NOT NULL COMMENT '平台内部生命周期关联标识，同一原始交易生命周期内保持不变，不返回商户。',
  MODIFY COLUMN root_transaction_id VARCHAR(64) NOT NULL COMMENT '生命周期内首个平台开户交易ID。',
  MODIFY COLUMN latest_transaction_id VARCHAR(64) NOT NULL COMMENT '最近一次平台开户交易ID。',
  MODIFY COLUMN merchant_order_id VARCHAR(128) NOT NULL COMMENT '商户本次API请求唯一标识，来自 orderInfo.orderId，用于幂等和排查。',
  ADD UNIQUE KEY uk_operation_id (operation_id),
  ADD UNIQUE KEY uk_root_transaction_id (root_transaction_id),
  ADD KEY idx_latest_transaction_id (latest_transaction_id);

-- 5.2 transaction_operation_202603 / transaction_operation_202604。
-- 仅对字段缺失的物理表执行 ADD COLUMN。
ALTER TABLE transaction_operation_202603
  ADD COLUMN source_operation_id VARCHAR(64) NULL COMMENT '源平台内部生命周期关联标识；请款关联授权、退款关联请款或原授权时使用。' AFTER source_transaction_id,
  ADD COLUMN merchant_operation_no VARCHAR(128) NULL COMMENT '商户动作单号，如 captureNo、refundNo；首笔可等于 merchant_order_no。' AFTER merchant_order_no;

ALTER TABLE transaction_operation_202604
  ADD COLUMN source_operation_id VARCHAR(64) NULL COMMENT '源平台内部生命周期关联标识；请款关联授权、退款关联请款或原授权时使用。' AFTER source_transaction_id,
  ADD COLUMN merchant_operation_no VARCHAR(128) NULL COMMENT '商户动作单号，如 captureNo、refundNo；首笔可等于 merchant_order_no。' AFTER merchant_order_no;

UPDATE transaction_operation_202603
SET merchant_operation_no = COALESCE(merchant_operation_no, merchant_order_id, merchant_order_no),
    source_operation_id = COALESCE(source_operation_id, operation_id)
WHERE merchant_operation_no IS NULL
   OR (transaction_type IN ('CAPTURE', 'REFUND', 'VOID', 'INCREMENTAL_AUTHORIZATION') AND source_operation_id IS NULL);

UPDATE transaction_operation_202604
SET merchant_operation_no = COALESCE(merchant_operation_no, merchant_order_id, merchant_order_no),
    source_operation_id = COALESCE(source_operation_id, operation_id)
WHERE merchant_operation_no IS NULL
   OR (transaction_type IN ('CAPTURE', 'REFUND', 'VOID', 'INCREMENTAL_AUTHORIZATION') AND source_operation_id IS NULL);

-- 仅当空值检查为 0、旧 uk_operation_id 存在、新索引缺失且重复数据检查为 0 行时执行。
ALTER TABLE transaction_operation_202603
  MODIFY COLUMN merchant_operation_no VARCHAR(128) NOT NULL COMMENT '商户动作单号，如 captureNo、refundNo；首笔可等于 merchant_order_no。',
  DROP INDEX uk_operation_id,
  ADD UNIQUE KEY uk_transaction_id (transaction_id),
  ADD KEY idx_operation_time (operation_id, transaction_date_time),
  ADD KEY idx_source_operation (source_operation_id, transaction_date_time),
  ADD UNIQUE KEY uk_merchant_operation (merchant_id, source_transaction_id, transaction_type, merchant_operation_no);

ALTER TABLE transaction_operation_202604
  MODIFY COLUMN merchant_operation_no VARCHAR(128) NOT NULL COMMENT '商户动作单号，如 captureNo、refundNo；首笔可等于 merchant_order_no。',
  DROP INDEX uk_operation_id,
  ADD UNIQUE KEY uk_transaction_id (transaction_id),
  ADD KEY idx_operation_time (operation_id, transaction_date_time),
  ADD KEY idx_source_operation (source_operation_id, transaction_date_time),
  ADD UNIQUE KEY uk_merchant_operation (merchant_id, source_transaction_id, transaction_type, merchant_operation_no);

-- 5.3 transaction_channel_request_202603 / transaction_channel_request_202604。
-- 仅当 channel_id 为 NOT NULL 或 idx_channel_transaction_identity 缺失时执行对应 MODIFY/ADD。
ALTER TABLE transaction_channel_request_202603
  MODIFY COLUMN channel_id BIGINT NULL COMMENT '渠道信息ID；路由失败或仅记录异常链路时可为空。',
  ADD KEY idx_channel_transaction_identity (channel_code, channel_order_no, channel_transaction_id, transaction_date_time);

ALTER TABLE transaction_channel_request_202604
  MODIFY COLUMN channel_id BIGINT NULL COMMENT '渠道信息ID；路由失败或仅记录异常链路时可为空。',
  ADD KEY idx_channel_transaction_identity (channel_code, channel_order_no, channel_transaction_id, transaction_date_time);

-- 5.4 event_outbox/status_history 当前结构与 Java/Mapper 基本一致；执行字段检查即可。

-- ============================================================
-- 6. 执行后校验 SQL
-- ============================================================
SELECT table_name, column_name, column_type, is_nullable, column_default, extra
FROM information_schema.columns
WHERE table_schema = @schema_name
  AND table_name IN (
    'transaction_idempotency',
    'transaction_order',
    'transaction_operation',
    'transaction_channel_request',
    'transaction_order_202603',
    'transaction_operation_202603',
    'transaction_channel_request_202603',
    'transaction_order_202604',
    'transaction_operation_202604',
    'transaction_channel_request_202604'
  )
  AND column_name IN (
    'merchant_order_id',
    'operation_id',
    'root_transaction_id',
    'latest_transaction_id',
    'transaction_id',
    'source_operation_id',
    'merchant_operation_no',
    'channel_id',
    'channel_order_no',
    'channel_transaction_id',
    'version'
  )
ORDER BY table_name, column_name;

SELECT table_name, index_name, non_unique, GROUP_CONCAT(column_name ORDER BY seq_in_index) AS columns
FROM information_schema.statistics
WHERE table_schema = @schema_name
  AND table_name IN (
    'transaction_idempotency',
    'transaction_order',
    'transaction_operation',
    'transaction_channel_request',
    'transaction_order_202603',
    'transaction_operation_202603',
    'transaction_channel_request_202603',
    'transaction_order_202604',
    'transaction_operation_202604',
    'transaction_channel_request_202604'
  )
  AND index_name IN (
    'uk_scope_key',
    'idx_merchant_order_id',
    'uk_operation_id',
    'uk_root_transaction_id',
    'idx_latest_transaction_id',
    'uk_transaction_id',
    'idx_operation_time',
    'idx_source_operation',
    'uk_merchant_operation',
    'uk_request_id',
    'idx_channel_transaction_identity'
  )
GROUP BY table_name, index_name, non_unique
ORDER BY table_name, index_name;

-- 模板与物理表字段数量对比。
SELECT template.table_name AS template_table,
       physical.table_name AS physical_table,
       template.column_count AS template_column_count,
       physical.column_count AS physical_column_count
FROM (
  SELECT table_name, COUNT(*) AS column_count
  FROM information_schema.columns
  WHERE table_schema = @schema_name
    AND table_name IN ('transaction_order', 'transaction_operation', 'transaction_channel_request', 'transaction_event_outbox', 'transaction_status_history')
  GROUP BY table_name
) template
JOIN (
  SELECT table_name, REGEXP_REPLACE(table_name, '_20260[34]$', '') AS logical_table, COUNT(*) AS column_count
  FROM information_schema.columns
  WHERE table_schema = @schema_name
    AND table_name REGEXP '^transaction_(order|operation|channel_request|event_outbox|status_history)_20260[34]$'
  GROUP BY table_name
) physical
  ON physical.logical_table = template.table_name
ORDER BY template.table_name, physical.table_name;

-- ============================================================
-- 7. 回滚草案
-- ============================================================
-- 回滚前影响说明：
-- 1. 如果新代码已经写入 source_operation_id/merchant_operation_no/merchant_order_id，回滚 DDL 会导致新版本 Java/Mapper 写入失败。
-- 2. 回滚前必须先回滚应用版本，停止写入新字段，再备份新增字段数据。
-- 3. 不建议删除新增字段；优先回滚索引和 NOT NULL 约束，保留数据便于再次前滚。
-- 4. 回滚语句同样不是幂等脚本：DROP INDEX 仅在索引存在时执行，ADD INDEX 仅在旧索引缺失且数据满足唯一性时执行。

-- 7.1 transaction_operation 回滚到兼容旧索引。
ALTER TABLE transaction_operation
  DROP INDEX uk_merchant_operation,
  DROP INDEX idx_source_operation,
  DROP INDEX idx_operation_time,
  DROP INDEX uk_transaction_id,
  ADD UNIQUE KEY uk_operation_id (operation_id),
  MODIFY COLUMN merchant_operation_no VARCHAR(128) NULL COMMENT '商户动作单号，如 captureNo、refundNo；首笔可等于 merchant_order_no。';

ALTER TABLE transaction_operation_202603
  DROP INDEX uk_merchant_operation,
  DROP INDEX idx_source_operation,
  DROP INDEX idx_operation_time,
  DROP INDEX uk_transaction_id,
  ADD UNIQUE KEY uk_operation_id (operation_id),
  MODIFY COLUMN merchant_operation_no VARCHAR(128) NULL COMMENT '商户动作单号，如 captureNo、refundNo；首笔可等于 merchant_order_no。';

ALTER TABLE transaction_operation_202604
  DROP INDEX uk_merchant_operation,
  DROP INDEX idx_source_operation,
  DROP INDEX idx_operation_time,
  DROP INDEX uk_transaction_id,
  ADD UNIQUE KEY uk_operation_id (operation_id),
  MODIFY COLUMN merchant_operation_no VARCHAR(128) NULL COMMENT '商户动作单号，如 captureNo、refundNo；首笔可等于 merchant_order_no。';

-- 7.2 transaction_order 回滚索引和可空约束；不删除数据列。
ALTER TABLE transaction_order
  DROP INDEX uk_operation_id,
  DROP INDEX uk_root_transaction_id,
  DROP INDEX idx_latest_transaction_id,
  MODIFY COLUMN operation_id VARCHAR(64) NULL,
  MODIFY COLUMN root_transaction_id VARCHAR(64) NULL,
  MODIFY COLUMN latest_transaction_id VARCHAR(64) NULL,
  MODIFY COLUMN merchant_order_id VARCHAR(128) NULL;

ALTER TABLE transaction_order_202603
  DROP INDEX uk_operation_id,
  DROP INDEX uk_root_transaction_id,
  DROP INDEX idx_latest_transaction_id,
  MODIFY COLUMN operation_id VARCHAR(64) NULL,
  MODIFY COLUMN root_transaction_id VARCHAR(64) NULL,
  MODIFY COLUMN latest_transaction_id VARCHAR(64) NULL,
  MODIFY COLUMN merchant_order_id VARCHAR(128) NULL;

ALTER TABLE transaction_order_202604
  DROP INDEX uk_operation_id,
  DROP INDEX uk_root_transaction_id,
  DROP INDEX idx_latest_transaction_id,
  MODIFY COLUMN operation_id VARCHAR(64) NULL,
  MODIFY COLUMN root_transaction_id VARCHAR(64) NULL,
  MODIFY COLUMN latest_transaction_id VARCHAR(64) NULL,
  MODIFY COLUMN merchant_order_id VARCHAR(128) NULL;

-- 7.3 transaction_channel_request 回滚新增索引；channel_id 可空建议保留，避免异常链路写入失败。
ALTER TABLE transaction_channel_request DROP INDEX idx_channel_transaction_identity;
ALTER TABLE transaction_channel_request_202603 DROP INDEX idx_channel_transaction_identity;
ALTER TABLE transaction_channel_request_202604 DROP INDEX idx_channel_transaction_identity;

-- 7.4 transaction_idempotency 回滚新增索引；merchant_order_id 字段建议保留。
ALTER TABLE transaction_idempotency DROP INDEX idx_merchant_order_id;

-- ============================================================
-- 8. 历史季度扩展生成提示
-- ============================================================
-- 查询实际存在的历史交易物理表：
SELECT table_name
FROM information_schema.tables
WHERE table_schema = @schema_name
  AND table_name REGEXP '^transaction_(order|operation|channel_request|event_outbox|status_history)_[0-9]{6}$'
ORDER BY table_name;

-- 对每个历史季度表重复第 3、5、6 节检查与迁移。
-- 不允许只修模板表和下一季度表后跳过历史表兼容性评估。
