SET NAMES utf8mb4;

-- 商户系统支付接入管理菜单及权限显示名称迁移。
-- 仅调整展示名称，不修改菜单编码、路由、权限编码或接口资源。

START TRANSACTION;

UPDATE sys_menu menu
JOIN sys_app app ON app.id = menu.app_id
                AND app.app_code = 'MERCHANT'
                AND app.deleted = 0
SET menu.menu_name = CASE menu.menu_code
        WHEN 'merchant_access_config_catalog_v1' THEN '支付接入管理'
        WHEN 'merchant_source_url_v1' THEN '店铺网址'
        WHEN 'merchant_ip_whitelist_v1' THEN 'IP 白名单'
        ELSE menu.menu_name
    END,
    menu.updated_at = CURRENT_TIMESTAMP(3)
WHERE menu.menu_code IN (
    'merchant_access_config_catalog_v1',
    'merchant_source_url_v1',
    'merchant_ip_whitelist_v1'
)
  AND menu.deleted = 0;

UPDATE sys_permission permission
JOIN sys_app app ON app.id = permission.app_id
                AND app.app_code = 'MERCHANT'
                AND app.deleted = 0
SET permission.permission_name = CASE permission.permission_code
        WHEN 'merchant:access-config:source-url:list' THEN '店铺网址查询'
        WHEN 'merchant:access-config:source-url:detail' THEN '店铺网址详情'
        WHEN 'merchant:access-config:source-url:submit' THEN '店铺网址提交'
        WHEN 'merchant:access-config:ip-whitelist:list' THEN 'IP 白名单查询'
        WHEN 'merchant:access-config:ip-whitelist:detail' THEN 'IP 白名单详情'
        WHEN 'merchant:access-config:ip-whitelist:submit' THEN 'IP 白名单提交'
        ELSE permission.permission_name
    END,
    permission.updated_at = CURRENT_TIMESTAMP(3)
WHERE permission.permission_code IN (
    'merchant:access-config:source-url:list',
    'merchant:access-config:source-url:detail',
    'merchant:access-config:source-url:submit',
    'merchant:access-config:ip-whitelist:list',
    'merchant:access-config:ip-whitelist:detail',
    'merchant:access-config:ip-whitelist:submit'
)
  AND permission.deleted = 0;

UPDATE msg_email_template template
SET template.template_name = CASE template.locale
        WHEN 'zh-CN' THEN REPLACE(REPLACE(template.template_name, '商户来源网址', '店铺网址'), '来源网址', '店铺网址')
        WHEN 'en-US' THEN REPLACE(REPLACE(template.template_name, 'Merchant Source URL', 'Store Website'), 'Source URL', 'Store Website')
        ELSE template.template_name
    END,
    template.subject_template = CASE template.locale
        WHEN 'zh-CN' THEN REPLACE(REPLACE(template.subject_template, '商户来源网址', '店铺网址'), '来源网址', '店铺网址')
        WHEN 'en-US' THEN REPLACE(REPLACE(template.subject_template, 'Source URL', 'Store website'), 'source URL', 'store website')
        ELSE template.subject_template
    END,
    template.content_template = CASE template.locale
        WHEN 'zh-CN' THEN REPLACE(REPLACE(template.content_template, '商户来源网址', '店铺网址'), '来源网址', '店铺网址')
        WHEN 'en-US' THEN REPLACE(REPLACE(template.content_template, 'Source URL', 'Store website'), 'source URL', 'store website')
        ELSE template.content_template
    END,
    template.update_by = 'system',
    template.update_time = CURRENT_TIMESTAMP(3)
WHERE template.template_code IN ('MERCHANT_SOURCE_URL_APPROVED', 'MERCHANT_SOURCE_URL_REJECTED')
  AND template.locale IN ('zh-CN', 'en-US')
  AND template.deleted = 0;

COMMIT;
