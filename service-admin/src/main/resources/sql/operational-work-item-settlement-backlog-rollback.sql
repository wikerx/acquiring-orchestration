-- 回退统一待办中的结算异常积压接入；结算批次、结算结果和资金事实不受影响。

SET NAMES utf8mb4;

DELETE history
FROM admin_operational_work_item_history history
JOIN admin_operational_work_item item ON item.id = history.work_item_id
WHERE item.source_type = 'SETTLEMENT_BACKLOG';

DELETE FROM admin_operational_work_item
WHERE source_type = 'SETTLEMENT_BACKLOG';

ALTER TABLE admin_operational_work_item
    DROP CHECK chk_admin_operational_work_item_state,
    ADD CONSTRAINT chk_admin_operational_work_item_state CHECK (
        source_type IN ('REFUND_APPROVAL', 'SETTLEMENT_REVIEW',
                        'EXCHANGE_RATE_ALERT', 'MERCHANT_NOTIFICATION_FAILURE',
                        'CHANNEL_MATCH_ABNORMAL', 'CLEARING_BACKLOG')
        AND work_status IN ('OPEN', 'PROCESSING', 'CLOSED')
        AND priority IN ('CRITICAL', 'HIGH', 'NORMAL', 'LOW')
        AND version >= 0
        AND ((owner_account_id IS NULL AND owner_account_name IS NULL)
             OR (owner_account_id IS NOT NULL AND owner_account_name IS NOT NULL))
        AND ((work_status = 'CLOSED' AND closed_at IS NOT NULL AND close_reason IS NOT NULL)
             OR (work_status <> 'CLOSED' AND closed_at IS NULL AND close_reason IS NULL))
    );

UPDATE sys_job_task
SET description = '每分钟幂等同步退款审批、结算复核、汇率预警、终态商户通知失败、渠道勾兑异常和清分异常积压；系统任务禁止删除或更换处理器。',
    update_by = 'system-rollback',
    update_time = NOW(3)
WHERE job_code = 'OPERATIONAL_WORK_ITEM_SYNC' AND deleted = 0;
