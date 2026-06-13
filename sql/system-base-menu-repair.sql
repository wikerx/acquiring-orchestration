-- 系统管理与基础数据菜单/按钮权限修复脚本
-- DEPRECATED：不要再执行本文件。
-- 原因：本文件早期使用了错误的菜单 ID（例如基础数据=240、部门=214、岗位=217 等），
--      已经在本地验证会导致菜单层级错乱。
-- 请使用：sql/system-menu-tree-direct-db-fix.sql
SIGNAL SQLSTATE '45000'
    SET MESSAGE_TEXT = 'Deprecated SQL: use sql/system-menu-tree-direct-db-fix.sql instead.';

-- 目标：MySQL 8，admin 应用 app_id=1，admin 角色 role_id=1
-- 说明：脚本可重复执行，不删除业务数据，只规范菜单、按钮和 admin 授权。

SET FOREIGN_KEY_CHECKS = 0;

-- 1. 规范一级目录和菜单。
UPDATE sys_menu
SET menu_name = '系统管理', parent_id = 0, menu_type = 'CATALOG', route_path = '/system',
    component_path = NULL, permission_code = NULL, visible = 1, status = 1, sort_no = 10,
    updated_at = CURRENT_TIMESTAMP(3)
WHERE id = 210 AND app_id = 1 AND deleted = 0;

UPDATE sys_menu
SET menu_name = '基础数据', parent_id = 0, menu_type = 'CATALOG', route_path = '/base',
    component_path = NULL, permission_code = NULL, visible = 1, status = 1, sort_no = 30,
    updated_at = CURRENT_TIMESTAMP(3)
WHERE id = 240 AND app_id = 1 AND deleted = 0;

UPDATE sys_menu
SET menu_code = 'system_dept', menu_name = '部门管理', parent_id = 210, menu_type = 'MENU',
    route_path = '/system/dept', component_path = 'system/dept/index',
    permission_code = 'system:dept:list', icon = 'OfficeBuilding', visible = 1, status = 1, sort_no = 14,
    updated_at = CURRENT_TIMESTAMP(3)
WHERE id = 214 AND app_id = 1 AND deleted = 0;

UPDATE sys_menu
SET menu_code = 'system_dict', menu_name = '字典管理', parent_id = 210, menu_type = 'MENU',
    route_path = '/system/dict', component_path = 'system/dict/index',
    permission_code = 'system:dict:list', icon = 'Tickets', visible = 1, status = 1, sort_no = 16,
    updated_at = CURRENT_TIMESTAMP(3)
WHERE id = 215 AND app_id = 1 AND deleted = 0;

INSERT INTO sys_menu (id, app_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path,
                      permission_code, icon, visible, keep_alive, external_link, sort_no, status, deleted)
VALUES
    (217, 1, 210, 'system_post', '岗位管理', 'MENU', '/system/post', 'system/post/index', 'system:post:list', 'Postcard', 1, 0, 0, 15, 1, 0),
    (218, 1, 210, 'system_config', '参数设置', 'MENU', '/system/config', 'system/config/index', 'system:config:list', 'Setting', 1, 0, 0, 17, 1, 0)
ON DUPLICATE KEY UPDATE
    parent_id = VALUES(parent_id), menu_name = VALUES(menu_name), menu_type = VALUES(menu_type),
    route_path = VALUES(route_path), component_path = VALUES(component_path),
    permission_code = VALUES(permission_code), icon = VALUES(icon), visible = VALUES(visible),
    sort_no = VALUES(sort_no), status = VALUES(status), deleted = 0, updated_at = CURRENT_TIMESTAMP(3);

UPDATE sys_menu
SET menu_name = '国家/地区', parent_id = 240, permission_code = 'base:country:list',
    route_path = '/base/country', component_path = 'base/country', visible = 1, status = 1, sort_no = 31,
    updated_at = CURRENT_TIMESTAMP(3)
WHERE id = 241 AND app_id = 1 AND deleted = 0;

UPDATE sys_menu
SET menu_name = '币种管理', parent_id = 240, permission_code = 'base:currency:list',
    route_path = '/base/currency', component_path = 'base/currency', visible = 1, status = 1, sort_no = 32,
    updated_at = CURRENT_TIMESTAMP(3)
WHERE id = 242 AND app_id = 1 AND deleted = 0;

UPDATE sys_menu
SET menu_name = '地区币种配置', parent_id = 240, permission_code = 'base:countryCurrency:list',
    route_path = '/base/region-currency', component_path = 'base/region-currency', visible = 1, status = 1, sort_no = 33,
    updated_at = CURRENT_TIMESTAMP(3)
WHERE id = 243 AND app_id = 1 AND deleted = 0;

-- 2. 补齐 sys_menu 按钮权限。角色授权树以 sys_menu 为准。
INSERT INTO sys_menu (id, app_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path,
                      permission_code, icon, visible, keep_alive, external_link, sort_no, status, deleted)
VALUES
    (324, 1, 214, 'system_dept_query', '部门查询', 'BUTTON', NULL, NULL, 'system:dept:query', NULL, 0, 0, 0, 1, 1, 0),
    (325, 1, 214, 'system_dept_add', '部门新增', 'BUTTON', NULL, NULL, 'system:dept:add', NULL, 0, 0, 0, 2, 1, 0),
    (326, 1, 214, 'system_dept_edit', '部门修改', 'BUTTON', NULL, NULL, 'system:dept:edit', NULL, 0, 0, 0, 3, 1, 0),
    (327, 1, 214, 'system_dept_remove', '部门删除', 'BUTTON', NULL, NULL, 'system:dept:remove', NULL, 0, 0, 0, 4, 1, 0),
    (344, 1, 214, 'system_dept_export', '部门导出', 'BUTTON', NULL, NULL, 'system:dept:export', NULL, 0, 0, 0, 5, 1, 0),

    (328, 1, 217, 'system_post_query', '岗位查询', 'BUTTON', NULL, NULL, 'system:post:query', NULL, 0, 0, 0, 1, 1, 0),
    (329, 1, 217, 'system_post_add', '岗位新增', 'BUTTON', NULL, NULL, 'system:post:add', NULL, 0, 0, 0, 2, 1, 0),
    (330, 1, 217, 'system_post_edit', '岗位修改', 'BUTTON', NULL, NULL, 'system:post:edit', NULL, 0, 0, 0, 3, 1, 0),
    (331, 1, 217, 'system_post_remove', '岗位删除', 'BUTTON', NULL, NULL, 'system:post:remove', NULL, 0, 0, 0, 4, 1, 0),
    (332, 1, 217, 'system_post_export', '岗位导出', 'BUTTON', NULL, NULL, 'system:post:export', NULL, 0, 0, 0, 5, 1, 0),

    (333, 1, 215, 'system_dict_query', '字典查询', 'BUTTON', NULL, NULL, 'system:dict:query', NULL, 0, 0, 0, 1, 1, 0),
    (334, 1, 215, 'system_dict_add', '字典新增', 'BUTTON', NULL, NULL, 'system:dict:add', NULL, 0, 0, 0, 2, 1, 0),
    (335, 1, 215, 'system_dict_edit', '字典修改', 'BUTTON', NULL, NULL, 'system:dict:edit', NULL, 0, 0, 0, 3, 1, 0),
    (336, 1, 215, 'system_dict_remove', '字典删除', 'BUTTON', NULL, NULL, 'system:dict:remove', NULL, 0, 0, 0, 4, 1, 0),
    (345, 1, 215, 'system_dict_export', '字典导出', 'BUTTON', NULL, NULL, 'system:dict:export', NULL, 0, 0, 0, 5, 1, 0),
    (346, 1, 215, 'system_dict_refresh', '刷新缓存', 'BUTTON', NULL, NULL, 'system:dict:refresh', NULL, 0, 0, 0, 6, 1, 0),
    (347, 1, 215, 'system_dict_data_list', '字典数据查询', 'BUTTON', NULL, NULL, 'system:dictData:list', NULL, 0, 0, 0, 7, 1, 0),
    (348, 1, 215, 'system_dict_data_query', '字典数据详情', 'BUTTON', NULL, NULL, 'system:dictData:query', NULL, 0, 0, 0, 8, 1, 0),
    (349, 1, 215, 'system_dict_data_add', '字典数据新增', 'BUTTON', NULL, NULL, 'system:dictData:add', NULL, 0, 0, 0, 9, 1, 0),
    (350, 1, 215, 'system_dict_data_edit', '字典数据修改', 'BUTTON', NULL, NULL, 'system:dictData:edit', NULL, 0, 0, 0, 10, 1, 0),
    (351, 1, 215, 'system_dict_data_remove', '字典数据删除', 'BUTTON', NULL, NULL, 'system:dictData:remove', NULL, 0, 0, 0, 11, 1, 0),
    (352, 1, 215, 'system_dict_data_export', '字典数据导出', 'BUTTON', NULL, NULL, 'system:dictData:export', NULL, 0, 0, 0, 12, 1, 0),

    (337, 1, 218, 'system_config_query', '参数查询', 'BUTTON', NULL, NULL, 'system:config:query', NULL, 0, 0, 0, 1, 1, 0),
    (338, 1, 218, 'system_config_add', '参数新增', 'BUTTON', NULL, NULL, 'system:config:add', NULL, 0, 0, 0, 2, 1, 0),
    (339, 1, 218, 'system_config_edit', '参数修改', 'BUTTON', NULL, NULL, 'system:config:edit', NULL, 0, 0, 0, 3, 1, 0),
    (340, 1, 218, 'system_config_remove', '参数删除', 'BUTTON', NULL, NULL, 'system:config:remove', NULL, 0, 0, 0, 4, 1, 0),
    (353, 1, 218, 'system_config_export', '参数导出', 'BUTTON', NULL, NULL, 'system:config:export', NULL, 0, 0, 0, 5, 1, 0),
    (354, 1, 218, 'system_config_refresh', '刷新缓存', 'BUTTON', NULL, NULL, 'system:config:refresh', NULL, 0, 0, 0, 6, 1, 0),

    (355, 1, 241, 'base_country_query', '国家地区查询', 'BUTTON', NULL, NULL, 'base:country:query', NULL, 0, 0, 0, 1, 1, 0),
    (356, 1, 241, 'base_country_add', '国家地区新增', 'BUTTON', NULL, NULL, 'base:country:add', NULL, 0, 0, 0, 2, 1, 0),
    (357, 1, 241, 'base_country_edit', '国家地区修改', 'BUTTON', NULL, NULL, 'base:country:edit', NULL, 0, 0, 0, 3, 1, 0),
    (358, 1, 241, 'base_country_remove', '国家地区删除', 'BUTTON', NULL, NULL, 'base:country:remove', NULL, 0, 0, 0, 4, 1, 0),
    (359, 1, 241, 'base_country_export', '国家地区导出', 'BUTTON', NULL, NULL, 'base:country:export', NULL, 0, 0, 0, 5, 1, 0),
    (360, 1, 241, 'base_country_change_status', '国家地区状态', 'BUTTON', NULL, NULL, 'base:country:changeStatus', NULL, 0, 0, 0, 6, 1, 0),

    (361, 1, 242, 'base_currency_query', '币种查询', 'BUTTON', NULL, NULL, 'base:currency:query', NULL, 0, 0, 0, 1, 1, 0),
    (362, 1, 242, 'base_currency_add', '币种新增', 'BUTTON', NULL, NULL, 'base:currency:add', NULL, 0, 0, 0, 2, 1, 0),
    (363, 1, 242, 'base_currency_edit', '币种修改', 'BUTTON', NULL, NULL, 'base:currency:edit', NULL, 0, 0, 0, 3, 1, 0),
    (364, 1, 242, 'base_currency_remove', '币种删除', 'BUTTON', NULL, NULL, 'base:currency:remove', NULL, 0, 0, 0, 4, 1, 0),
    (365, 1, 242, 'base_currency_export', '币种导出', 'BUTTON', NULL, NULL, 'base:currency:export', NULL, 0, 0, 0, 5, 1, 0),
    (366, 1, 242, 'base_currency_change_status', '币种状态', 'BUTTON', NULL, NULL, 'base:currency:changeStatus', NULL, 0, 0, 0, 6, 1, 0),

    (367, 1, 243, 'base_country_currency_query', '地区币种查询', 'BUTTON', NULL, NULL, 'base:countryCurrency:query', NULL, 0, 0, 0, 1, 1, 0),
    (368, 1, 243, 'base_country_currency_add', '地区币种新增', 'BUTTON', NULL, NULL, 'base:countryCurrency:add', NULL, 0, 0, 0, 2, 1, 0),
    (369, 1, 243, 'base_country_currency_edit', '地区币种修改', 'BUTTON', NULL, NULL, 'base:countryCurrency:edit', NULL, 0, 0, 0, 3, 1, 0),
    (370, 1, 243, 'base_country_currency_remove', '地区币种删除', 'BUTTON', NULL, NULL, 'base:countryCurrency:remove', NULL, 0, 0, 0, 4, 1, 0),
    (371, 1, 243, 'base_country_currency_export', '地区币种导出', 'BUTTON', NULL, NULL, 'base:countryCurrency:export', NULL, 0, 0, 0, 5, 1, 0),
    (372, 1, 243, 'base_country_currency_change_status', '地区币种状态', 'BUTTON', NULL, NULL, 'base:countryCurrency:changeStatus', NULL, 0, 0, 0, 6, 1, 0)
ON DUPLICATE KEY UPDATE
    parent_id = VALUES(parent_id), menu_name = VALUES(menu_name), menu_type = VALUES(menu_type),
    permission_code = VALUES(permission_code), visible = VALUES(visible), sort_no = VALUES(sort_no),
    status = VALUES(status), deleted = 0, updated_at = CURRENT_TIMESTAMP(3);

-- 3. 兼容旧 sys_permission 标识。角色授权树不再依赖 sys_permission，但保留资源权限映射的标准标识。
UPDATE sys_permission SET permission_code = 'system:dict:remove', updated_at = CURRENT_TIMESTAMP(3)
WHERE app_id = 1 AND deleted = 0 AND permission_code = 'system:dict:delete';

UPDATE sys_permission SET permission_code = 'system:config:remove', updated_at = CURRENT_TIMESTAMP(3)
WHERE app_id = 1 AND deleted = 0 AND permission_code = 'system:config:delete';

UPDATE sys_permission SET permission_code = 'base:country:remove', updated_at = CURRENT_TIMESTAMP(3)
WHERE app_id = 1 AND deleted = 0 AND permission_code = 'base:country:delete';

UPDATE sys_permission SET permission_code = 'base:currency:remove', updated_at = CURRENT_TIMESTAMP(3)
WHERE app_id = 1 AND deleted = 0 AND permission_code = 'base:currency:delete';

UPDATE sys_permission SET permission_code = REPLACE(permission_code, 'base:region-currency:', 'base:countryCurrency:'),
                          updated_at = CURRENT_TIMESTAMP(3)
WHERE app_id = 1 AND deleted = 0 AND permission_code LIKE 'base:region-currency:%';

-- 4. 授权 admin 角色拥有全部目标菜单和按钮。
INSERT IGNORE INTO sys_role_menu (app_id, role_id, menu_id, deleted)
SELECT 1, 1, id, 0
FROM sys_menu
WHERE app_id = 1
  AND deleted = 0
  AND id IN (210, 214, 215, 217, 218, 240, 241, 242, 243,
             324, 325, 326, 327, 328, 329, 330, 331, 332,
             333, 334, 335, 336, 337, 338, 339, 340,
             344, 345, 346, 347, 348, 349, 350, 351, 352,
             353, 354, 355, 356, 357, 358, 359, 360,
             361, 362, 363, 364, 365, 366, 367, 368, 369, 370, 371, 372);

SET FOREIGN_KEY_CHECKS = 1;
