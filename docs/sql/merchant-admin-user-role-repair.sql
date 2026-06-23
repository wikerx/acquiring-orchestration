-- 商户系统默认管理员用户与角色修复脚本
-- 目标：
-- 1. 每个有效商户拥有商户端 admin 用户；
-- 2. admin 绑定当前商户的 MERCHANT_ADMIN_{merchantId} 角色；
-- 3. 管理员角色菜单与权限同步为平台授权给该商户的范围，不额外越权。
--
-- 注意：
-- 当前 sys_account 仍保留 (app_id, login_account, deleted) 唯一约束，因此底层登录账号使用 admin_{merchantId}；
-- 商户系统登录入口会按 sys_merchant_user(merchant_id, login_account='admin') 解析到对应 sys_account。

SET NAMES utf8mb4;
SET @merchant_app_id := (SELECT id FROM sys_app WHERE app_code = 'MERCHANT' AND deleted = 0 LIMIT 1);

UPDATE sys_role role
JOIN base_merchant_info merchant
  ON role.app_id = @merchant_app_id
 AND role.role_code = CONCAT('MERCHANT_ADMIN_', merchant.merchant_id)
 AND role.deleted = 0
SET role.merchant_id = merchant.merchant_id,
    role.role_name = '管理员',
    role.role_type = 'SYSTEM',
    role.data_scope = 'SELF',
    role.description = CONCAT('商户端默认管理员角色，权限上限为平台授权给商户 ', merchant.merchant_id, ' 的菜单和功能'),
    role.status = 1,
    role.updated_at = CURRENT_TIMESTAMP(3)
WHERE merchant.merchant_status = 1
  AND merchant.deleted = 0;

INSERT INTO sys_role (app_id, role_code, role_name, merchant_id, role_type, data_scope, description, status, sort_no, deleted)
SELECT @merchant_app_id,
       CONCAT('MERCHANT_ADMIN_', merchant.merchant_id),
       '管理员',
       merchant.merchant_id,
       'SYSTEM',
       'SELF',
       CONCAT('商户端默认管理员角色，权限上限为平台授权给商户 ', merchant.merchant_id, ' 的菜单和功能'),
       1,
       1,
       0
FROM base_merchant_info merchant
LEFT JOIN sys_role role
  ON role.app_id = @merchant_app_id
 AND role.role_code = CONCAT('MERCHANT_ADMIN_', merchant.merchant_id)
 AND role.deleted = 0
WHERE merchant.merchant_status = 1
  AND merchant.deleted = 0
  AND role.id IS NULL;

INSERT INTO sys_user (user_type, real_name, mobile, email, status, remark, deleted)
SELECT 'MERCHANT',
       CONCAT(merchant.merchant_name, ' 管理员'),
       merchant.contact_phone,
       merchant.contact_email,
       1,
       CONCAT('商户端默认管理员用户:', merchant.merchant_id),
       0
FROM base_merchant_info merchant
LEFT JOIN sys_merchant_user merchant_user
  ON merchant_user.merchant_id = merchant.merchant_id
 AND merchant_user.login_account = 'admin'
 AND merchant_user.deleted = 0
LEFT JOIN sys_user seed_user
  ON seed_user.user_type = 'MERCHANT'
 AND seed_user.remark = CONCAT('商户端默认管理员用户:', merchant.merchant_id)
 AND seed_user.deleted = 0
WHERE merchant.merchant_status = 1
  AND merchant.deleted = 0
  AND merchant_user.id IS NULL
  AND seed_user.id IS NULL;

INSERT INTO sys_account (
    app_id, user_id, merchant_id, login_account, password_hash, password_salt, password_algo,
    mobile, email, mfa_enabled, password_expired, password_updated_at, failed_login_count,
    locked, status, remark, deleted
)
SELECT @merchant_app_id,
       seed_user.id,
       merchant.merchant_id,
       CONCAT('admin_', merchant.merchant_id),
       COALESCE(template.password_hash, '7MdL9dFjEJC57HERdArwVRfeZNJmeVLPusRcnP5s4to'),
       COALESCE(template.password_salt, '5IzBHyMjRVby5ft8CqSUNw'),
       COALESCE(template.password_algo, 'PBKDF2WithHmacSHA256'),
       COALESCE(template.mobile, merchant.contact_phone),
       COALESCE(template.email, merchant.contact_email),
       0,
       0,
       CURRENT_TIMESTAMP(3),
       0,
       0,
       1,
       '商户端默认管理员底层登录账号',
       0
FROM base_merchant_info merchant
JOIN sys_user seed_user
  ON seed_user.user_type = 'MERCHANT'
 AND seed_user.remark = CONCAT('商户端默认管理员用户:', merchant.merchant_id)
 AND seed_user.deleted = 0
LEFT JOIN sys_account template
  ON template.id = (
      SELECT account_template.id
      FROM sys_account account_template
      WHERE account_template.app_id = @merchant_app_id
        AND account_template.merchant_id = merchant.merchant_id
        AND account_template.deleted = 0
      ORDER BY account_template.id
      LIMIT 1
  )
LEFT JOIN sys_account exists_account
  ON exists_account.app_id = @merchant_app_id
 AND exists_account.login_account = CONCAT('admin_', merchant.merchant_id)
 AND exists_account.deleted = 0
LEFT JOIN sys_merchant_user merchant_user
  ON merchant_user.merchant_id = merchant.merchant_id
 AND merchant_user.login_account = 'admin'
 AND merchant_user.deleted = 0
WHERE merchant.merchant_status = 1
  AND merchant.deleted = 0
  AND exists_account.id IS NULL
  AND merchant_user.id IS NULL;

INSERT INTO sys_merchant_user (
    merchant_info_id, merchant_id, user_id, account_id, login_account, real_name, status, deleted
)
SELECT merchant.id,
       merchant.merchant_id,
       account.user_id,
       account.id,
       'admin',
       user.real_name,
       1,
       0
FROM base_merchant_info merchant
JOIN sys_account account
  ON account.app_id = @merchant_app_id
 AND account.merchant_id = merchant.merchant_id
 AND account.login_account = CONCAT('admin_', merchant.merchant_id)
 AND account.deleted = 0
JOIN sys_user user ON user.id = account.user_id AND user.deleted = 0
LEFT JOIN sys_merchant_user merchant_user
  ON merchant_user.merchant_id = merchant.merchant_id
 AND merchant_user.login_account = 'admin'
 AND merchant_user.deleted = 0
WHERE merchant.merchant_status = 1
  AND merchant.deleted = 0
  AND merchant_user.id IS NULL;

UPDATE sys_merchant_user merchant_user
JOIN base_merchant_info merchant
  ON merchant.merchant_id = merchant_user.merchant_id
 AND merchant.deleted = 0
JOIN sys_account account
  ON account.id = merchant_user.account_id
 AND account.deleted = 0
SET merchant_user.merchant_info_id = merchant.id,
    merchant_user.status = account.status,
    merchant_user.updated_at = CURRENT_TIMESTAMP(3)
WHERE merchant_user.login_account = 'admin'
  AND merchant_user.deleted = 0;

INSERT INTO sys_merchant_user_role (app_id, merchant_info_id, merchant_user_id, role_id, deleted)
SELECT @merchant_app_id,
       merchant_user.merchant_info_id,
       merchant_user.id,
       role.id,
       0
FROM sys_merchant_user merchant_user
JOIN sys_role role
  ON role.app_id = @merchant_app_id
 AND role.role_code = CONCAT('MERCHANT_ADMIN_', merchant_user.merchant_id)
 AND role.merchant_id = merchant_user.merchant_id
 AND role.status = 1
 AND role.deleted = 0
LEFT JOIN sys_merchant_user_role relation
  ON relation.app_id = @merchant_app_id
 AND relation.merchant_user_id = merchant_user.id
 AND relation.role_id = role.id
 AND relation.deleted = 0
WHERE merchant_user.login_account = 'admin'
  AND merchant_user.status = 1
  AND merchant_user.deleted = 0
  AND relation.id IS NULL;

UPDATE sys_role_menu role_menu
JOIN sys_role role
  ON role.id = role_menu.role_id
 AND role.app_id = role_menu.app_id
 AND role.app_id = @merchant_app_id
 AND role.role_code LIKE 'MERCHANT_ADMIN\_%'
 AND role.merchant_id IS NOT NULL
 AND role.deleted = 0
LEFT JOIN sys_merchant_menu_grant grant_menu
  ON grant_menu.app_id = role_menu.app_id
 AND grant_menu.merchant_id = role.merchant_id
 AND grant_menu.menu_id = role_menu.menu_id
 AND grant_menu.status = 1
 AND grant_menu.deleted = 0
SET role_menu.deleted = role_menu.id
WHERE role_menu.deleted = 0
  AND grant_menu.id IS NULL;

INSERT INTO sys_role_menu (app_id, role_id, menu_id, deleted)
SELECT @merchant_app_id,
       role.id,
       grant_menu.menu_id,
       0
FROM sys_role role
JOIN sys_merchant_menu_grant grant_menu
  ON grant_menu.app_id = @merchant_app_id
 AND grant_menu.merchant_id = role.merchant_id
 AND grant_menu.status = 1
 AND grant_menu.deleted = 0
LEFT JOIN sys_role_menu role_menu
  ON role_menu.app_id = @merchant_app_id
 AND role_menu.role_id = role.id
 AND role_menu.menu_id = grant_menu.menu_id
 AND role_menu.deleted = 0
WHERE role.app_id = @merchant_app_id
  AND role.role_code LIKE 'MERCHANT_ADMIN\_%'
  AND role.merchant_id IS NOT NULL
  AND role.status = 1
  AND role.deleted = 0
  AND role_menu.id IS NULL;

UPDATE sys_role_permission role_permission
JOIN sys_role role
  ON role.id = role_permission.role_id
 AND role.app_id = role_permission.app_id
 AND role.app_id = @merchant_app_id
 AND role.role_code LIKE 'MERCHANT_ADMIN\_%'
 AND role.merchant_id IS NOT NULL
 AND role.deleted = 0
LEFT JOIN sys_merchant_permission_grant grant_permission
  ON grant_permission.app_id = role_permission.app_id
 AND grant_permission.merchant_id = role.merchant_id
 AND grant_permission.permission_id = role_permission.permission_id
 AND grant_permission.status = 1
 AND grant_permission.deleted = 0
SET role_permission.deleted = role_permission.id
WHERE role_permission.deleted = 0
  AND grant_permission.id IS NULL;

INSERT INTO sys_role_permission (app_id, role_id, permission_id, deleted)
SELECT @merchant_app_id,
       role.id,
       grant_permission.permission_id,
       0
FROM sys_role role
JOIN sys_merchant_permission_grant grant_permission
  ON grant_permission.app_id = @merchant_app_id
 AND grant_permission.merchant_id = role.merchant_id
 AND grant_permission.status = 1
 AND grant_permission.deleted = 0
LEFT JOIN sys_role_permission role_permission
  ON role_permission.app_id = @merchant_app_id
 AND role_permission.role_id = role.id
 AND role_permission.permission_id = grant_permission.permission_id
 AND role_permission.deleted = 0
WHERE role.app_id = @merchant_app_id
  AND role.role_code LIKE 'MERCHANT_ADMIN\_%'
  AND role.merchant_id IS NOT NULL
  AND role.status = 1
  AND role.deleted = 0
  AND role_permission.id IS NULL;
