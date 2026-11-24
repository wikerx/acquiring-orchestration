-- 商户交易结算及保证金结算独立授权。请在结算菜单基线之后执行。
-- 仅首次创建时承接对应的旧权限。重新执行不会恢复已撤销的角色权限。
SET NAMES utf8mb4;
START TRANSACTION;

SET @merchant_settlement_permission_specs = '[
    {"code":"merchant:settlement:reserve-batch:list","name":"保证金结算批次查询","method":"POST","path":"/merchant/settlements/reserve-batches/search","legacy":"merchant:settlement:batch:list","menu":"merchant_settlement_reserve_v1"},
    {"code":"merchant:settlement:reserve-batch:detail","name":"保证金结算批次详情","method":"GET","path":"/merchant/settlements/reserve-batches/*","legacy":"merchant:settlement:batch:detail","menu":"merchant_settlement_reserve_v1"},
    {"code":"merchant:settlement:reserve-batch:export","name":"导出保证金结算批次","method":"POST","path":"/merchant/settlements/reserve-batches/export","legacy":"merchant:settlement:batch:export","menu":"merchant_settlement_reserve_v1"},
    {"code":"merchant:settlement:reserve-batch:summary:list","name":"查看保证金结算汇总","method":"GET","path":"/merchant/settlements/reserve-batches/*/summaries","legacy":"merchant:settlement:batch:summary:list","menu":"merchant_settlement_reserve_v1"},
    {"code":"merchant:settlement:reserve-batch:summary:export","name":"导出保证金结算汇总","method":"POST","path":"/merchant/settlements/reserve-batches/*/summaries/export","legacy":"merchant:settlement:batch:summary:export","menu":"merchant_settlement_reserve_v1"},
    {"code":"merchant:settlement:reserve-batch:voucher:view","name":"预览正式保证金结算单","method":"GET","path":"/merchant/settlements/reserve-batches/*/voucher/preview","legacy":"merchant:settlement:batch:voucher-download","menu":"merchant_settlement_reserve_v1"},
    {"code":"merchant:settlement:reserve-batch:voucher-download","name":"下载正式保证金结算单","method":"GET","path":"/merchant/settlements/reserve-batches/*/voucher","legacy":"merchant:settlement:batch:voucher-download","menu":"merchant_settlement_reserve_v1"},
    {"code":"merchant:settlement:reserve-item:detail","name":"查看保证金逐笔详情","method":"GET","path":"/merchant/settlements/reserve-items/*","legacy":"merchant:settlement:reserve-item:list","menu":"merchant_settlement_reserve_v1"},
    {"code":"merchant:settlement:batch:voucher:view","name":"预览正式交易结算单","method":"GET","path":"/merchant/settlements/*/voucher/preview","legacy":"merchant:settlement:batch:voucher-download","menu":"merchant_settlement_batch_v1"},
    {"code":"merchant:settlement:transaction-item:detail","name":"查看交易结算财务组件","method":"GET","path":"/merchant/settlements/transaction-items/batches/*/transactions/*","legacy":"merchant:settlement:transaction-item:list","menu":"merchant_settlement_batch_v1"}
]';

SELECT COALESCE(JSON_ARRAYAGG(spec.code), JSON_ARRAY())
INTO @merchant_new_settlement_permissions
FROM sys_app app
JOIN JSON_TABLE(@merchant_settlement_permission_specs, '$[*]' COLUMNS (
    code VARCHAR(150) PATH '$.code'
)) spec
LEFT JOIN sys_permission existing ON existing.app_id = app.id AND existing.permission_code = spec.code
                                AND existing.deleted = 0
WHERE app.app_code = 'MERCHANT' AND app.deleted = 0 AND existing.id IS NULL;

INSERT INTO sys_permission (app_id, menu_id, permission_code, permission_name, permission_type,
                            resource_method, resource_path, description, status, deleted)
SELECT app.id, menu.id, spec.code, spec.name, 'BUTTON', spec.method, spec.path, spec.name, 1, 0
FROM sys_app app
JOIN JSON_TABLE(@merchant_settlement_permission_specs, '$[*]' COLUMNS (
    code VARCHAR(150) PATH '$.code',
    name VARCHAR(120) PATH '$.name',
    method VARCHAR(10) PATH '$.method',
    path VARCHAR(255) PATH '$.path',
    legacy VARCHAR(150) PATH '$.legacy',
    menu VARCHAR(100) PATH '$.menu'
)) spec
JOIN sys_menu menu ON menu.app_id = app.id AND menu.menu_code = spec.menu AND menu.deleted = 0
WHERE app.app_code = 'MERCHANT' AND app.deleted = 0
  AND JSON_CONTAINS(@merchant_new_settlement_permissions, JSON_QUOTE(spec.code));

-- 菜单节点由角色菜单授权控制。查询按钮各有自己的权限，取消查询不影响菜单授权。
UPDATE sys_menu menu
JOIN sys_app app ON app.id = menu.app_id AND app.app_code = 'MERCHANT' AND app.deleted = 0
SET menu.permission_code = NULL
WHERE menu.deleted = 0 AND (
    (menu.menu_code = 'merchant_settlement_batch_v1' AND menu.permission_code = 'merchant:settlement:batch:list')
 OR (menu.menu_code = 'merchant_settlement_reserve_v1' AND menu.permission_code = 'merchant:settlement:reserve-item:list')
);

UPDATE sys_permission permission
JOIN sys_app app ON app.id = permission.app_id AND app.app_code = 'MERCHANT' AND app.deleted = 0
SET permission.permission_type = 'BUTTON',
    permission.permission_name = CASE permission.permission_code
        WHEN 'merchant:settlement:reserve-item:list' THEN '保证金结算明细查询'
        WHEN 'merchant:settlement:reserve-item:export' THEN '保证金结算明细导出'
        ELSE permission.permission_name END
WHERE permission.deleted = 0 AND permission.permission_code IN (
    'merchant:settlement:batch:list', 'merchant:settlement:reserve-item:list', 'merchant:settlement:reserve-item:export'
);

-- 平台可分配权限沿用原业务授权边界。
INSERT IGNORE INTO sys_merchant_permission_grant
    (merchant_id, app_id, permission_id, grant_source, status, created_at, updated_at, deleted)
SELECT grant_row.merchant_id, grant_row.app_id, new_permission.id, grant_row.grant_source,
       1, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 0
FROM sys_merchant_permission_grant grant_row
JOIN sys_app app ON app.id = grant_row.app_id AND app.app_code = 'MERCHANT' AND app.deleted = 0
JOIN sys_permission legacy ON legacy.id = grant_row.permission_id AND legacy.app_id = app.id
                          AND legacy.deleted = 0 AND legacy.status = 1
JOIN JSON_TABLE(@merchant_settlement_permission_specs, '$[*]' COLUMNS (
    code VARCHAR(150) PATH '$.code',
    legacy VARCHAR(150) PATH '$.legacy'
)) spec ON spec.legacy = legacy.permission_code
JOIN sys_permission new_permission ON new_permission.app_id = app.id
                                  AND new_permission.permission_code = spec.code AND new_permission.deleted = 0
WHERE grant_row.deleted = 0 AND grant_row.status = 1
  AND JSON_CONTAINS(@merchant_new_settlement_permissions, JSON_QUOTE(spec.code));

-- 角色继承各自原有动作授权。原来没有对应授权的角色保持未授予。
INSERT IGNORE INTO sys_role_permission (app_id, role_id, permission_id, deleted)
SELECT old_role.app_id, old_role.role_id, new_permission.id, 0
FROM sys_role_permission old_role
JOIN sys_role role ON role.id = old_role.role_id AND role.app_id = old_role.app_id AND role.deleted = 0
JOIN sys_app app ON app.id = old_role.app_id AND app.app_code = 'MERCHANT' AND app.deleted = 0
JOIN sys_permission legacy ON legacy.id = old_role.permission_id AND legacy.app_id = app.id
                          AND legacy.deleted = 0 AND legacy.status = 1
JOIN JSON_TABLE(@merchant_settlement_permission_specs, '$[*]' COLUMNS (
    code VARCHAR(150) PATH '$.code',
    legacy VARCHAR(150) PATH '$.legacy'
)) spec ON spec.legacy = legacy.permission_code
JOIN sys_permission new_permission ON new_permission.app_id = app.id
                                  AND new_permission.permission_code = spec.code AND new_permission.deleted = 0
WHERE old_role.deleted = 0
  AND JSON_CONTAINS(@merchant_new_settlement_permissions, JSON_QUOTE(spec.code))
  AND (role.merchant_id IS NULL OR EXISTS (
      SELECT 1 FROM sys_merchant_permission_grant grant_row
      WHERE grant_row.app_id = app.id AND grant_row.merchant_id = role.merchant_id
        AND grant_row.permission_id = new_permission.id AND grant_row.status = 1 AND grant_row.deleted = 0
  ));

COMMIT;
SET @merchant_settlement_permission_specs = NULL;
SET @merchant_new_settlement_permissions = NULL;
