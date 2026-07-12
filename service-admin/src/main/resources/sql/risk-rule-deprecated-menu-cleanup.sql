-- 清理已废弃的内风控规则菜单。
-- 发卡行国家限定已由“发卡行国家/地区黑名单”覆盖；卡 BIN 交易规则已由 AML/黑白名单/基础卡 BIN 能力覆盖。
-- 执行后请刷新管理端菜单缓存或重新登录。

SET NAMES utf8mb4;

UPDATE sys_role_permission rp
JOIN sys_permission p ON p.app_id = rp.app_id AND p.id = rp.permission_id
SET rp.deleted = rp.id
WHERE p.app_id = 1
  AND p.deleted = 0
  AND rp.deleted = 0
  AND (
      p.permission_code LIKE 'risk:rule:issuerCountry:%'
      OR p.permission_code LIKE 'risk:rule:cardBin:%'
      OR p.permission_name IN ('发卡行国家限定', '卡BIN交易规则')
      OR p.resource_path IN ('/risk/rule/issuer-country', '/risk/rule/card-bin')
  );

UPDATE sys_permission p
SET p.deleted = p.id,
    p.status = 0
WHERE p.app_id = 1
  AND p.deleted = 0
  AND (
      p.permission_code LIKE 'risk:rule:issuerCountry:%'
      OR p.permission_code LIKE 'risk:rule:cardBin:%'
      OR p.permission_name IN ('发卡行国家限定', '卡BIN交易规则')
      OR p.resource_path IN ('/risk/rule/issuer-country', '/risk/rule/card-bin')
  );

UPDATE sys_role_menu rm
JOIN sys_menu m ON m.app_id = rm.app_id AND m.id = rm.menu_id
SET rm.deleted = rm.id
WHERE m.app_id = 1
  AND m.deleted = 0
  AND rm.deleted = 0
  AND (
      m.menu_code LIKE 'risk_rule_issuer_country%'
      OR m.menu_code LIKE 'risk_rule_card_bin%'
      OR m.menu_name IN ('发卡行国家限定', '卡BIN交易规则')
      OR m.route_path IN ('/risk/rule/issuer-country', '/risk/rule/card-bin')
      OR m.permission_code LIKE 'risk:rule:issuerCountry:%'
      OR m.permission_code LIKE 'risk:rule:cardBin:%'
  );

UPDATE sys_menu m
SET m.deleted = m.id,
    m.status = 0,
    m.visible = 0
WHERE m.app_id = 1
  AND m.deleted = 0
  AND (
      m.menu_code LIKE 'risk_rule_issuer_country%'
      OR m.menu_code LIKE 'risk_rule_card_bin%'
      OR m.menu_name IN ('发卡行国家限定', '卡BIN交易规则')
      OR m.route_path IN ('/risk/rule/issuer-country', '/risk/rule/card-bin')
      OR m.permission_code LIKE 'risk:rule:issuerCountry:%'
      OR m.permission_code LIKE 'risk:rule:cardBin:%'
  );

DROP TABLE IF EXISTS risk_rule_issuer_country;
DROP TABLE IF EXISTS risk_rule_card_bin;
