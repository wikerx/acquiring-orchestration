-- 商户风控功能开通状态与审计原因码迁移草案（MySQL 8.4，可重复执行）。
-- 仅供评审，未由 Codex 执行。执行前必须完成备份、DDL 窗口、存量数据和回滚方案评审。
-- 本脚本不写入任何商户配置、规则、Mock 或测试数据；无配置即未开通，运行时默认跳过可选风控功能。

SET NAMES utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE `payment_acquiring`;

CREATE TABLE IF NOT EXISTS `risk_merchant_function_config` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '物理主键。',
  `merchant_id` varchar(32) NOT NULL COMMENT '商户号。',
  `module_type` varchar(32) NOT NULL COMMENT '风控模块类型：WHITE、BLACK、RULE。',
  `function_code` varchar(64) NOT NULL COMMENT '稳定风控功能编码。',
  `enabled` tinyint(1) NOT NULL DEFAULT 0 COMMENT '商户是否开通该可选功能：0 否，1 是。',
  `deleted` tinyint(1) NOT NULL DEFAULT 0 COMMENT '软删除标记：0 正常，1 删除。',
  `created_by` varchar(64) DEFAULT NULL COMMENT '创建人。',
  `updated_by` varchar(64) DEFAULT NULL COMMENT '最后修改人。',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间。',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
      ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_risk_merchant_function` (`merchant_id`, `module_type`, `function_code`, `deleted`),
  KEY `idx_risk_merchant_function_enabled`
      (`merchant_id`, `enabled`, `deleted`, `module_type`, `function_code`),
  CONSTRAINT `chk_risk_merchant_function_enabled` CHECK (`enabled` IN (0, 1)),
  CONSTRAINT `chk_risk_merchant_function_deleted` CHECK (`deleted` IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='商户可选风控功能开通状态；平台全局 AML 和监管规则不受该表控制。';

DROP PROCEDURE IF EXISTS `migrate_20261017_02_risk_runtime_schema`;
DELIMITER $$
CREATE PROCEDURE `migrate_20261017_02_risk_runtime_schema`()
BEGIN
  IF NOT EXISTS (
      SELECT 1
      FROM `information_schema`.`columns`
      WHERE `table_schema` = DATABASE()
        AND `table_name` = 'risk_evaluation_record'
        AND `column_name` = 'decision_reason_code'
  ) THEN
    ALTER TABLE `risk_evaluation_record`
      ADD COLUMN `decision_reason_code` varchar(64) DEFAULT NULL
        COMMENT '最终决策稳定原因码。' AFTER `decision_result`;
  END IF;

  IF NOT EXISTS (
      SELECT 1
      FROM `information_schema`.`columns`
      WHERE `table_schema` = DATABASE()
        AND `table_name` = 'risk_evaluation_hit_detail'
        AND `column_name` = 'reason_code'
  ) THEN
    ALTER TABLE `risk_evaluation_hit_detail`
      ADD COLUMN `reason_code` varchar(64) DEFAULT NULL
        COMMENT '明细稳定原因码：NOT_ENABLED、NOT_CONFIGURED、NO_MATCH 等。' AFTER `decision_result`;
  END IF;

  -- 纠正历史上因连接字符集错误写入的乱码注释；字段定义、索引和业务数据保持不变。
  IF EXISTS (
      SELECT 1
      FROM `information_schema`.`tables`
      WHERE `table_schema` = DATABASE()
        AND `table_name` = 'risk_merchant_function_config'
        AND `table_comment` <> '商户可选风控功能开通状态；平台全局 AML 和监管规则不受该表控制。'
  ) OR EXISTS (
      SELECT 1
      FROM `information_schema`.`columns`
      WHERE `table_schema` = DATABASE()
        AND `table_name` = 'risk_merchant_function_config'
        AND ((`column_name` = 'id' AND `column_comment` <> '物理主键。')
          OR (`column_name` = 'merchant_id' AND `column_comment` <> '商户号。')
          OR (`column_name` = 'module_type' AND `column_comment` <> '风控模块类型：WHITE、BLACK、RULE。')
          OR (`column_name` = 'function_code' AND `column_comment` <> '稳定风控功能编码。')
          OR (`column_name` = 'enabled' AND `column_comment` <> '商户是否开通该可选功能：0 否，1 是。')
          OR (`column_name` = 'deleted' AND `column_comment` <> '软删除标记：0 正常，1 删除。')
          OR (`column_name` = 'created_by' AND `column_comment` <> '创建人。')
          OR (`column_name` = 'updated_by' AND `column_comment` <> '最后修改人。')
          OR (`column_name` = 'create_time' AND `column_comment` <> '创建时间。')
          OR (`column_name` = 'update_time' AND `column_comment` <> '更新时间。'))
  ) THEN
    ALTER TABLE `risk_merchant_function_config`
      MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '物理主键。',
      MODIFY COLUMN `merchant_id` varchar(32) NOT NULL COMMENT '商户号。',
      MODIFY COLUMN `module_type` varchar(32) NOT NULL COMMENT '风控模块类型：WHITE、BLACK、RULE。',
      MODIFY COLUMN `function_code` varchar(64) NOT NULL COMMENT '稳定风控功能编码。',
      MODIFY COLUMN `enabled` tinyint(1) NOT NULL DEFAULT 0 COMMENT '商户是否开通该可选功能：0 否，1 是。',
      MODIFY COLUMN `deleted` tinyint(1) NOT NULL DEFAULT 0 COMMENT '软删除标记：0 正常，1 删除。',
      MODIFY COLUMN `created_by` varchar(64) DEFAULT NULL COMMENT '创建人。',
      MODIFY COLUMN `updated_by` varchar(64) DEFAULT NULL COMMENT '最后修改人。',
      MODIFY COLUMN `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间。',
      MODIFY COLUMN `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间。',
      COMMENT = '商户可选风控功能开通状态；平台全局 AML 和监管规则不受该表控制。';
  END IF;

  IF EXISTS (
      SELECT 1
      FROM `information_schema`.`columns`
      WHERE `table_schema` = DATABASE()
        AND `table_name` = 'risk_evaluation_record'
        AND `column_name` = 'decision_reason_code'
        AND `column_comment` <> '最终决策稳定原因码。'
  ) THEN
    ALTER TABLE `risk_evaluation_record`
      MODIFY COLUMN `decision_reason_code` varchar(64) DEFAULT NULL
        COMMENT '最终决策稳定原因码。';
  END IF;

  IF EXISTS (
      SELECT 1
      FROM `information_schema`.`columns`
      WHERE `table_schema` = DATABASE()
        AND `table_name` = 'risk_evaluation_hit_detail'
        AND `column_name` = 'reason_code'
        AND `column_comment` <> '明细稳定原因码：NOT_ENABLED、NOT_CONFIGURED、NO_MATCH 等。'
  ) THEN
    ALTER TABLE `risk_evaluation_hit_detail`
      MODIFY COLUMN `reason_code` varchar(64) DEFAULT NULL
        COMMENT '明细稳定原因码：NOT_ENABLED、NOT_CONFIGURED、NO_MATCH 等。';
  END IF;
END$$
DELIMITER ;

CALL `migrate_20261017_02_risk_runtime_schema`();
DROP PROCEDURE IF EXISTS `migrate_20261017_02_risk_runtime_schema`;

-- 后检：必须确认表、唯一约束、查询索引和审计原因码列均存在。
SHOW CREATE TABLE `risk_merchant_function_config`;
SHOW CREATE TABLE `risk_evaluation_record`;
SHOW CREATE TABLE `risk_evaluation_hit_detail`;

SELECT `table_name`, `column_name`, `column_type`, `is_nullable`
FROM `information_schema`.`columns`
WHERE `table_schema` = DATABASE()
  AND (
        (`table_name` = 'risk_merchant_function_config'
            AND `column_name` IN ('merchant_id', 'module_type', 'function_code', 'enabled', 'deleted'))
     OR (`table_name` = 'risk_evaluation_record' AND `column_name` = 'decision_reason_code')
     OR (`table_name` = 'risk_evaluation_hit_detail' AND `column_name` = 'reason_code')
      )
ORDER BY `table_name`, `ordinal_position`;

SELECT `index_name`, GROUP_CONCAT(`column_name` ORDER BY `seq_in_index`) AS `columns`
FROM `information_schema`.`statistics`
WHERE `table_schema` = DATABASE()
  AND `table_name` = 'risk_merchant_function_config'
GROUP BY `index_name`
ORDER BY `index_name`;
