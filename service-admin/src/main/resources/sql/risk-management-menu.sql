SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

UPDATE sys_role_permission rp
JOIN sys_permission p ON p.id = rp.permission_id
JOIN sys_menu m ON m.id = p.menu_id
SET rp.deleted = rp.id
WHERE p.app_id = 1
  AND p.permission_code LIKE 'risk:%'
  AND m.id BETWEEN 600 AND 721
  AND rp.deleted = 0;

UPDATE sys_permission p
JOIN sys_menu m ON m.id = p.menu_id
SET p.deleted = p.id, p.status = 0
WHERE p.app_id = 1
  AND p.permission_code LIKE 'risk:%'
  AND m.id BETWEEN 600 AND 721
  AND p.deleted = 0;

UPDATE sys_role_menu rm
JOIN sys_menu m ON m.id = rm.menu_id
SET rm.deleted = rm.id
WHERE m.app_id = 1
  AND m.menu_code LIKE 'risk_%'
  AND m.id BETWEEN 600 AND 721
  AND rm.deleted = 0;

UPDATE sys_menu
SET deleted = id, status = 0
WHERE app_id = 1
  AND menu_code LIKE 'risk_%'
  AND id BETWEEN 600 AND 721
  AND deleted = 0;

INSERT INTO sys_menu (app_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path, permission_code, icon, visible, sort_no, status, deleted)
VALUES (1, 0, 'risk_center', '收单风控', 'CATALOG', '/risk', NULL, 'risk:access', 'WarningFilled', 1, 60, 1, 0)
ON DUPLICATE KEY UPDATE menu_name=VALUES(menu_name), menu_type=VALUES(menu_type), route_path=VALUES(route_path), component_path=VALUES(component_path), permission_code=VALUES(permission_code), icon=VALUES(icon), visible=VALUES(visible), sort_no=VALUES(sort_no), status=VALUES(status), deleted=0;

INSERT INTO sys_menu (app_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path, permission_code, icon, visible, sort_no, status, deleted)
SELECT 1, root.id, item.menu_code, item.menu_name, 'CATALOG', item.route_path, NULL, item.permission_code, item.icon, 1, item.sort_no, 1, 0
FROM sys_menu root
JOIN (
    SELECT 'risk_dashboard_catalog' menu_code, '风险工作台' menu_name, '/risk/dashboard' route_path, 'risk:dashboard:list' permission_code, 'DataAnalysis' icon, 10 sort_no
    UNION ALL SELECT 'risk_aml_catalog', 'AML强制拦截', '/risk/aml', 'risk:aml:list', 'Lock', 20
    UNION ALL SELECT 'risk_black_catalog', '黑名单管理', '/risk/blacklist', 'risk:blacklist:list', 'CircleCloseFilled', 30
    UNION ALL SELECT 'risk_trade_black_catalog', '系统交易加黑', '/risk/trade-black', 'risk:tradeBlack:list', 'DocumentAdd', 40
    UNION ALL SELECT 'risk_white_catalog', '白名单管理', '/risk/whitelist', 'risk:whitelist:list', 'CircleCheckFilled', 50
    UNION ALL SELECT 'risk_rule_catalog', '内风控规则管理', '/risk/rule', 'risk:rule:list', 'SetUp', 60
    UNION ALL SELECT 'risk_record_catalog', '风控记录', '/risk/record', 'risk:record:list', 'Document', 70
) item ON 1 = 1
WHERE root.app_id = 1 AND root.menu_code = 'risk_center' AND root.deleted = 0
ON DUPLICATE KEY UPDATE parent_id=VALUES(parent_id), menu_name=VALUES(menu_name), menu_type=VALUES(menu_type), route_path=VALUES(route_path), component_path=VALUES(component_path), permission_code=VALUES(permission_code), icon=VALUES(icon), visible=VALUES(visible), sort_no=VALUES(sort_no), status=VALUES(status), deleted=0;

INSERT INTO sys_menu (app_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path, permission_code, icon, visible, sort_no, status, deleted)
SELECT 1, parent.id, item.menu_code, item.menu_name, 'MENU', item.route_path, item.component_path, item.permission_code, item.icon, 1, item.sort_no, 1, 0
FROM sys_menu parent
JOIN (
    SELECT 'risk_dashboard_catalog' parent_code, 'risk_dashboard_overview' menu_code, '风控总览' menu_name, '/risk/dashboard/overview' route_path, 'risk/dashboard/overview' component_path, 'risk:dashboard:overview:list' permission_code, 'DataBoard' icon, 11 sort_no
    UNION ALL SELECT 'risk_dashboard_catalog', 'risk_dashboard_today_events', '今日风险事件', '/risk/dashboard/today-events', 'risk/dashboard/today-events', 'risk:dashboard:todayEvents:list', 'Bell', 12
    UNION ALL SELECT 'risk_dashboard_catalog', 'risk_dashboard_merchant_ranking', '高风险商户排行', '/risk/dashboard/merchant-ranking', 'risk/dashboard/merchant-ranking', 'risk:dashboard:merchantRanking:list', 'TrendCharts', 13
    UNION ALL SELECT 'risk_dashboard_catalog', 'risk_dashboard_config_changes', '风控配置变更', '/risk/dashboard/config-changes', 'risk/dashboard/config-changes', 'risk:dashboard:configChanges:list', 'DocumentChecked', 14
    UNION ALL SELECT 'risk_aml_catalog', 'risk_aml_overview', 'AML名单总览', '/risk/aml/overview', 'risk/overview', 'risk:aml:overview:list', 'DataBoard', 21
    UNION ALL SELECT 'risk_aml_catalog', 'risk_aml_card', '卡号/卡指纹AML', '/risk/aml/card', 'risk/list', 'risk:aml:card:list', 'CreditCard', 22
    UNION ALL SELECT 'risk_aml_catalog', 'risk_aml_card_bin', '卡BIN/区间AML', '/risk/aml/card-bin', 'risk/list', 'risk:aml:cardBin:list', 'Tickets', 23
    UNION ALL SELECT 'risk_aml_catalog', 'risk_aml_ip', 'IP地址/区间AML', '/risk/aml/ip', 'risk/list', 'risk:aml:ip:list', 'Position', 24
    UNION ALL SELECT 'risk_aml_catalog', 'risk_aml_country', '国家/地区AML', '/risk/aml/country', 'risk/list', 'risk:aml:country:list', 'Location', 25
    UNION ALL SELECT 'risk_aml_catalog', 'risk_aml_email', '邮箱/域名AML', '/risk/aml/email', 'risk/list', 'risk:aml:email:list', 'Message', 26
    UNION ALL SELECT 'risk_aml_catalog', 'risk_aml_phone', '手机号AML', '/risk/aml/phone', 'risk/list', 'risk:aml:phone:list', 'Iphone', 27
    UNION ALL SELECT 'risk_aml_catalog', 'risk_aml_cardholder_name', '持卡人姓名AML', '/risk/aml/cardholder-name', 'risk/list', 'risk:aml:cardholderName:list', 'User', 28
    UNION ALL SELECT 'risk_aml_catalog', 'risk_aml_source_url', '来源网址AML', '/risk/aml/source-url', 'risk/list', 'risk:aml:sourceUrl:list', 'Link', 29
    UNION ALL SELECT 'risk_aml_catalog', 'risk_aml_hit_record', 'AML命中记录', '/risk/aml/hit-record', 'risk/record', 'risk:record:evaluation:list', 'Document', 30
    UNION ALL SELECT 'risk_black_catalog', 'risk_black_overview', '黑名单总览', '/risk/blacklist/overview', 'risk/overview', 'risk:blacklist:overview:list', 'DataBoard', 31
    UNION ALL SELECT 'risk_black_catalog', 'risk_black_card_no', '卡号黑名单', '/risk/blacklist/card-no', 'risk/list', 'risk:blacklist:cardNo:list', 'CreditCard', 32
    UNION ALL SELECT 'risk_black_catalog', 'risk_black_card_fingerprint', '卡指纹黑名单', '/risk/blacklist/card-fingerprint', 'risk/list', 'risk:blacklist:cardFingerprint:list', 'FingerPrint', 33
    UNION ALL SELECT 'risk_black_catalog', 'risk_black_card_bin', '卡BIN/区间黑名单', '/risk/blacklist/card-bin', 'risk/list', 'risk:blacklist:cardBin:list', 'Tickets', 34
    UNION ALL SELECT 'risk_black_catalog', 'risk_black_cardholder_name', '持卡人姓名黑名单', '/risk/blacklist/cardholder-name', 'risk/list', 'risk:blacklist:cardholderName:list', 'User', 35
    UNION ALL SELECT 'risk_black_catalog', 'risk_black_phone', '电话号码黑名单', '/risk/blacklist/phone', 'risk/list', 'risk:blacklist:phone:list', 'Iphone', 36
    UNION ALL SELECT 'risk_black_catalog', 'risk_black_ip', 'IP地址/区间黑名单', '/risk/blacklist/ip', 'risk/list', 'risk:blacklist:ip:list', 'Position', 37
    UNION ALL SELECT 'risk_black_catalog', 'risk_black_region', '高风险区域黑名单', '/risk/blacklist/region', 'risk/list', 'risk:blacklist:region:list', 'MapLocation', 38
    UNION ALL SELECT 'risk_black_catalog', 'risk_black_email', '邮箱地址黑名单', '/risk/blacklist/email', 'risk/list', 'risk:blacklist:email:list', 'Message', 39
    UNION ALL SELECT 'risk_black_catalog', 'risk_black_email_username', '邮箱用户名黑名单', '/risk/blacklist/email-username', 'risk/list', 'risk:blacklist:emailUsername:list', 'UserFilled', 40
    UNION ALL SELECT 'risk_black_catalog', 'risk_black_email_domain', '邮箱域名黑名单', '/risk/blacklist/email-domain', 'risk/list', 'risk:blacklist:emailDomain:list', 'Connection', 41
    UNION ALL SELECT 'risk_black_catalog', 'risk_black_billing_address', '账单地址黑名单', '/risk/blacklist/billing-address', 'risk/list', 'risk:blacklist:billingAddress:list', 'House', 42
    UNION ALL SELECT 'risk_black_catalog', 'risk_black_billing_zip', '账单邮编黑名单', '/risk/blacklist/billing-zip', 'risk/list', 'risk:blacklist:billingZip:list', 'Postcard', 43
    UNION ALL SELECT 'risk_black_catalog', 'risk_black_billing_country', '账单国家/地区黑名单', '/risk/blacklist/billing-country', 'risk/list', 'risk:blacklist:billingCountry:list', 'Location', 44
    UNION ALL SELECT 'risk_black_catalog', 'risk_black_shipping_address', '收货地址黑名单', '/risk/blacklist/shipping-address', 'risk/list', 'risk:blacklist:shippingAddress:list', 'Van', 45
    UNION ALL SELECT 'risk_black_catalog', 'risk_black_shipping_zip', '收货邮编黑名单', '/risk/blacklist/shipping-zip', 'risk/list', 'risk:blacklist:shippingZip:list', 'Postcard', 46
    UNION ALL SELECT 'risk_black_catalog', 'risk_black_shipping_country', '收货国家/地区黑名单', '/risk/blacklist/shipping-country', 'risk/list', 'risk:blacklist:shippingCountry:list', 'Location', 47
    UNION ALL SELECT 'risk_black_catalog', 'risk_black_issuer_country', '发卡行国家/地区黑名单', '/risk/blacklist/issuer-country', 'risk/list', 'risk:blacklist:issuerCountry:list', 'LocationFilled', 48
    UNION ALL SELECT 'risk_black_catalog', 'risk_black_device_fingerprint', '设备指纹黑名单', '/risk/blacklist/device-fingerprint', 'risk/list', 'risk:blacklist:deviceFingerprint:list', 'Monitor', 49
    UNION ALL SELECT 'risk_trade_black_catalog', 'risk_trade_black_system', '系统交易加黑', '/risk/trade-black/system', 'risk/trade-black', 'risk:tradeBlack:system:list', 'DocumentAdd', 41
    UNION ALL SELECT 'risk_white_catalog', 'risk_white_overview', '白名单总览', '/risk/whitelist/overview', 'risk/overview', 'risk:whitelist:overview:list', 'DataBoard', 51
    UNION ALL SELECT 'risk_white_catalog', 'risk_white_merchant', '商户白名单', '/risk/whitelist/merchant', 'risk/list', 'risk:whitelist:merchant:list', 'Shop', 52
    UNION ALL SELECT 'risk_white_catalog', 'risk_white_card_no', '卡号白名单', '/risk/whitelist/card-no', 'risk/list', 'risk:whitelist:cardNo:list', 'CreditCard', 53
    UNION ALL SELECT 'risk_white_catalog', 'risk_white_card_fingerprint', '卡指纹白名单', '/risk/whitelist/card-fingerprint', 'risk/list', 'risk:whitelist:cardFingerprint:list', 'FingerPrint', 54
    UNION ALL SELECT 'risk_white_catalog', 'risk_white_card_bin', '卡BIN/区间白名单', '/risk/whitelist/card-bin', 'risk/list', 'risk:whitelist:cardBin:list', 'Tickets', 55
    UNION ALL SELECT 'risk_white_catalog', 'risk_white_ip', 'IP地址白名单', '/risk/whitelist/ip', 'risk/list', 'risk:whitelist:ip:list', 'Position', 56
    UNION ALL SELECT 'risk_white_catalog', 'risk_white_trade_country', '交易国家/地区白名单', '/risk/whitelist/trade-country', 'risk/list', 'risk:whitelist:tradeCountry:list', 'Location', 57
    UNION ALL SELECT 'risk_white_catalog', 'risk_white_issuer_country', '发卡行国家/地区白名单', '/risk/whitelist/issuer-country', 'risk/list', 'risk:whitelist:issuerCountry:list', 'LocationFilled', 58
    UNION ALL SELECT 'risk_white_catalog', 'risk_white_email', '邮箱地址白名单', '/risk/whitelist/email', 'risk/list', 'risk:whitelist:email:list', 'Message', 59
    UNION ALL SELECT 'risk_white_catalog', 'risk_white_email_domain', '邮箱域名白名单', '/risk/whitelist/email-domain', 'risk/list', 'risk:whitelist:emailDomain:list', 'Connection', 60
    UNION ALL SELECT 'risk_white_catalog', 'risk_white_phone', '手机号白名单', '/risk/whitelist/phone', 'risk/list', 'risk:whitelist:phone:list', 'Iphone', 61
    UNION ALL SELECT 'risk_white_catalog', 'risk_white_customer_id', 'Customer ID 白名单', '/risk/whitelist/customer-id', 'risk/list', 'risk:whitelist:customerId:list', 'UserFilled', 62
    UNION ALL SELECT 'risk_white_catalog', 'risk_white_device_fingerprint', '设备指纹白名单', '/risk/whitelist/device-fingerprint', 'risk/list', 'risk:whitelist:deviceFingerprint:list', 'Monitor', 63
    UNION ALL SELECT 'risk_rule_catalog', 'risk_rule_overview', '内风控规则总览', '/risk/rule/overview', 'risk/overview', 'risk:rule:overview:list', 'DataBoard', 61
    UNION ALL SELECT 'risk_rule_catalog', 'risk_rule_source_url', '商户来源网址限定', '/risk/rule/source-url', 'risk/rule', 'risk:rule:sourceUrl:list', 'Link', 62
    UNION ALL SELECT 'risk_rule_catalog', 'risk_rule_merchant_limit', '商户交易限额管理', '/risk/rule/merchant-limit', 'risk/rule', 'risk:rule:merchantLimit:list', 'Money', 63
    UNION ALL SELECT 'risk_rule_catalog', 'risk_rule_frequency', '交易频率限定', '/risk/rule/frequency', 'risk/rule', 'risk:rule:frequency:list', 'Timer', 64
    UNION ALL SELECT 'risk_rule_catalog', 'risk_rule_trade_country', '商户交易国家限定', '/risk/rule/trade-country', 'risk/rule', 'risk:rule:tradeCountry:list', 'Location', 65
    UNION ALL SELECT 'risk_rule_catalog', 'risk_rule_issuer_country', '发卡行国家限定', '/risk/rule/issuer-country', 'risk/rule', 'risk:rule:issuerCountry:list', 'LocationFilled', 66
    UNION ALL SELECT 'risk_rule_catalog', 'risk_rule_card_bin', '卡BIN交易规则', '/risk/rule/card-bin', 'risk/rule', 'risk:rule:cardBin:list', 'Tickets', 67
    UNION ALL SELECT 'risk_rule_catalog', 'risk_rule_3ds', '3DS规则管理', '/risk/rule/3ds', 'risk/rule', 'risk:rule:threeDs:list', 'Unlock', 68
    UNION ALL SELECT 'risk_record_catalog', 'risk_record_evaluation', '风控记录明细', '/risk/record/evaluation', 'risk/record', 'risk:record:evaluation:list', 'Document', 71
) item ON item.parent_code = parent.menu_code
WHERE parent.app_id = 1 AND parent.deleted = 0
ON DUPLICATE KEY UPDATE parent_id=VALUES(parent_id), menu_name=VALUES(menu_name), menu_type=VALUES(menu_type), route_path=VALUES(route_path), component_path=VALUES(component_path), permission_code=VALUES(permission_code), icon=VALUES(icon), visible=VALUES(visible), sort_no=VALUES(sort_no), status=VALUES(status), deleted=0;

INSERT INTO sys_menu (app_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path, permission_code, icon, visible, sort_no, status, deleted)
SELECT 1, menu.id, CONCAT(menu.menu_code, '_', action.action_code), CONCAT(menu.menu_name, action.action_name), 'BUTTON', NULL, NULL, REPLACE(menu.permission_code, ':list', CONCAT(':', action.action_code)), NULL, 0, action.sort_no, 1, 0
FROM sys_menu menu
JOIN (
    SELECT 'detail' action_code, '详情' action_name, 101 sort_no UNION ALL
    SELECT 'add', '新增', 102 UNION ALL
    SELECT 'edit', '编辑', 103 UNION ALL
    SELECT 'remove', '删除', 104 UNION ALL
    SELECT 'status', '状态', 105 UNION ALL
    SELECT 'import', '导入', 106 UNION ALL
    SELECT 'export', '导出', 107 UNION ALL
    SELECT 'template', '模板', 108
) action ON 1 = 1
WHERE menu.app_id = 1
  AND menu.deleted = 0
  AND menu.menu_type = 'MENU'
  AND menu.menu_code REGEXP '^risk_(aml|black|white|rule)_'
  AND menu.menu_code NOT LIKE '%_overview'
  AND menu.menu_code <> 'risk_aml_hit_record'
ON DUPLICATE KEY UPDATE parent_id=VALUES(parent_id), menu_name=VALUES(menu_name), menu_type=VALUES(menu_type), permission_code=VALUES(permission_code), visible=VALUES(visible), sort_no=VALUES(sort_no), status=VALUES(status), deleted=0;

INSERT INTO sys_menu (app_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path, permission_code, icon, visible, sort_no, status, deleted)
SELECT 1, menu.id, button.menu_code, button.menu_name, 'BUTTON', NULL, NULL, button.permission_code, NULL, 0, button.sort_no, 1, 0
FROM sys_menu menu
JOIN (
    SELECT 'risk_trade_black_system' parent_code, 'risk_trade_black_system_add' menu_code, '系统交易加黑新增' menu_name, 'risk:tradeBlack:system:add' permission_code, 101 sort_no
    UNION ALL SELECT 'risk_trade_black_system', 'risk_trade_black_system_release', '系统交易加黑解除', 'risk:tradeBlack:system:release', 102
    UNION ALL SELECT 'risk_trade_black_system', 'risk_trade_black_system_batch_add', '系统交易批量加黑', 'risk:tradeBlack:system:batchAdd', 103
    UNION ALL SELECT 'risk_record_evaluation', 'risk_record_evaluation_detail', '风控记录命中明细', 'risk:record:evaluation:detail', 101
) button ON button.parent_code = menu.menu_code
WHERE menu.app_id = 1 AND menu.deleted = 0
ON DUPLICATE KEY UPDATE parent_id=VALUES(parent_id), menu_name=VALUES(menu_name), menu_type=VALUES(menu_type), permission_code=VALUES(permission_code), visible=VALUES(visible), sort_no=VALUES(sort_no), status=VALUES(status), deleted=0;

INSERT INTO sys_permission (app_id, menu_id, permission_code, permission_name, permission_type, resource_method, resource_path, description, status, deleted)
SELECT 1, menu.id, menu.permission_code, menu.menu_name, menu.menu_type, '*', COALESCE(menu.route_path, '/admin/risk/**'), '收单风控菜单与按钮权限', 1, 0
FROM sys_menu menu
WHERE menu.app_id = 1 AND menu.deleted = 0 AND menu.permission_code LIKE 'risk:%'
ON DUPLICATE KEY UPDATE menu_id=VALUES(menu_id), permission_name=VALUES(permission_name), permission_type=VALUES(permission_type), resource_method=VALUES(resource_method), resource_path=VALUES(resource_path), description=VALUES(description), status=VALUES(status), deleted=0;

INSERT INTO sys_role_menu (app_id, role_id, menu_id, deleted)
SELECT 1, role.id, menu.id, 0
FROM sys_role role
JOIN sys_menu menu ON menu.app_id = role.app_id
WHERE role.app_id = 1 AND role.role_code IN ('ADMIN_OPERATOR', 'ADMIN') AND role.deleted = 0 AND menu.permission_code LIKE 'risk:%' AND menu.deleted = 0
ON DUPLICATE KEY UPDATE deleted = 0;

INSERT INTO sys_role_permission (app_id, role_id, permission_id, deleted)
SELECT 1, role.id, permission.id, 0
FROM sys_role role
JOIN sys_permission permission ON permission.app_id = role.app_id
WHERE role.app_id = 1 AND role.role_code IN ('ADMIN_OPERATOR', 'ADMIN') AND role.deleted = 0 AND permission.permission_code LIKE 'risk:%' AND permission.deleted = 0
ON DUPLICATE KEY UPDATE deleted = 0;

SET FOREIGN_KEY_CHECKS = 1;
