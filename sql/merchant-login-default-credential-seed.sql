-- 商户门户本地开发默认登录凭据。
-- 默认登录信息：商户号 200045，账号 merchant，密码 Merchant@123456。
-- 仅用于本地 dev/test 初始化，禁止作为生产初始化脚本执行。

INSERT IGNORE INTO base_merchant_info (merchant_id, merchant_name, merchant_short_name, merchant_status,
                                       merchant_category_code, country_code, region_code, city, address_line,
                                       contact_email, contact_phone, settlement_currency, timezone, risk_level,
                                       gmt_create, gmt_modified, deleted)
VALUES ('200045',
        'Scott Payment Merchant 200045',
        'ScottPay200045',
        1,
        '5311',
        'USA',
        'CA',
        'San Jose',
        '1 Payment Framework Road',
        'merchant200045@example.com',
        '+1-408-555-0100',
        'USD',
        'Asia/Shanghai',
        2,
        CURRENT_TIMESTAMP(3),
        CURRENT_TIMESTAMP(3),
        0);

INSERT INTO sys_user (user_type, real_name, nickname, mobile, email, country_code, language, timezone, status,
                      remark, created_at, updated_at, deleted)
SELECT 'MERCHANT',
       'Merchant Demo Admin',
       'merchant',
       '+1-408-555-0199',
       'merchant@local.local',
       'USA',
       'zh-CN',
       'Asia/Shanghai',
       1,
       '本地开发商户门户默认账号，首次登录后请修改密码',
       CURRENT_TIMESTAMP(3),
       CURRENT_TIMESTAMP(3),
       0
WHERE NOT EXISTS (
    SELECT 1
    FROM sys_account account
    JOIN sys_app app ON app.id = account.app_id AND app.deleted = 0
    WHERE app.app_code = 'MERCHANT'
      AND account.login_account = 'merchant'
      AND account.deleted = 0
);

INSERT IGNORE INTO sys_account (app_id, user_id, merchant_id, login_account, password_hash, password_salt,
                                password_algo, mobile, email, mfa_enabled, password_expired, password_updated_at,
                                failed_login_count, locked, status, remark, created_at, updated_at, deleted)
SELECT app.id,
       user.id,
       '200045',
       'merchant',
       'Zkci_umE-BiV6tpIfE9Xog-9H4R-WdtZo_yh9F71yZM',
       'TWVyY2hhbnRTZWVkU2FsdDIwMjY',
       'PBKDF2WithHmacSHA256',
       '+1-408-555-0199',
       'merchant@local.local',
       0,
       0,
       CURRENT_TIMESTAMP(3),
       0,
       0,
       1,
       '初始密码 Merchant@123456，仅用于本地开发和首次初始化',
       CURRENT_TIMESTAMP(3),
       CURRENT_TIMESTAMP(3),
       0
FROM sys_app app
JOIN sys_user user ON user.email = 'merchant@local.local' AND user.deleted = 0
WHERE app.app_code = 'MERCHANT'
  AND app.deleted = 0
  AND EXISTS (
      SELECT 1
      FROM base_merchant_info merchant
      WHERE merchant.merchant_id = '200045'
        AND merchant.merchant_status = 1
        AND merchant.deleted = 0
  )
  AND NOT EXISTS (
      SELECT 1
      FROM sys_account exists_account
      WHERE exists_account.app_id = app.id
        AND exists_account.login_account = 'merchant'
        AND exists_account.deleted = 0
  )
ORDER BY user.id DESC
LIMIT 1;

INSERT IGNORE INTO sys_account_role (app_id, account_id, role_id, created_at, deleted)
SELECT app.id, account.id, role.id, CURRENT_TIMESTAMP(3), 0
FROM sys_app app
JOIN sys_account account
    ON account.app_id = app.id
    AND account.login_account = 'merchant'
    AND account.deleted = 0
JOIN sys_role role
    ON role.app_id = app.id
    AND role.role_code IN ('MERCHANT_ADMIN_200045', 'MERCHANT_ADMIN')
    AND role.deleted = 0
WHERE app.app_code = 'MERCHANT'
  AND app.deleted = 0
ORDER BY CASE role.role_code WHEN 'MERCHANT_ADMIN_200045' THEN 0 ELSE 1 END
LIMIT 1;

INSERT IGNORE INTO sys_merchant_user (merchant_info_id, merchant_id, user_id, account_id, login_account, real_name,
                                      status, deleted)
SELECT merchant.id,
       merchant.merchant_id,
       account.user_id,
       account.id,
       account.login_account,
       user.real_name,
       account.status,
       0
FROM sys_account account
JOIN sys_user user ON user.id = account.user_id AND user.deleted = 0
JOIN base_merchant_info merchant ON merchant.merchant_id = account.merchant_id AND merchant.deleted = 0
JOIN sys_app app ON app.id = account.app_id AND app.app_code = 'MERCHANT' AND app.deleted = 0
WHERE account.login_account = 'merchant'
  AND account.deleted = 0;

INSERT IGNORE INTO sys_merchant_user_role (app_id, merchant_info_id, merchant_user_id, role_id, deleted)
SELECT account_role.app_id,
       merchant_user.merchant_info_id,
       merchant_user.id,
       account_role.role_id,
       0
FROM sys_merchant_user merchant_user
JOIN sys_account_role account_role
    ON account_role.account_id = merchant_user.account_id
    AND account_role.app_id = 2
    AND account_role.deleted = 0
WHERE merchant_user.login_account = 'merchant'
  AND merchant_user.deleted = 0;
