-- 修复曾通过非 UTF-8 客户端导入的商户菜单和权限名称。
-- MySQL 8.0+，可重复执行；仅按应用编码和稳定资源编码更新已确认的四条记录。

SET NAMES utf8mb4;

START TRANSACTION;

UPDATE sys_menu menu
JOIN sys_app app ON app.id = menu.app_id
                AND app.app_code = 'MERCHANT'
                AND app.deleted = 0
SET menu.menu_name = CASE menu.menu_code
        WHEN 'merchant_info_catalog_v1' THEN '商户信息'
        WHEN 'merchant_profile_maintenance_v1' THEN '资料维护'
        ELSE menu.menu_name
    END,
    menu.updated_at = CURRENT_TIMESTAMP(3)
WHERE menu.menu_code IN ('merchant_info_catalog_v1', 'merchant_profile_maintenance_v1')
  AND menu.deleted = 0
  AND menu.menu_name <> CASE menu.menu_code
        WHEN 'merchant_info_catalog_v1' THEN '商户信息'
        WHEN 'merchant_profile_maintenance_v1' THEN '资料维护'
        ELSE menu.menu_name
    END;

UPDATE sys_permission permission
JOIN sys_app app ON app.id = permission.app_id
                AND app.app_code = 'MERCHANT'
                AND app.deleted = 0
SET permission.permission_name = CASE permission.permission_code
        WHEN 'merchant:info:view' THEN '商户资料查询'
        WHEN 'merchant:info:edit' THEN '商户资料维护'
        ELSE permission.permission_name
    END,
    permission.updated_at = CURRENT_TIMESTAMP(3)
WHERE permission.permission_code IN ('merchant:info:view', 'merchant:info:edit')
  AND permission.deleted = 0
  AND permission.permission_name <> CASE permission.permission_code
        WHEN 'merchant:info:view' THEN '商户资料查询'
        WHEN 'merchant:info:edit' THEN '商户资料维护'
        ELSE permission.permission_name
    END;

COMMIT;
