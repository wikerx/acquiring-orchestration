-- 支付路由配置中心迁移前检查。返回结果必须为空或计数为0；本脚本不修改数据。

SELECT required_table.table_name AS missing_prerequisite_table
FROM (
    SELECT 'channel_info' table_name
    UNION ALL SELECT 'channel_mid_config'
    UNION ALL SELECT 'merchant_channel_mid_binding'
    UNION ALL SELECT 'sys_menu'
    UNION ALL SELECT 'sys_permission'
    UNION ALL SELECT 'sys_role'
    UNION ALL SELECT 'sys_role_menu'
    UNION ALL SELECT 'sys_role_permission'
) required_table
LEFT JOIN information_schema.tables actual
  ON actual.table_schema = DATABASE()
 AND actual.table_name = required_table.table_name
WHERE actual.table_name IS NULL;

SELECT 1 - COUNT(*) AS missing_channel_catalog_count
FROM sys_menu
WHERE app_id = 1
  AND menu_code = 'admin_channel_catalog_v1'
  AND deleted = 0;

SELECT menu_code, COUNT(*) AS duplicate_menu_count
FROM sys_menu
WHERE app_id = 1
  AND menu_code LIKE 'admin_payment_route%'
  AND deleted = 0
GROUP BY menu_code
HAVING COUNT(*) > 1;

SELECT permission_code, COUNT(*) AS duplicate_permission_count
FROM sys_permission
WHERE app_id = 1
  AND permission_code LIKE 'channel:route:%'
  AND deleted = 0
GROUP BY permission_code
HAVING COUNT(*) > 1;
