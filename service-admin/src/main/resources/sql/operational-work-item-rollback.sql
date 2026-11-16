-- 统一运营待办第一批回滚。
-- 执行前确认不再需要保留负责人、SLA 和处理轨迹；来源业务事实不受影响。

START TRANSACTION;

SET @admin_app_id = (
    SELECT id FROM sys_app WHERE app_code = 'ADMIN' AND deleted = 0 ORDER BY id LIMIT 1
);

DELETE relation
FROM sys_role_permission relation
JOIN sys_permission permission ON permission.id = relation.permission_id
WHERE relation.app_id = @admin_app_id
  AND permission.app_id = @admin_app_id
  AND permission.permission_code LIKE 'operations:work-item:%';

DELETE relation
FROM sys_role_menu relation
JOIN sys_menu menu ON menu.id = relation.menu_id
WHERE relation.app_id = @admin_app_id
  AND menu.app_id = @admin_app_id
  AND menu.menu_code IN (
      'admin_operations_catalog_v1', 'admin_operational_work_item_v1',
      'admin_operational_work_item_detail_v1', 'admin_operational_work_item_claim_v1',
      'admin_operational_work_item_assign_v1', 'admin_operational_work_item_transfer_v1',
      'admin_operational_work_item_complete_v1', 'admin_operational_work_item_remark_v1',
      'admin_operational_work_item_export_v1'
  );

DELETE FROM sys_permission
WHERE app_id = @admin_app_id AND permission_code LIKE 'operations:work-item:%';

DELETE FROM sys_menu
WHERE app_id = @admin_app_id
  AND menu_code IN (
      'admin_operational_work_item_detail_v1', 'admin_operational_work_item_claim_v1',
      'admin_operational_work_item_assign_v1', 'admin_operational_work_item_transfer_v1',
      'admin_operational_work_item_complete_v1', 'admin_operational_work_item_remark_v1',
      'admin_operational_work_item_export_v1',
      'admin_operational_work_item_v1', 'admin_operations_catalog_v1'
  );

DELETE FROM sys_job_task WHERE job_code = 'OPERATIONAL_WORK_ITEM_SYNC';

COMMIT;

DROP TABLE IF EXISTS admin_operational_work_item_history;
DROP TABLE IF EXISTS admin_operational_work_item;
