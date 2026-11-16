-- 统一运营待办第四小批：接入拒绝决策及 HIGH/CRITICAL 风险评估事件。
-- 本脚本只扩展统一待办约束和系统任务说明，不修改风控决策、支付状态或交易事实。

SET NAMES utf8mb4;

SET @work_item_table_exists = (
    SELECT COUNT(*) FROM information_schema.tables
    WHERE table_schema = DATABASE() AND table_name = 'admin_operational_work_item'
);

SET @drop_work_item_check = IF(
    @work_item_table_exists = 1 AND EXISTS (
        SELECT 1 FROM information_schema.check_constraints
        WHERE constraint_schema = DATABASE()
          AND constraint_name = 'chk_admin_operational_work_item_state'
    ),
    'ALTER TABLE admin_operational_work_item DROP CHECK chk_admin_operational_work_item_state',
    'SELECT 1'
);
PREPARE statement FROM @drop_work_item_check;
EXECUTE statement;
DEALLOCATE PREPARE statement;

SET @add_work_item_check = IF(
    @work_item_table_exists = 1,
    'ALTER TABLE admin_operational_work_item ADD CONSTRAINT chk_admin_operational_work_item_state CHECK (source_type IN (''REFUND_APPROVAL'', ''SETTLEMENT_REVIEW'', ''EXCHANGE_RATE_ALERT'', ''MERCHANT_NOTIFICATION_FAILURE'', ''CHANNEL_MATCH_ABNORMAL'', ''CLEARING_BACKLOG'', ''SETTLEMENT_BACKLOG'', ''HIGH_RISK_EVENT'') AND work_status IN (''OPEN'', ''PROCESSING'', ''CLOSED'') AND priority IN (''CRITICAL'', ''HIGH'', ''NORMAL'', ''LOW'') AND version >= 0 AND ((owner_account_id IS NULL AND owner_account_name IS NULL) OR (owner_account_id IS NOT NULL AND owner_account_name IS NOT NULL)) AND ((work_status = ''CLOSED'' AND closed_at IS NOT NULL AND close_reason IS NOT NULL) OR (work_status <> ''CLOSED'' AND closed_at IS NULL AND close_reason IS NULL)))',
    'SELECT 1'
);
PREPARE statement FROM @add_work_item_check;
EXECUTE statement;
DEALLOCATE PREPARE statement;

UPDATE sys_job_task
SET description = '每分钟幂等同步退款审批、结算复核、汇率预警、终态商户通知失败、渠道勾兑异常、清分异常积压、结算异常积压和高风险事件；系统任务禁止删除或更换处理器。',
    update_by = 'system-migration',
    update_time = NOW(3)
WHERE job_code = 'OPERATIONAL_WORK_ITEM_SYNC' AND deleted = 0;
