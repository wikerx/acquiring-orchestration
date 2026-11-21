-- REQ-001 08 执行后修复管理端支付方式菜单。适配仍保留停用 v3 目录的库。
-- 前置核对并留存 sys_menu 的当前父级和排序，以及支付方式菜单角色的父目录绑定。
-- 回退时按留存记录恢复本菜单的父级、排序和本次补入的目录绑定。
START TRANSACTION;

SET @admin_app_id = (SELECT id FROM sys_app WHERE app_code = 'ADMIN' AND deleted = 0);
SET @payment_methods_menu_id = (
    SELECT id FROM sys_menu
    WHERE app_id = @admin_app_id AND menu_code = 'merchant_payment_methods_v1'
      AND route_path = '/merchant/payment-methods' AND menu_type = 'MENU' AND deleted = 0
);
SET @merchant_catalog_id = (
    SELECT id FROM sys_menu
    WHERE app_id = @admin_app_id AND route_path = '/merchant' AND menu_type = 'CATALOG'
      AND menu_code IN ('merchant_manage', 'admin_merchant_catalog_v3', 'admin_merchant_center')
      AND status = 1 AND visible = 1 AND deleted = 0
    ORDER BY FIELD(menu_code, 'merchant_manage', 'admin_merchant_catalog_v3', 'admin_merchant_center')
    LIMIT 1
);
SET @merchant_info_sort = (
    SELECT sort_no FROM sys_menu
    WHERE app_id = @admin_app_id AND parent_id = @merchant_catalog_id AND route_path = '/merchant/info'
      AND status = 1 AND visible = 1 AND deleted = 0
    ORDER BY id LIMIT 1
);

UPDATE sys_menu
SET parent_id = @merchant_catalog_id, sort_no = COALESCE(@merchant_info_sort, sort_no)
WHERE app_id = @admin_app_id AND id = @payment_methods_menu_id AND @merchant_catalog_id IS NOT NULL
  AND (parent_id <> @merchant_catalog_id OR sort_no <> COALESCE(@merchant_info_sort, sort_no));

-- 只给已获子菜单的角色补目录。目录不携带支付方式或其他业务操作权限。
INSERT INTO sys_role_menu (app_id, role_id, menu_id)
SELECT DISTINCT leaf.app_id, leaf.role_id, @merchant_catalog_id
FROM sys_role_menu leaf
JOIN sys_role role_row ON role_row.id = leaf.role_id AND role_row.app_id = leaf.app_id
    AND role_row.status = 1 AND role_row.deleted = 0
WHERE leaf.app_id = @admin_app_id AND leaf.menu_id = @payment_methods_menu_id AND leaf.deleted = 0
  AND @merchant_catalog_id IS NOT NULL
  AND EXISTS (SELECT 1 FROM sys_menu parent_menu WHERE parent_menu.id = @merchant_catalog_id
              AND parent_menu.permission_code IS NULL)
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_menu parent_binding
      WHERE parent_binding.app_id = leaf.app_id AND parent_binding.role_id = leaf.role_id
        AND parent_binding.menu_id = @merchant_catalog_id AND parent_binding.deleted = 0
  );

COMMIT;
