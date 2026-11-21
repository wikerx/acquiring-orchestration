-- 2027-Q1 未来季度交易分表结构增量修复。
-- 执行约束：必须先通过 20261108_05 前检；本脚本只增加模板表已存在的字段。
-- 三张目标表必须保持空表，禁止将本脚本用于已承载交易事实的物理表。

SET NAMES utf8mb4;

ALTER TABLE `transaction_order_202701`
    ADD COLUMN `label_currency_exponent` tinyint NOT NULL COMMENT '标签币种 ISO 4217 小数位精度。' AFTER `label_amount`,
    ADD COLUMN `label_authorized_amount` decimal(20,6) NOT NULL DEFAULT 0 COMMENT '累计授权成功金额，标签币种单位。' AFTER `rate_time`,
    ADD COLUMN `label_authorized_cancel_amount` decimal(20,6) NOT NULL DEFAULT 0 COMMENT '累计授权取消金额，标签币种单位。' AFTER `label_authorized_amount`,
    ADD COLUMN `label_captured_amount` decimal(20,6) NOT NULL DEFAULT 0 COMMENT '累计请款成功金额，标签币种单位。' AFTER `label_authorized_cancel_amount`,
    ADD COLUMN `label_refunded_amount` decimal(20,6) NOT NULL DEFAULT 0 COMMENT '累计退款成功金额，标签币种单位。' AFTER `label_captured_amount`,
    ADD COLUMN `label_chargeback_amount` decimal(20,6) NOT NULL DEFAULT 0 COMMENT '累计拒付金额，标签币种单位。' AFTER `label_refunded_amount`,
    ADD COLUMN `label_available_capture_amount` decimal(20,6) NOT NULL DEFAULT 0 COMMENT '当前可请款金额，标签币种单位。' AFTER `label_chargeback_amount`,
    ADD COLUMN `label_available_refund_amount` decimal(20,6) NOT NULL DEFAULT 0 COMMENT '当前可退款金额，标签币种单位。' AFTER `label_available_capture_amount`;

ALTER TABLE `transaction_operation_202701`
    ADD COLUMN `label_currency_exponent` tinyint NOT NULL COMMENT '标签币种 ISO 4217 小数位精度。' AFTER `label_amount`;

ALTER TABLE `transaction_amount_change_log_202701`
    ADD COLUMN `label_amount_currency` char(3) NOT NULL COMMENT '本次变动的标签币种。' AFTER `change_amount`,
    ADD COLUMN `label_change_amount` decimal(20,6) NOT NULL COMMENT '本次变动的标签金额。' AFTER `label_amount_currency`,
    ADD COLUMN `label_authorized_before` decimal(20,6) NOT NULL COMMENT '变动前累计标签授权金额。' AFTER `label_change_amount`,
    ADD COLUMN `label_authorized_after` decimal(20,6) NOT NULL COMMENT '变动后累计标签授权金额。' AFTER `label_authorized_before`,
    ADD COLUMN `label_captured_before` decimal(20,6) NOT NULL COMMENT '变动前累计标签请款金额。' AFTER `label_authorized_after`,
    ADD COLUMN `label_captured_after` decimal(20,6) NOT NULL COMMENT '变动后累计标签请款金额。' AFTER `label_captured_before`,
    ADD COLUMN `label_refunded_before` decimal(20,6) NOT NULL COMMENT '变动前累计标签退款金额。' AFTER `label_captured_after`,
    ADD COLUMN `label_refunded_after` decimal(20,6) NOT NULL COMMENT '变动后累计标签退款金额。' AFTER `label_refunded_before`,
    ADD COLUMN `label_available_capture_before` decimal(20,6) NOT NULL COMMENT '变动前标签可请款金额。' AFTER `label_refunded_after`,
    ADD COLUMN `label_available_capture_after` decimal(20,6) NOT NULL COMMENT '变动后标签可请款金额。' AFTER `label_available_capture_before`,
    ADD COLUMN `label_available_refund_before` decimal(20,6) NOT NULL COMMENT '变动前标签可退款金额。' AFTER `label_available_capture_after`,
    ADD COLUMN `label_available_refund_after` decimal(20,6) NOT NULL COMMENT '变动后标签可退款金额。' AFTER `label_available_refund_before`;

SELECT 'FUTURE_QUARTER_SCHEMA_REPAIR_MIGRATION' AS check_name, 1 AS executed;
