-- 回退统一待办中的渠道勾兑异常接入；支付核心案件事实不受影响。

DELETE history
FROM admin_operational_work_item_history history
JOIN admin_operational_work_item item ON item.id = history.work_item_id
WHERE item.source_type = 'CHANNEL_MATCH_ABNORMAL';

DELETE FROM admin_operational_work_item
WHERE source_type = 'CHANNEL_MATCH_ABNORMAL';

ALTER TABLE admin_operational_work_item
    DROP CHECK chk_admin_operational_work_item_state,
    ADD CONSTRAINT chk_admin_operational_work_item_state CHECK (
        source_type IN ('REFUND_APPROVAL', 'SETTLEMENT_REVIEW',
                        'EXCHANGE_RATE_ALERT', 'MERCHANT_NOTIFICATION_FAILURE')
        AND work_status IN ('OPEN', 'PROCESSING', 'CLOSED')
        AND priority IN ('CRITICAL', 'HIGH', 'NORMAL', 'LOW')
        AND version >= 0
        AND ((owner_account_id IS NULL AND owner_account_name IS NULL)
             OR (owner_account_id IS NOT NULL AND owner_account_name IS NOT NULL))
        AND ((work_status = 'CLOSED' AND closed_at IS NOT NULL AND close_reason IS NOT NULL)
             OR (work_status <> 'CLOSED' AND closed_at IS NULL AND close_reason IS NULL))
    );

ALTER TABLE admin_operational_work_item_history
    DROP CHECK chk_admin_operational_work_item_history_action,
    ADD CONSTRAINT chk_admin_operational_work_item_history_action CHECK (
        action_type IN ('CREATE', 'REOPEN', 'CLAIM', 'ASSIGN', 'TRANSFER', 'COMPLETE', 'REMARK', 'AUTO_CLOSE')
        AND after_status IN ('OPEN', 'PROCESSING', 'CLOSED')
        AND (before_status IS NULL OR before_status IN ('OPEN', 'PROCESSING', 'CLOSED'))
        AND ((before_owner_account_id IS NULL AND before_owner_account_name IS NULL)
             OR (before_owner_account_id IS NOT NULL AND before_owner_account_name IS NOT NULL))
        AND ((after_owner_account_id IS NULL AND after_owner_account_name IS NULL)
             OR (after_owner_account_id IS NOT NULL AND after_owner_account_name IS NOT NULL))
    );

UPDATE sys_job_task
SET description = '每分钟幂等同步退款审批、结算复核、汇率预警和终态商户通知失败；系统任务禁止删除或更换处理器。',
    update_by = 'system-rollback',
    update_time = NOW(3)
WHERE job_code = 'OPERATIONAL_WORK_ITEM_SYNC' AND deleted = 0;
