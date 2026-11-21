-- 交易金额双口径生命周期迁移。
-- 目标：商户标签金额用于商户查询、额度、清分、结算和账务；渠道交易金额仅用于渠道执行与平台成本。
-- 执行前停止 service-payment 以及会写入 transaction_order、transaction_operation、
-- transaction_amount_change_log 的回调、查询勾兑和补偿任务，避免 DDL/历史回填期间产生并发写入。

SET NAMES utf8mb4;

-- 本迁移包含 DDL 和历史事实修复，必须一次完整执行。若检测到部分迁移、模板表承载业务数据、
-- 标签币种缺少 ISO 精度或金额日志无法关联动作事实，则立即停止，禁止带病回填。
DROP PROCEDURE IF EXISTS `precheck_transaction_label_amount_lifecycle_20261105`;
DELIMITER $$
CREATE PROCEDURE `precheck_transaction_label_amount_lifecycle_20261105`()
BEGIN
    DECLARE existing_column_count INT DEFAULT 0;
    DECLARE required_table_count INT DEFAULT 0;
    DECLARE invalid_row_count BIGINT DEFAULT 0;

    SELECT COUNT(*) INTO required_table_count
    FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME IN (
          'transaction_order', 'transaction_order_202603', 'transaction_order_202604',
          'transaction_operation', 'transaction_operation_202603', 'transaction_operation_202604',
          'transaction_amount_change_log', 'transaction_amount_change_log_202603',
          'transaction_amount_change_log_202604'
      );
    IF required_table_count <> 9 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'label amount lifecycle migration requires all template and published shard tables';
    END IF;

    SELECT COUNT(*) INTO existing_column_count
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME IN (
          'transaction_order', 'transaction_order_202603', 'transaction_order_202604',
          'transaction_operation', 'transaction_operation_202603', 'transaction_operation_202604',
          'transaction_amount_change_log', 'transaction_amount_change_log_202603',
          'transaction_amount_change_log_202604'
      )
      AND COLUMN_NAME IN (
          'label_currency_exponent', 'label_authorized_amount', 'label_authorized_cancel_amount',
          'label_captured_amount', 'label_refunded_amount', 'label_chargeback_amount',
          'label_available_capture_amount', 'label_available_refund_amount',
          'label_amount_currency', 'label_change_amount', 'label_authorized_before',
          'label_authorized_after', 'label_captured_before', 'label_captured_after',
          'label_refunded_before', 'label_refunded_after', 'label_available_capture_before',
          'label_available_capture_after', 'label_available_refund_before',
          'label_available_refund_after'
      );
    IF existing_column_count <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'label amount lifecycle migration found existing target columns; rollback or inspect partial migration first';
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'transaction_label_amount_void_backup_20261105'
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'label amount lifecycle migration backup already exists';
    END IF;

    SELECT (SELECT COUNT(*) FROM transaction_order)
         + (SELECT COUNT(*) FROM transaction_operation)
         + (SELECT COUNT(*) FROM transaction_amount_change_log)
    INTO invalid_row_count;
    IF invalid_row_count <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'transaction template tables must remain empty before label amount lifecycle migration';
    END IF;

    SELECT COUNT(*) INTO invalid_row_count
    FROM (
        SELECT o.label_currency
        FROM transaction_order_202603 o
        LEFT JOIN base_iso_currency c ON c.alpha3_code = o.label_currency AND c.deleted = 0
        WHERE c.alpha3_code IS NULL
        UNION ALL
        SELECT o.label_currency
        FROM transaction_order_202604 o
        LEFT JOIN base_iso_currency c ON c.alpha3_code = o.label_currency AND c.deleted = 0
        WHERE c.alpha3_code IS NULL
        UNION ALL
        SELECT o.label_currency
        FROM transaction_operation_202603 o
        LEFT JOIN base_iso_currency c ON c.alpha3_code = o.label_currency AND c.deleted = 0
        WHERE c.alpha3_code IS NULL
        UNION ALL
        SELECT o.label_currency
        FROM transaction_operation_202604 o
        LEFT JOIN base_iso_currency c ON c.alpha3_code = o.label_currency AND c.deleted = 0
        WHERE c.alpha3_code IS NULL
    ) missing_currency;
    IF invalid_row_count <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'label amount lifecycle migration found currency without active ISO exponent';
    END IF;

    SELECT COUNT(*) INTO invalid_row_count
    FROM (
        SELECT l.id
        FROM transaction_amount_change_log_202603 l
        LEFT JOIN transaction_operation_202603 o
          ON o.transaction_id = l.transaction_id AND o.operation_id = l.operation_id
        WHERE o.id IS NULL
        UNION ALL
        SELECT l.id
        FROM transaction_amount_change_log_202604 l
        LEFT JOIN transaction_operation_202604 o
          ON o.transaction_id = l.transaction_id AND o.operation_id = l.operation_id
        WHERE o.id IS NULL
    ) unmatched_log;
    IF invalid_row_count <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'label amount lifecycle migration found amount log without operation fact';
    END IF;
END$$
DELIMITER ;
CALL `precheck_transaction_label_amount_lifecycle_20261105`();
DROP PROCEDURE `precheck_transaction_label_amount_lifecycle_20261105`;

ALTER TABLE `transaction_order`
    ADD COLUMN `label_currency_exponent` tinyint NULL COMMENT '标签币种 ISO 4217 小数位精度。' AFTER `label_amount`,
    ADD COLUMN `label_authorized_amount` decimal(20,6) NOT NULL DEFAULT 0 COMMENT '累计授权成功金额，标签币种单位。' AFTER `rate_time`,
    ADD COLUMN `label_authorized_cancel_amount` decimal(20,6) NOT NULL DEFAULT 0 COMMENT '累计授权取消金额，标签币种单位。' AFTER `label_authorized_amount`,
    ADD COLUMN `label_captured_amount` decimal(20,6) NOT NULL DEFAULT 0 COMMENT '累计请款成功金额，标签币种单位。' AFTER `label_authorized_cancel_amount`,
    ADD COLUMN `label_refunded_amount` decimal(20,6) NOT NULL DEFAULT 0 COMMENT '累计退款成功金额，标签币种单位。' AFTER `label_captured_amount`,
    ADD COLUMN `label_chargeback_amount` decimal(20,6) NOT NULL DEFAULT 0 COMMENT '累计拒付金额，标签币种单位。' AFTER `label_refunded_amount`,
    ADD COLUMN `label_available_capture_amount` decimal(20,6) NOT NULL DEFAULT 0 COMMENT '当前可请款金额，标签币种单位。' AFTER `label_chargeback_amount`,
    ADD COLUMN `label_available_refund_amount` decimal(20,6) NOT NULL DEFAULT 0 COMMENT '当前可退款金额，标签币种单位。' AFTER `label_available_capture_amount`;

ALTER TABLE `transaction_order_202603`
    ADD COLUMN `label_currency_exponent` tinyint NULL COMMENT '标签币种 ISO 4217 小数位精度。' AFTER `label_amount`,
    ADD COLUMN `label_authorized_amount` decimal(20,6) NOT NULL DEFAULT 0 COMMENT '累计授权成功金额，标签币种单位。' AFTER `rate_time`,
    ADD COLUMN `label_authorized_cancel_amount` decimal(20,6) NOT NULL DEFAULT 0 COMMENT '累计授权取消金额，标签币种单位。' AFTER `label_authorized_amount`,
    ADD COLUMN `label_captured_amount` decimal(20,6) NOT NULL DEFAULT 0 COMMENT '累计请款成功金额，标签币种单位。' AFTER `label_authorized_cancel_amount`,
    ADD COLUMN `label_refunded_amount` decimal(20,6) NOT NULL DEFAULT 0 COMMENT '累计退款成功金额，标签币种单位。' AFTER `label_captured_amount`,
    ADD COLUMN `label_chargeback_amount` decimal(20,6) NOT NULL DEFAULT 0 COMMENT '累计拒付金额，标签币种单位。' AFTER `label_refunded_amount`,
    ADD COLUMN `label_available_capture_amount` decimal(20,6) NOT NULL DEFAULT 0 COMMENT '当前可请款金额，标签币种单位。' AFTER `label_chargeback_amount`,
    ADD COLUMN `label_available_refund_amount` decimal(20,6) NOT NULL DEFAULT 0 COMMENT '当前可退款金额，标签币种单位。' AFTER `label_available_capture_amount`;

ALTER TABLE `transaction_order_202604`
    ADD COLUMN `label_currency_exponent` tinyint NULL COMMENT '标签币种 ISO 4217 小数位精度。' AFTER `label_amount`,
    ADD COLUMN `label_authorized_amount` decimal(20,6) NOT NULL DEFAULT 0 COMMENT '累计授权成功金额，标签币种单位。' AFTER `rate_time`,
    ADD COLUMN `label_authorized_cancel_amount` decimal(20,6) NOT NULL DEFAULT 0 COMMENT '累计授权取消金额，标签币种单位。' AFTER `label_authorized_amount`,
    ADD COLUMN `label_captured_amount` decimal(20,6) NOT NULL DEFAULT 0 COMMENT '累计请款成功金额，标签币种单位。' AFTER `label_authorized_cancel_amount`,
    ADD COLUMN `label_refunded_amount` decimal(20,6) NOT NULL DEFAULT 0 COMMENT '累计退款成功金额，标签币种单位。' AFTER `label_captured_amount`,
    ADD COLUMN `label_chargeback_amount` decimal(20,6) NOT NULL DEFAULT 0 COMMENT '累计拒付金额，标签币种单位。' AFTER `label_refunded_amount`,
    ADD COLUMN `label_available_capture_amount` decimal(20,6) NOT NULL DEFAULT 0 COMMENT '当前可请款金额，标签币种单位。' AFTER `label_chargeback_amount`,
    ADD COLUMN `label_available_refund_amount` decimal(20,6) NOT NULL DEFAULT 0 COMMENT '当前可退款金额，标签币种单位。' AFTER `label_available_capture_amount`;

ALTER TABLE `transaction_operation`
    ADD COLUMN `label_currency_exponent` tinyint NULL COMMENT '标签币种 ISO 4217 小数位精度。' AFTER `label_amount`;
ALTER TABLE `transaction_operation_202603`
    ADD COLUMN `label_currency_exponent` tinyint NULL COMMENT '标签币种 ISO 4217 小数位精度。' AFTER `label_amount`;
ALTER TABLE `transaction_operation_202604`
    ADD COLUMN `label_currency_exponent` tinyint NULL COMMENT '标签币种 ISO 4217 小数位精度。' AFTER `label_amount`;

ALTER TABLE `transaction_amount_change_log`
    ADD COLUMN `label_amount_currency` char(3) NULL COMMENT '本次变动的标签币种。' AFTER `change_amount`,
    ADD COLUMN `label_change_amount` decimal(20,6) NULL COMMENT '本次变动的标签金额。' AFTER `label_amount_currency`,
    ADD COLUMN `label_authorized_before` decimal(20,6) NULL COMMENT '变动前累计标签授权金额。' AFTER `label_change_amount`,
    ADD COLUMN `label_authorized_after` decimal(20,6) NULL COMMENT '变动后累计标签授权金额。' AFTER `label_authorized_before`,
    ADD COLUMN `label_captured_before` decimal(20,6) NULL COMMENT '变动前累计标签请款金额。' AFTER `label_authorized_after`,
    ADD COLUMN `label_captured_after` decimal(20,6) NULL COMMENT '变动后累计标签请款金额。' AFTER `label_captured_before`,
    ADD COLUMN `label_refunded_before` decimal(20,6) NULL COMMENT '变动前累计标签退款金额。' AFTER `label_captured_after`,
    ADD COLUMN `label_refunded_after` decimal(20,6) NULL COMMENT '变动后累计标签退款金额。' AFTER `label_refunded_before`,
    ADD COLUMN `label_available_capture_before` decimal(20,6) NULL COMMENT '变动前标签可请款金额。' AFTER `label_refunded_after`,
    ADD COLUMN `label_available_capture_after` decimal(20,6) NULL COMMENT '变动后标签可请款金额。' AFTER `label_available_capture_before`,
    ADD COLUMN `label_available_refund_before` decimal(20,6) NULL COMMENT '变动前标签可退款金额。' AFTER `label_available_capture_after`,
    ADD COLUMN `label_available_refund_after` decimal(20,6) NULL COMMENT '变动后标签可退款金额。' AFTER `label_available_refund_before`;

ALTER TABLE `transaction_amount_change_log_202603`
    ADD COLUMN `label_amount_currency` char(3) NULL COMMENT '本次变动的标签币种。' AFTER `change_amount`,
    ADD COLUMN `label_change_amount` decimal(20,6) NULL COMMENT '本次变动的标签金额。' AFTER `label_amount_currency`,
    ADD COLUMN `label_authorized_before` decimal(20,6) NULL COMMENT '变动前累计标签授权金额。' AFTER `label_change_amount`,
    ADD COLUMN `label_authorized_after` decimal(20,6) NULL COMMENT '变动后累计标签授权金额。' AFTER `label_authorized_before`,
    ADD COLUMN `label_captured_before` decimal(20,6) NULL COMMENT '变动前累计标签请款金额。' AFTER `label_authorized_after`,
    ADD COLUMN `label_captured_after` decimal(20,6) NULL COMMENT '变动后累计标签请款金额。' AFTER `label_captured_before`,
    ADD COLUMN `label_refunded_before` decimal(20,6) NULL COMMENT '变动前累计标签退款金额。' AFTER `label_captured_after`,
    ADD COLUMN `label_refunded_after` decimal(20,6) NULL COMMENT '变动后累计标签退款金额。' AFTER `label_refunded_before`,
    ADD COLUMN `label_available_capture_before` decimal(20,6) NULL COMMENT '变动前标签可请款金额。' AFTER `label_refunded_after`,
    ADD COLUMN `label_available_capture_after` decimal(20,6) NULL COMMENT '变动后标签可请款金额。' AFTER `label_available_capture_before`,
    ADD COLUMN `label_available_refund_before` decimal(20,6) NULL COMMENT '变动前标签可退款金额。' AFTER `label_available_capture_after`,
    ADD COLUMN `label_available_refund_after` decimal(20,6) NULL COMMENT '变动后标签可退款金额。' AFTER `label_available_refund_before`;

ALTER TABLE `transaction_amount_change_log_202604`
    ADD COLUMN `label_amount_currency` char(3) NULL COMMENT '本次变动的标签币种。' AFTER `change_amount`,
    ADD COLUMN `label_change_amount` decimal(20,6) NULL COMMENT '本次变动的标签金额。' AFTER `label_amount_currency`,
    ADD COLUMN `label_authorized_before` decimal(20,6) NULL COMMENT '变动前累计标签授权金额。' AFTER `label_change_amount`,
    ADD COLUMN `label_authorized_after` decimal(20,6) NULL COMMENT '变动后累计标签授权金额。' AFTER `label_authorized_before`,
    ADD COLUMN `label_captured_before` decimal(20,6) NULL COMMENT '变动前累计标签请款金额。' AFTER `label_authorized_after`,
    ADD COLUMN `label_captured_after` decimal(20,6) NULL COMMENT '变动后累计标签请款金额。' AFTER `label_captured_before`,
    ADD COLUMN `label_refunded_before` decimal(20,6) NULL COMMENT '变动前累计标签退款金额。' AFTER `label_captured_after`,
    ADD COLUMN `label_refunded_after` decimal(20,6) NULL COMMENT '变动后累计标签退款金额。' AFTER `label_refunded_before`,
    ADD COLUMN `label_available_capture_before` decimal(20,6) NULL COMMENT '变动前标签可请款金额。' AFTER `label_refunded_after`,
    ADD COLUMN `label_available_capture_after` decimal(20,6) NULL COMMENT '变动后标签可请款金额。' AFTER `label_available_capture_before`,
    ADD COLUMN `label_available_refund_before` decimal(20,6) NULL COMMENT '变动前标签可退款金额。' AFTER `label_available_capture_after`,
    ADD COLUMN `label_available_refund_after` decimal(20,6) NULL COMMENT '变动后标签可退款金额。' AFTER `label_available_refund_before`;

-- 仅备份本迁移会改写的历史 VOID 既有字段。新增标签生命周期列由回滚脚本直接删除。
CREATE TABLE `transaction_label_amount_void_backup_20261105` (
    `shard_code` char(6) NOT NULL COMMENT '原交易分片：202603、202604。',
    `operation_row_id` bigint NOT NULL COMMENT 'transaction_operation 分片表物理主键。',
    `label_currency` char(3) NOT NULL COMMENT '迁移前标签币种。',
    `label_amount` decimal(20,6) NOT NULL COMMENT '迁移前标签金额。',
    `transaction_rate` decimal(24,12) NULL COMMENT '迁移前交易汇率。',
    `dcc_enabled` tinyint NOT NULL COMMENT '迁移前 DCC 标识。',
    `edc_enabled` tinyint NOT NULL COMMENT '迁移前 EDC 标识。',
    `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '备份时间。',
    PRIMARY KEY (`shard_code`, `operation_row_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='20261105 标签金额双口径迁移的历史 VOID 回滚备份。';

INSERT INTO `transaction_label_amount_void_backup_20261105`
    (`shard_code`, `operation_row_id`, `label_currency`, `label_amount`, `transaction_rate`,
     `dcc_enabled`, `edc_enabled`)
SELECT '202603', o.id, o.label_currency, o.label_amount, o.transaction_rate, o.dcc_enabled, o.edc_enabled
FROM `transaction_operation_202603` o
WHERE o.transaction_type = 'VOID'
  AND o.label_currency <> o.transaction_currency
UNION ALL
SELECT '202604', o.id, o.label_currency, o.label_amount, o.transaction_rate, o.dcc_enabled, o.edc_enabled
FROM `transaction_operation_202604` o
WHERE o.transaction_type = 'VOID'
  AND o.label_currency <> o.transaction_currency;

UPDATE `transaction_order` o
JOIN `base_iso_currency` c ON c.alpha3_code = o.label_currency AND c.deleted = 0
SET o.label_currency_exponent = c.fraction_digits;
UPDATE `transaction_order_202603` o
JOIN `base_iso_currency` c ON c.alpha3_code = o.label_currency AND c.deleted = 0
SET o.label_currency_exponent = c.fraction_digits;
UPDATE `transaction_order_202604` o
JOIN `base_iso_currency` c ON c.alpha3_code = o.label_currency AND c.deleted = 0
SET o.label_currency_exponent = c.fraction_digits;

UPDATE `transaction_operation` o
JOIN `base_iso_currency` c ON c.alpha3_code = o.label_currency AND c.deleted = 0
SET o.label_currency_exponent = c.fraction_digits;
UPDATE `transaction_operation_202603` o
JOIN `base_iso_currency` c ON c.alpha3_code = o.label_currency AND c.deleted = 0
SET o.label_currency_exponent = c.fraction_digits;
UPDATE `transaction_operation_202604` o
JOIN `base_iso_currency` c ON c.alpha3_code = o.label_currency AND c.deleted = 0
SET o.label_currency_exponent = c.fraction_digits;

-- 从成功动作事实重建 202603 生命周期标签累计金额。
DROP TEMPORARY TABLE IF EXISTS `tmp_label_lifecycle_202603`;
CREATE TEMPORARY TABLE `tmp_label_lifecycle_202603`  COMMENT '第三季度交易标签金额生命周期修复临时汇总' AS
SELECT operation_id,
       SUM(CASE WHEN transaction_status = 'SUCCESS' AND transaction_type IN
                    ('AUTHORIZATION', 'PRE_AUTHORIZATION', 'PAYMENT', 'INCREMENTAL_AUTHORIZATION')
                THEN label_amount ELSE 0 END) AS label_authorized_amount,
       SUM(CASE WHEN transaction_status = 'SUCCESS' AND transaction_type IN
                    ('PAYMENT', 'CAPTURE', 'PRE_AUTH_COMPLETION')
                THEN label_amount ELSE 0 END) AS label_captured_amount,
       SUM(CASE WHEN transaction_status = 'SUCCESS' AND transaction_type = 'REFUND'
                THEN label_amount ELSE 0 END) AS label_refunded_amount,
       SUM(CASE WHEN transaction_status = 'SUCCESS' AND transaction_type = 'CHARGEBACK'
                THEN label_amount ELSE 0 END) AS label_chargeback_amount,
       MAX(CASE WHEN transaction_status = 'SUCCESS' AND transaction_type = 'VOID' THEN 1 ELSE 0 END)
           AS successful_void
FROM `transaction_operation_202603`
WHERE deleted = 0
GROUP BY operation_id;

UPDATE `transaction_order_202603` o
JOIN `tmp_label_lifecycle_202603` a ON a.operation_id = o.operation_id
SET o.label_authorized_amount = a.label_authorized_amount,
    o.label_authorized_cancel_amount = CASE WHEN a.successful_void = 1
        THEN GREATEST(a.label_authorized_amount - a.label_captured_amount, 0) ELSE 0 END,
    o.label_captured_amount = a.label_captured_amount,
    o.label_refunded_amount = a.label_refunded_amount,
    o.label_chargeback_amount = a.label_chargeback_amount,
    o.label_available_capture_amount = CASE WHEN a.successful_void = 1 THEN 0
        ELSE GREATEST(a.label_authorized_amount - a.label_captured_amount, 0) END,
    o.label_available_refund_amount = GREATEST(
        a.label_captured_amount - a.label_refunded_amount - a.label_chargeback_amount, 0);

-- 从成功动作事实重建 202604 生命周期标签累计金额。
DROP TEMPORARY TABLE IF EXISTS `tmp_label_lifecycle_202604`;
CREATE TEMPORARY TABLE `tmp_label_lifecycle_202604`  COMMENT '第四季度交易标签金额生命周期修复临时汇总' AS
SELECT operation_id,
       SUM(CASE WHEN transaction_status = 'SUCCESS' AND transaction_type IN
                    ('AUTHORIZATION', 'PRE_AUTHORIZATION', 'PAYMENT', 'INCREMENTAL_AUTHORIZATION')
                THEN label_amount ELSE 0 END) AS label_authorized_amount,
       SUM(CASE WHEN transaction_status = 'SUCCESS' AND transaction_type IN
                    ('PAYMENT', 'CAPTURE', 'PRE_AUTH_COMPLETION')
                THEN label_amount ELSE 0 END) AS label_captured_amount,
       SUM(CASE WHEN transaction_status = 'SUCCESS' AND transaction_type = 'REFUND'
                THEN label_amount ELSE 0 END) AS label_refunded_amount,
       SUM(CASE WHEN transaction_status = 'SUCCESS' AND transaction_type = 'CHARGEBACK'
                THEN label_amount ELSE 0 END) AS label_chargeback_amount,
       MAX(CASE WHEN transaction_status = 'SUCCESS' AND transaction_type = 'VOID' THEN 1 ELSE 0 END)
           AS successful_void
FROM `transaction_operation_202604`
WHERE deleted = 0
GROUP BY operation_id;

UPDATE `transaction_order_202604` o
JOIN `tmp_label_lifecycle_202604` a ON a.operation_id = o.operation_id
SET o.label_authorized_amount = a.label_authorized_amount,
    o.label_authorized_cancel_amount = CASE WHEN a.successful_void = 1
        THEN GREATEST(a.label_authorized_amount - a.label_captured_amount, 0) ELSE 0 END,
    o.label_captured_amount = a.label_captured_amount,
    o.label_refunded_amount = a.label_refunded_amount,
    o.label_chargeback_amount = a.label_chargeback_amount,
    o.label_available_capture_amount = CASE WHEN a.successful_void = 1 THEN 0
        ELSE GREATEST(a.label_authorized_amount - a.label_captured_amount, 0) END,
    o.label_available_refund_amount = GREATEST(
        a.label_captured_amount - a.label_refunded_amount - a.label_chargeback_amount, 0);

-- 以动作顺序重建每笔历史金额变动日志的标签口径前后值，并修复旧跨币种撤销动作。
DROP TEMPORARY TABLE IF EXISTS `tmp_label_operation_state_202603`;
CREATE TEMPORARY TABLE `tmp_label_operation_state_202603`  COMMENT '第三季度交易标签金额动作状态修复临时汇总' AS
SELECT s.*,
       CASE WHEN s.void_before > 0 THEN 0 ELSE GREATEST(s.authorized_before - s.captured_before, 0) END
           AS available_capture_before,
       CASE WHEN s.void_after > 0 THEN 0 ELSE GREATEST(s.authorized_after - s.captured_after, 0) END
           AS available_capture_after,
       GREATEST(s.captured_before - s.refunded_before - s.chargeback_before, 0) AS available_refund_before,
       GREATEST(s.captured_after - s.refunded_after - s.chargeback_after, 0) AS available_refund_after
FROM (
    SELECT o.id, o.transaction_id, o.operation_id, o.transaction_type, o.transaction_status,
           o.label_currency, o.label_amount, o.transaction_currency, o.operation_sequence,
           COALESCE(SUM(CASE WHEN transaction_status = 'SUCCESS' AND transaction_type IN
               ('AUTHORIZATION', 'PRE_AUTHORIZATION', 'PAYMENT', 'INCREMENTAL_AUTHORIZATION')
               THEN label_amount ELSE 0 END) OVER w_before, 0) AS authorized_before,
           COALESCE(SUM(CASE WHEN transaction_status = 'SUCCESS' AND transaction_type IN
               ('AUTHORIZATION', 'PRE_AUTHORIZATION', 'PAYMENT', 'INCREMENTAL_AUTHORIZATION')
               THEN label_amount ELSE 0 END) OVER w_after, 0) AS authorized_after,
           COALESCE(SUM(CASE WHEN transaction_status = 'SUCCESS' AND transaction_type IN
               ('PAYMENT', 'CAPTURE', 'PRE_AUTH_COMPLETION') THEN label_amount ELSE 0 END)
               OVER w_before, 0) AS captured_before,
           COALESCE(SUM(CASE WHEN transaction_status = 'SUCCESS' AND transaction_type IN
               ('PAYMENT', 'CAPTURE', 'PRE_AUTH_COMPLETION') THEN label_amount ELSE 0 END)
               OVER w_after, 0) AS captured_after,
           COALESCE(SUM(CASE WHEN transaction_status = 'SUCCESS' AND transaction_type = 'REFUND'
               THEN label_amount ELSE 0 END) OVER w_before, 0) AS refunded_before,
           COALESCE(SUM(CASE WHEN transaction_status = 'SUCCESS' AND transaction_type = 'REFUND'
               THEN label_amount ELSE 0 END) OVER w_after, 0) AS refunded_after,
           COALESCE(SUM(CASE WHEN transaction_status = 'SUCCESS' AND transaction_type = 'CHARGEBACK'
               THEN label_amount ELSE 0 END) OVER w_before, 0) AS chargeback_before,
           COALESCE(SUM(CASE WHEN transaction_status = 'SUCCESS' AND transaction_type = 'CHARGEBACK'
               THEN label_amount ELSE 0 END) OVER w_after, 0) AS chargeback_after,
           COALESCE(MAX(CASE WHEN transaction_status = 'SUCCESS' AND transaction_type = 'VOID'
               THEN 1 ELSE 0 END) OVER w_before, 0) AS void_before,
           COALESCE(MAX(CASE WHEN transaction_status = 'SUCCESS' AND transaction_type = 'VOID'
               THEN 1 ELSE 0 END) OVER w_after, 0) AS void_after
    FROM `transaction_operation_202603` o
    WHERE deleted = 0
    WINDOW w_before AS (PARTITION BY operation_id ORDER BY operation_sequence
        ROWS BETWEEN UNBOUNDED PRECEDING AND 1 PRECEDING),
           w_after AS (PARTITION BY operation_id ORDER BY operation_sequence
        ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW)
) s;

UPDATE `transaction_operation_202603` o
JOIN `tmp_label_operation_state_202603` s ON s.id = o.id
JOIN `transaction_order_202603` r ON r.operation_id = o.operation_id
SET o.label_currency = r.label_currency,
    o.label_amount = s.available_capture_before,
    o.label_currency_exponent = r.label_currency_exponent,
    o.transaction_rate = r.transaction_rate,
    o.dcc_enabled = r.dcc_enabled,
    o.edc_enabled = r.edc_enabled
WHERE o.transaction_type = 'VOID'
  AND o.label_currency <> o.transaction_currency;

UPDATE `transaction_amount_change_log_202603` l
JOIN `tmp_label_operation_state_202603` s
  ON s.transaction_id = l.transaction_id AND s.operation_id = l.operation_id
SET l.label_amount_currency = s.label_currency,
    l.label_change_amount = CASE WHEN s.transaction_type = 'VOID'
        THEN s.available_capture_before ELSE s.label_amount END,
    l.label_authorized_before = s.authorized_before,
    l.label_authorized_after = s.authorized_after,
    l.label_captured_before = s.captured_before,
    l.label_captured_after = s.captured_after,
    l.label_refunded_before = s.refunded_before,
    l.label_refunded_after = s.refunded_after,
    l.label_available_capture_before = s.available_capture_before,
    l.label_available_capture_after = s.available_capture_after,
    l.label_available_refund_before = s.available_refund_before,
    l.label_available_refund_after = s.available_refund_after;

DROP TEMPORARY TABLE IF EXISTS `tmp_label_operation_state_202604`;
CREATE TEMPORARY TABLE `tmp_label_operation_state_202604`  COMMENT '第四季度交易标签金额动作状态修复临时汇总' AS
SELECT s.*,
       CASE WHEN s.void_before > 0 THEN 0 ELSE GREATEST(s.authorized_before - s.captured_before, 0) END
           AS available_capture_before,
       CASE WHEN s.void_after > 0 THEN 0 ELSE GREATEST(s.authorized_after - s.captured_after, 0) END
           AS available_capture_after,
       GREATEST(s.captured_before - s.refunded_before - s.chargeback_before, 0) AS available_refund_before,
       GREATEST(s.captured_after - s.refunded_after - s.chargeback_after, 0) AS available_refund_after
FROM (
    SELECT o.id, o.transaction_id, o.operation_id, o.transaction_type, o.transaction_status,
           o.label_currency, o.label_amount, o.transaction_currency, o.operation_sequence,
           COALESCE(SUM(CASE WHEN transaction_status = 'SUCCESS' AND transaction_type IN
               ('AUTHORIZATION', 'PRE_AUTHORIZATION', 'PAYMENT', 'INCREMENTAL_AUTHORIZATION')
               THEN label_amount ELSE 0 END) OVER w_before, 0) AS authorized_before,
           COALESCE(SUM(CASE WHEN transaction_status = 'SUCCESS' AND transaction_type IN
               ('AUTHORIZATION', 'PRE_AUTHORIZATION', 'PAYMENT', 'INCREMENTAL_AUTHORIZATION')
               THEN label_amount ELSE 0 END) OVER w_after, 0) AS authorized_after,
           COALESCE(SUM(CASE WHEN transaction_status = 'SUCCESS' AND transaction_type IN
               ('PAYMENT', 'CAPTURE', 'PRE_AUTH_COMPLETION') THEN label_amount ELSE 0 END)
               OVER w_before, 0) AS captured_before,
           COALESCE(SUM(CASE WHEN transaction_status = 'SUCCESS' AND transaction_type IN
               ('PAYMENT', 'CAPTURE', 'PRE_AUTH_COMPLETION') THEN label_amount ELSE 0 END)
               OVER w_after, 0) AS captured_after,
           COALESCE(SUM(CASE WHEN transaction_status = 'SUCCESS' AND transaction_type = 'REFUND'
               THEN label_amount ELSE 0 END) OVER w_before, 0) AS refunded_before,
           COALESCE(SUM(CASE WHEN transaction_status = 'SUCCESS' AND transaction_type = 'REFUND'
               THEN label_amount ELSE 0 END) OVER w_after, 0) AS refunded_after,
           COALESCE(SUM(CASE WHEN transaction_status = 'SUCCESS' AND transaction_type = 'CHARGEBACK'
               THEN label_amount ELSE 0 END) OVER w_before, 0) AS chargeback_before,
           COALESCE(SUM(CASE WHEN transaction_status = 'SUCCESS' AND transaction_type = 'CHARGEBACK'
               THEN label_amount ELSE 0 END) OVER w_after, 0) AS chargeback_after,
           COALESCE(MAX(CASE WHEN transaction_status = 'SUCCESS' AND transaction_type = 'VOID'
               THEN 1 ELSE 0 END) OVER w_before, 0) AS void_before,
           COALESCE(MAX(CASE WHEN transaction_status = 'SUCCESS' AND transaction_type = 'VOID'
               THEN 1 ELSE 0 END) OVER w_after, 0) AS void_after
    FROM `transaction_operation_202604` o
    WHERE deleted = 0
    WINDOW w_before AS (PARTITION BY operation_id ORDER BY operation_sequence
        ROWS BETWEEN UNBOUNDED PRECEDING AND 1 PRECEDING),
           w_after AS (PARTITION BY operation_id ORDER BY operation_sequence
        ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW)
) s;

UPDATE `transaction_operation_202604` o
JOIN `tmp_label_operation_state_202604` s ON s.id = o.id
JOIN `transaction_order_202604` r ON r.operation_id = o.operation_id
SET o.label_currency = r.label_currency,
    o.label_amount = s.available_capture_before,
    o.label_currency_exponent = r.label_currency_exponent,
    o.transaction_rate = r.transaction_rate,
    o.dcc_enabled = r.dcc_enabled,
    o.edc_enabled = r.edc_enabled
WHERE o.transaction_type = 'VOID'
  AND o.label_currency <> o.transaction_currency;

UPDATE `transaction_amount_change_log_202604` l
JOIN `tmp_label_operation_state_202604` s
  ON s.transaction_id = l.transaction_id AND s.operation_id = l.operation_id
SET l.label_amount_currency = s.label_currency,
    l.label_change_amount = CASE WHEN s.transaction_type = 'VOID'
        THEN s.available_capture_before ELSE s.label_amount END,
    l.label_authorized_before = s.authorized_before,
    l.label_authorized_after = s.authorized_after,
    l.label_captured_before = s.captured_before,
    l.label_captured_after = s.captured_after,
    l.label_refunded_before = s.refunded_before,
    l.label_refunded_after = s.refunded_after,
    l.label_available_capture_before = s.available_capture_before,
    l.label_available_capture_after = s.available_capture_after,
    l.label_available_refund_before = s.available_refund_before,
    l.label_available_refund_after = s.available_refund_after;

ALTER TABLE `transaction_order` MODIFY COLUMN `label_currency_exponent` tinyint NOT NULL COMMENT '标签币种 ISO 4217 小数位精度。';
ALTER TABLE `transaction_order_202603` MODIFY COLUMN `label_currency_exponent` tinyint NOT NULL COMMENT '标签币种 ISO 4217 小数位精度。';
ALTER TABLE `transaction_order_202604` MODIFY COLUMN `label_currency_exponent` tinyint NOT NULL COMMENT '标签币种 ISO 4217 小数位精度。';
ALTER TABLE `transaction_operation` MODIFY COLUMN `label_currency_exponent` tinyint NOT NULL COMMENT '标签币种 ISO 4217 小数位精度。';
ALTER TABLE `transaction_operation_202603` MODIFY COLUMN `label_currency_exponent` tinyint NOT NULL COMMENT '标签币种 ISO 4217 小数位精度。';
ALTER TABLE `transaction_operation_202604` MODIFY COLUMN `label_currency_exponent` tinyint NOT NULL COMMENT '标签币种 ISO 4217 小数位精度。';

ALTER TABLE `transaction_amount_change_log`
    MODIFY COLUMN `label_amount_currency` char(3) NOT NULL COMMENT '本次变动的标签币种。',
    MODIFY COLUMN `label_change_amount` decimal(20,6) NOT NULL COMMENT '本次变动的标签金额。',
    MODIFY COLUMN `label_authorized_before` decimal(20,6) NOT NULL COMMENT '变动前累计标签授权金额。',
    MODIFY COLUMN `label_authorized_after` decimal(20,6) NOT NULL COMMENT '变动后累计标签授权金额。',
    MODIFY COLUMN `label_captured_before` decimal(20,6) NOT NULL COMMENT '变动前累计标签请款金额。',
    MODIFY COLUMN `label_captured_after` decimal(20,6) NOT NULL COMMENT '变动后累计标签请款金额。',
    MODIFY COLUMN `label_refunded_before` decimal(20,6) NOT NULL COMMENT '变动前累计标签退款金额。',
    MODIFY COLUMN `label_refunded_after` decimal(20,6) NOT NULL COMMENT '变动后累计标签退款金额。',
    MODIFY COLUMN `label_available_capture_before` decimal(20,6) NOT NULL COMMENT '变动前标签可请款金额。',
    MODIFY COLUMN `label_available_capture_after` decimal(20,6) NOT NULL COMMENT '变动后标签可请款金额。',
    MODIFY COLUMN `label_available_refund_before` decimal(20,6) NOT NULL COMMENT '变动前标签可退款金额。',
    MODIFY COLUMN `label_available_refund_after` decimal(20,6) NOT NULL COMMENT '变动后标签可退款金额。';

ALTER TABLE `transaction_amount_change_log_202603`
    MODIFY COLUMN `label_amount_currency` char(3) NOT NULL COMMENT '本次变动的标签币种。',
    MODIFY COLUMN `label_change_amount` decimal(20,6) NOT NULL COMMENT '本次变动的标签金额。',
    MODIFY COLUMN `label_authorized_before` decimal(20,6) NOT NULL COMMENT '变动前累计标签授权金额。',
    MODIFY COLUMN `label_authorized_after` decimal(20,6) NOT NULL COMMENT '变动后累计标签授权金额。',
    MODIFY COLUMN `label_captured_before` decimal(20,6) NOT NULL COMMENT '变动前累计标签请款金额。',
    MODIFY COLUMN `label_captured_after` decimal(20,6) NOT NULL COMMENT '变动后累计标签请款金额。',
    MODIFY COLUMN `label_refunded_before` decimal(20,6) NOT NULL COMMENT '变动前累计标签退款金额。',
    MODIFY COLUMN `label_refunded_after` decimal(20,6) NOT NULL COMMENT '变动后累计标签退款金额。',
    MODIFY COLUMN `label_available_capture_before` decimal(20,6) NOT NULL COMMENT '变动前标签可请款金额。',
    MODIFY COLUMN `label_available_capture_after` decimal(20,6) NOT NULL COMMENT '变动后标签可请款金额。',
    MODIFY COLUMN `label_available_refund_before` decimal(20,6) NOT NULL COMMENT '变动前标签可退款金额。',
    MODIFY COLUMN `label_available_refund_after` decimal(20,6) NOT NULL COMMENT '变动后标签可退款金额。';

ALTER TABLE `transaction_amount_change_log_202604`
    MODIFY COLUMN `label_amount_currency` char(3) NOT NULL COMMENT '本次变动的标签币种。',
    MODIFY COLUMN `label_change_amount` decimal(20,6) NOT NULL COMMENT '本次变动的标签金额。',
    MODIFY COLUMN `label_authorized_before` decimal(20,6) NOT NULL COMMENT '变动前累计标签授权金额。',
    MODIFY COLUMN `label_authorized_after` decimal(20,6) NOT NULL COMMENT '变动后累计标签授权金额。',
    MODIFY COLUMN `label_captured_before` decimal(20,6) NOT NULL COMMENT '变动前累计标签请款金额。',
    MODIFY COLUMN `label_captured_after` decimal(20,6) NOT NULL COMMENT '变动后累计标签请款金额。',
    MODIFY COLUMN `label_refunded_before` decimal(20,6) NOT NULL COMMENT '变动前累计标签退款金额。',
    MODIFY COLUMN `label_refunded_after` decimal(20,6) NOT NULL COMMENT '变动后累计标签退款金额。',
    MODIFY COLUMN `label_available_capture_before` decimal(20,6) NOT NULL COMMENT '变动前标签可请款金额。',
    MODIFY COLUMN `label_available_capture_after` decimal(20,6) NOT NULL COMMENT '变动后标签可请款金额。',
    MODIFY COLUMN `label_available_refund_before` decimal(20,6) NOT NULL COMMENT '变动前标签可退款金额。',
    MODIFY COLUMN `label_available_refund_after` decimal(20,6) NOT NULL COMMENT '变动后标签可退款金额。';

DROP TEMPORARY TABLE IF EXISTS `tmp_label_lifecycle_202603`;
DROP TEMPORARY TABLE IF EXISTS `tmp_label_lifecycle_202604`;
DROP TEMPORARY TABLE IF EXISTS `tmp_label_operation_state_202603`;
DROP TEMPORARY TABLE IF EXISTS `tmp_label_operation_state_202604`;
