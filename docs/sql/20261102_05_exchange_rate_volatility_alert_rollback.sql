-- 汇率波动与预警回滚/止损脚本。
-- 默认只停用入口和任务并保留审计事实；只有三张业务表均为空时才自动删除结构。

UPDATE sys_job_task
SET status = 'DISABLED',
    next_trigger_time = NULL,
    update_by = 'rollback',
    update_time = NOW(3)
WHERE job_code = 'EXCHANGE_RATE_VOLATILITY_MONITOR'
  AND deleted = 0;

UPDATE sys_permission
SET status = 0,
    updated_at = NOW(3)
WHERE app_id = 1
  AND deleted = 0
  AND (
      permission_code LIKE 'exchange:volatility:%'
      OR permission_code LIKE 'exchange:alert-event:%'
      OR permission_code LIKE 'exchange:alert-rule:%'
  );

UPDATE sys_menu
SET status = 0,
    visible = 0,
    updated_at = NOW(3)
WHERE app_id = 1
  AND deleted = 0
  AND menu_code IN (
      'exchange_rate_volatility', 'exchange_rate_volatility_query', 'exchange_rate_volatility_export',
      'exchange_rate_alert_event', 'exchange_rate_alert_event_query', 'exchange_rate_alert_event_ack',
      'exchange_rate_alert_event_close', 'exchange_rate_alert_event_export',
      'exchange_rate_alert_rule', 'exchange_rate_alert_rule_query', 'exchange_rate_alert_rule_add',
      'exchange_rate_alert_rule_edit', 'exchange_rate_alert_rule_remove', 'exchange_rate_alert_rule_start',
      'exchange_rate_alert_rule_stop', 'exchange_rate_alert_rule_import', 'exchange_rate_alert_rule_export'
  );

DROP PROCEDURE IF EXISTS rollback_empty_exchange_rate_volatility_alert;
DELIMITER $$
CREATE PROCEDURE rollback_empty_exchange_rate_volatility_alert()
BEGIN
    DECLARE event_count bigint DEFAULT 0;
    DECLARE evaluation_count bigint DEFAULT 0;
    DECLARE custom_rule_count bigint DEFAULT 0;

    SELECT COUNT(*) INTO event_count FROM exchange_rate_alert_event;
    SELECT COUNT(*) INTO evaluation_count FROM exchange_rate_alert_evaluation;
    SELECT COUNT(*) INTO custom_rule_count
    FROM exchange_rate_alert_rule
    WHERE system_mandatory = 0;

    IF event_count = 0 AND evaluation_count = 0 AND custom_rule_count = 0 THEN
        DROP TABLE exchange_rate_alert_evaluation;
        DROP TABLE exchange_rate_alert_event;
        DROP TABLE exchange_rate_alert_rule;

        IF EXISTS (
            SELECT 1 FROM information_schema.statistics
            WHERE table_schema = DATABASE()
              AND table_name = 'exchange_business_rate'
              AND index_name = 'idx_exchange_business_rate_volatility'
        ) THEN
            ALTER TABLE exchange_business_rate DROP INDEX idx_exchange_business_rate_volatility;
        END IF;
    END IF;
END$$
DELIMITER ;
CALL rollback_empty_exchange_rate_volatility_alert();
DROP PROCEDURE rollback_empty_exchange_rate_volatility_alert;

SELECT 'Containment applied. Audit tables are retained whenever events, evaluations, or custom rules exist.' AS rollback_result;
