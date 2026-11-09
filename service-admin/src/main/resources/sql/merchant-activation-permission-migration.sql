-- 商户最终开通使用独立高风险权限，禁止继续复用冻结/解冻权限。
SET NAMES utf8mb4;
START TRANSACTION;

SET @admin_app_id = (
    SELECT MIN(id)
    FROM sys_app
    WHERE app_code = 'ADMIN' AND deleted = 0
);

SET @merchant_info_menu_id = (
    SELECT MIN(id)
    FROM sys_menu
    WHERE app_id = @admin_app_id
      AND menu_code = 'merchant_info_manage'
      AND status = 1
      AND deleted = 0
);

INSERT INTO sys_permission (
    app_id, menu_id, permission_code, permission_name, permission_type,
    resource_method, resource_path, description, status, deleted
)
SELECT @admin_app_id, @merchant_info_menu_id,
       'merchant:info:activate', '激活商户', 'BUTTON',
       'POST', '/admin/merchants/*/activate',
       '在资料、结算、支付能力、费率、风控和技术接入全部就绪后开放真实交易', 1, 0
WHERE @admin_app_id IS NOT NULL
  AND @merchant_info_menu_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM sys_permission permission
      WHERE permission.app_id = @admin_app_id
        AND permission.permission_code = 'merchant:info:activate'
        AND permission.deleted = 0
  );

UPDATE sys_permission
SET menu_id = @merchant_info_menu_id,
    permission_name = BINARY '激活商户',
    permission_type = 'BUTTON',
    resource_method = 'POST',
    resource_path = '/admin/merchants/*/activate',
    description = BINARY '在资料、结算、支付能力、费率、风控和技术接入全部就绪后开放真实交易',
    status = 1,
    updated_at = CURRENT_TIMESTAMP(3)
WHERE app_id = @admin_app_id
  AND permission_code = 'merchant:info:activate'
  AND deleted = 0;

INSERT INTO sys_menu (
    app_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path,
    permission_code, icon, visible, keep_alive, external_link, sort_no, status, deleted
)
SELECT @admin_app_id, @merchant_info_menu_id,
       'merchant_info_activate', '激活商户', 'BUTTON', NULL, NULL,
       'merchant:info:activate', NULL, 0, 0, 0, 90, 1, 0
WHERE @admin_app_id IS NOT NULL
  AND @merchant_info_menu_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM sys_menu menu
      WHERE menu.app_id = @admin_app_id
        AND menu.menu_code = 'merchant_info_activate'
        AND menu.deleted = 0
  );

UPDATE sys_menu
SET parent_id = @merchant_info_menu_id,
    menu_name = BINARY '激活商户',
    menu_type = 'BUTTON',
    route_path = NULL,
    component_path = NULL,
    permission_code = 'merchant:info:activate',
    visible = 0,
    status = 1,
    updated_at = CURRENT_TIMESTAMP(3)
WHERE app_id = @admin_app_id
  AND menu_code = 'merchant_info_activate'
  AND deleted = 0;

SET @merchant_activate_permission_id = (
    SELECT MIN(id)
    FROM sys_permission
    WHERE app_id = @admin_app_id
      AND permission_code = 'merchant:info:activate'
      AND deleted = 0
);

SET @merchant_activate_menu_id = (
    SELECT MIN(id)
    FROM sys_menu
    WHERE app_id = @admin_app_id
      AND menu_code = 'merchant_info_activate'
      AND deleted = 0
);

INSERT IGNORE INTO sys_role_permission (app_id, role_id, permission_id, deleted)
SELECT @admin_app_id, role.id, @merchant_activate_permission_id, 0
FROM sys_role role
WHERE role.app_id = @admin_app_id
  AND role.role_code = 'SUPER_ADMIN'
  AND role.status = 1
  AND role.deleted = 0
  AND @merchant_activate_permission_id IS NOT NULL;

INSERT IGNORE INTO sys_role_menu (app_id, role_id, menu_id, deleted)
SELECT @admin_app_id, role.id, @merchant_activate_menu_id, 0
FROM sys_role role
WHERE role.app_id = @admin_app_id
  AND role.role_code = 'SUPER_ADMIN'
  AND role.status = 1
  AND role.deleted = 0
  AND @merchant_activate_menu_id IS NOT NULL;

COMMIT;
