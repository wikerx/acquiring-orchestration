-- REQ-001 双端菜单与独立动作权限，不向现有角色自动分配。
-- 正式环境先核对父菜单及 HMAC 调用范围。新增任务默认禁用。
-- 中文种子数据按 UTF-8 解码，执行时同时指定 mysql --default-character-set=utf8mb4。
SET NAMES utf8mb4;

START TRANSACTION;

INSERT INTO sys_menu (app_id,parent_id,menu_code,menu_name,menu_type,route_path,component_path,permission_code,icon,visible,keep_alive,external_link,sort_no,status,deleted)
SELECT app.id,parent.id,'merchant_payment_methods_v1','支付方式','MENU',target.route_path,target.component_path,
       'merchant:payment-method:view','CreditCard',1,0,0,26,1,0
FROM (
    SELECT 'ADMIN' AS app_code,'admin_merchant_catalog_v3' AS parent_code,'/merchant/payment-methods' AS route_path,
           'merchant/payment-methods/index' AS component_path
    UNION ALL SELECT 'MERCHANT','merchant_info_catalog_v1','/merchant-info/payment-methods','merchant-info/payment-methods/index'
) target
JOIN sys_app app ON app.app_code=target.app_code AND app.deleted=0
JOIN sys_menu parent ON parent.app_id=app.id AND parent.menu_code=target.parent_code AND parent.deleted=0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu existing WHERE existing.app_id=app.id
                  AND existing.menu_code='merchant_payment_methods_v1' AND existing.deleted=0);

INSERT INTO sys_menu (app_id,parent_id,menu_code,menu_name,menu_type,permission_code,visible,keep_alive,external_link,sort_no,status,deleted)
SELECT app.id,parent.id,target.menu_code,target.label,'BUTTON',target.code,0,0,0,target.sort_no,1,0
FROM (
    SELECT 'ADMIN' AS app_code, 'merchant:payment-method:apply' AS code, 'merchant_payment_method_apply_v1' AS menu_code, '代商户申请支付方式' AS label, 'POST' AS method, '/admin/merchants/*/payment-method-applications' AS path, 1 AS sort_no
    UNION ALL SELECT 'ADMIN' AS app_code, 'merchant:payment-method:review' AS code, 'merchant_payment_method_review_v1' AS menu_code, '逐项审批支付方式' AS label, 'POST' AS method, '/admin/merchant-payment-application-items/*/decisions' AS path, 2 AS sort_no
    UNION ALL SELECT 'ADMIN' AS app_code, 'merchant:payment-method:suspend' AS code, 'merchant_payment_method_suspend_v1' AS menu_code, '暂停商户支付方式' AS label, 'POST' AS method, '/admin/merchants/*/payment-methods/*/suspend' AS path, 3 AS sort_no
    UNION ALL SELECT 'ADMIN' AS app_code, 'merchant:payment-method:restore' AS code, 'merchant_payment_method_restore_v1' AS menu_code, '恢复商户支付方式' AS label, 'POST' AS method, '/admin/merchants/*/payment-methods/*/restore' AS path, 4 AS sort_no
    UNION ALL SELECT 'ADMIN' AS app_code, 'merchant:payment-method:terminate' AS code, 'merchant_payment_method_terminate_v1' AS menu_code, '终止商户支付方式' AS label, 'POST' AS method, '/admin/merchants/*/payment-methods/*/terminate' AS path, 5 AS sort_no
    UNION ALL SELECT 'ADMIN' AS app_code, 'merchant:payment-method:validity' AS code, 'merchant_payment_method_validity_v1' AS menu_code, '调整支付方式授权有效期' AS label, 'POST' AS method, '/admin/merchants/*/payment-methods/*/validity' AS path, 6 AS sort_no
    UNION ALL SELECT 'ADMIN' AS app_code, 'merchant:payment-method:history' AS code, 'merchant_payment_method_history_v1' AS menu_code, '查看支付方式授权历史' AS label, 'GET' AS method, '/admin/merchants/*/payment-methods/*/history' AS path, 7 AS sort_no
    UNION ALL SELECT 'ADMIN' AS app_code, 'merchant:payment-method:baseline:submit' AS code, 'merchant_payment_method_baseline_submit_v1' AS menu_code, '登记历史基线' AS label, 'POST' AS method, '/admin/merchants/*/payment-method-history-baselines' AS path, 8 AS sort_no
    UNION ALL SELECT 'ADMIN' AS app_code, 'merchant:payment-method:baseline:approve' AS code, 'merchant_payment_method_baseline_approve_v1' AS menu_code, '复核历史基线' AS label, 'POST' AS method, '/admin/merchants/*/payment-method-history-baselines/*/approve' AS path, 9 AS sort_no
    UNION ALL SELECT 'MERCHANT' AS app_code, 'merchant:payment-method:apply' AS code, 'merchant_payment_method_apply_v1' AS menu_code, '申请支付方式' AS label, 'POST' AS method, '/merchant/payment-method-applications' AS path, 10 AS sort_no
    UNION ALL SELECT 'MERCHANT' AS app_code, 'merchant:payment-method:application:view' AS code, 'merchant_payment_method_application_view_v1' AS menu_code, '查看支付方式申请' AS label, 'POST' AS method, '/merchant/payment-method-applications/search' AS path, 11 AS sort_no
    UNION ALL SELECT 'MERCHANT' AS app_code, 'merchant:payment-method:resubmit' AS code, 'merchant_payment_method_resubmit_v1' AS menu_code, '补充支付方式申请' AS label, 'POST' AS method, '/merchant/payment-method-application-items/*/resubmit' AS path, 12 AS sort_no
    UNION ALL SELECT 'MERCHANT' AS app_code, 'merchant:payment-method:withdraw' AS code, 'merchant_payment_method_withdraw_v1' AS menu_code, '撤回未批准的支付方式申请' AS label, 'POST' AS method, '/merchant/payment-method-application-items/*/withdraw' AS path, 13 AS sort_no
) target
JOIN sys_app app ON app.app_code=target.app_code AND app.deleted=0
JOIN sys_menu parent ON parent.app_id=app.id AND parent.menu_code='merchant_payment_methods_v1' AND parent.deleted=0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu q WHERE q.app_id=app.id AND q.menu_code=target.menu_code AND q.deleted=0);

INSERT INTO sys_permission (app_id,menu_id,permission_code,permission_name,permission_type,resource_method,resource_path,status,deleted)
SELECT app.id,m.id,target.code,target.label,m.menu_type,target.method,target.path,1,0
FROM (
    SELECT 'ADMIN' AS app_code, 'merchant:payment-method:view' AS code, 'merchant_payment_methods_v1' AS menu_code, '查看商户支付方式' AS label, 'POST' AS method, '/admin/merchants/*/payment-methods/search' AS path, 1 AS sort_no
    UNION ALL SELECT 'ADMIN' AS app_code, 'merchant:payment-method:apply' AS code, 'merchant_payment_method_apply_v1' AS menu_code, '代商户申请支付方式' AS label, 'POST' AS method, '/admin/merchants/*/payment-method-applications' AS path, 2 AS sort_no
    UNION ALL SELECT 'ADMIN' AS app_code, 'merchant:payment-method:review' AS code, 'merchant_payment_method_review_v1' AS menu_code, '逐项审批支付方式' AS label, 'POST' AS method, '/admin/merchant-payment-application-items/*/decisions' AS path, 3 AS sort_no
    UNION ALL SELECT 'ADMIN' AS app_code, 'merchant:payment-method:suspend' AS code, 'merchant_payment_method_suspend_v1' AS menu_code, '暂停商户支付方式' AS label, 'POST' AS method, '/admin/merchants/*/payment-methods/*/suspend' AS path, 4 AS sort_no
    UNION ALL SELECT 'ADMIN' AS app_code, 'merchant:payment-method:restore' AS code, 'merchant_payment_method_restore_v1' AS menu_code, '恢复商户支付方式' AS label, 'POST' AS method, '/admin/merchants/*/payment-methods/*/restore' AS path, 5 AS sort_no
    UNION ALL SELECT 'ADMIN' AS app_code, 'merchant:payment-method:terminate' AS code, 'merchant_payment_method_terminate_v1' AS menu_code, '终止商户支付方式' AS label, 'POST' AS method, '/admin/merchants/*/payment-methods/*/terminate' AS path, 6 AS sort_no
    UNION ALL SELECT 'ADMIN' AS app_code, 'merchant:payment-method:validity' AS code, 'merchant_payment_method_validity_v1' AS menu_code, '调整支付方式授权有效期' AS label, 'POST' AS method, '/admin/merchants/*/payment-methods/*/validity' AS path, 7 AS sort_no
    UNION ALL SELECT 'ADMIN' AS app_code, 'merchant:payment-method:history' AS code, 'merchant_payment_method_history_v1' AS menu_code, '查看支付方式授权历史' AS label, 'GET' AS method, '/admin/merchants/*/payment-methods/*/history' AS path, 8 AS sort_no
    UNION ALL SELECT 'ADMIN' AS app_code, 'merchant:payment-method:baseline:submit' AS code, 'merchant_payment_method_baseline_submit_v1' AS menu_code, '登记历史基线' AS label, 'POST' AS method, '/admin/merchants/*/payment-method-history-baselines' AS path, 9 AS sort_no
    UNION ALL SELECT 'ADMIN' AS app_code, 'merchant:payment-method:baseline:approve' AS code, 'merchant_payment_method_baseline_approve_v1' AS menu_code, '复核历史基线' AS label, 'POST' AS method, '/admin/merchants/*/payment-method-history-baselines/*/approve' AS path, 10 AS sort_no
    UNION ALL SELECT 'MERCHANT' AS app_code, 'merchant:payment-method:view' AS code, 'merchant_payment_methods_v1' AS menu_code, '查看支付方式' AS label, 'POST' AS method, '/merchant/payment-methods/search' AS path, 11 AS sort_no
    UNION ALL SELECT 'MERCHANT' AS app_code, 'merchant:payment-method:apply' AS code, 'merchant_payment_method_apply_v1' AS menu_code, '申请支付方式' AS label, 'POST' AS method, '/merchant/payment-method-applications' AS path, 12 AS sort_no
    UNION ALL SELECT 'MERCHANT' AS app_code, 'merchant:payment-method:application:view' AS code, 'merchant_payment_method_application_view_v1' AS menu_code, '查看支付方式申请' AS label, 'POST' AS method, '/merchant/payment-method-applications/search' AS path, 13 AS sort_no
    UNION ALL SELECT 'MERCHANT' AS app_code, 'merchant:payment-method:resubmit' AS code, 'merchant_payment_method_resubmit_v1' AS menu_code, '补充支付方式申请' AS label, 'POST' AS method, '/merchant/payment-method-application-items/*/resubmit' AS path, 14 AS sort_no
    UNION ALL SELECT 'MERCHANT' AS app_code, 'merchant:payment-method:withdraw' AS code, 'merchant_payment_method_withdraw_v1' AS menu_code, '撤回未批准的支付方式申请' AS label, 'POST' AS method, '/merchant/payment-method-application-items/*/withdraw' AS path, 15 AS sort_no
) target
JOIN sys_app app ON app.app_code=target.app_code AND app.deleted=0
JOIN sys_menu m ON m.app_id=app.id AND m.menu_code=target.menu_code AND m.deleted=0
WHERE NOT EXISTS (SELECT 1 FROM sys_permission q WHERE q.app_id=app.id AND q.permission_code=target.code AND q.deleted=0);

INSERT INTO sys_job_task (job_code,job_name,job_group,handler_code,cron_expression,scheduler_mode,trigger_mode,execute_mode,
    route_strategy,misfire_strategy,timeout_seconds,retry_count,retry_interval_seconds,allow_concurrent,params,status,
    description,next_trigger_time,version,deleted,create_by,create_time,update_by,update_time)
SELECT target.job_code,target.job_name,'operations',target.handler_code,target.cron,'DISTRIBUTED','CRON','SYNC',
       'LOCAL','FIRE_ONCE',30,2,5,0,'{}','DISABLED','发布前保持禁用，按已核准范围启用',
       NULL,0,0,'system',CURRENT_TIMESTAMP(3),'system',CURRENT_TIMESTAMP(3)
FROM (
    SELECT 'MERCHANT_PAYMENT_CAPABILITY_BOUNDARY' AS job_code, '支付方式准入及配置复核' AS job_name,
           'merchantPaymentCapabilityBoundary' AS handler_code, '0/5 * * * * ?' AS cron
    UNION ALL SELECT 'MERCHANT_PAYMENT_CAPABILITY_DELIVERY_RECOVERY','支付方式通知恢复',
                     'merchantPaymentCapabilityDeliveryRecovery','0/10 * * * * ?'
    UNION ALL SELECT 'MERCHANT_PAYMENT_RISK_RESERVATION_RECOVERY','累计限额预占复核',
                     'merchantPaymentRiskReservationRecovery','0/15 * * * * ?'
) target
WHERE NOT EXISTS (SELECT 1 FROM sys_job_task existing WHERE existing.job_code=target.job_code);

INSERT INTO sys_dict_data (dict_type,dict_label,dict_value,locale,dict_sort,is_default,status,remark,created_by,updated_by,deleted)
SELECT 'email_scene_code','支付方式进度','MERCHANT_PAYMENT_CAPABILITY','zh-CN',81,0,1,'支付方式授权和配置进度','system','system',0
WHERE EXISTS (SELECT 1 FROM sys_dict_type WHERE dict_type='email_scene_code' AND deleted=0)
  AND NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE dict_type='email_scene_code'
      AND dict_value='MERCHANT_PAYMENT_CAPABILITY' AND locale='zh-CN' AND deleted=0);

INSERT INTO msg_email_template (template_code,template_name,app_code,scene_code,locale,subject_template,content_type,content_template,
    variable_schema,sensitive_variable_names,status,system_builtin,version_no,remark,create_by,update_by,deleted)
SELECT 'MERCHANT_PAYMENT_CAPABILITY','支付方式进度','ADMIN','MERCHANT_PAYMENT_CAPABILITY','zh-CN','支付方式进度更新','TEXT',
    '商户 ${merchantId} 的支付方式 ${itemKey} 状态已更新。请登录商户平台查看。',JSON_ARRAY('merchantId','action','itemKey','occurredAt'),JSON_ARRAY(),1,1,1,
    '通过现有可靠通知流程投递','system','system',0
WHERE NOT EXISTS (SELECT 1 FROM msg_email_template WHERE template_code='MERCHANT_PAYMENT_CAPABILITY'
    AND app_code='ADMIN' AND locale='zh-CN' AND deleted=0);

INSERT INTO sys_dict_data (dict_type,dict_label,dict_value,locale,dict_sort,is_default,status,remark,created_by,updated_by,deleted)
SELECT 'email_scene_code','Payment method progress','MERCHANT_PAYMENT_CAPABILITY','en-US',81,0,1,'支付方式授权和配置进度','system','system',0
WHERE EXISTS (SELECT 1 FROM sys_dict_type WHERE dict_type='email_scene_code' AND deleted=0)
  AND NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE dict_type='email_scene_code'
      AND dict_value='MERCHANT_PAYMENT_CAPABILITY' AND locale='en-US' AND deleted=0);

INSERT INTO msg_email_template (template_code,template_name,app_code,scene_code,locale,subject_template,content_type,content_template,
    variable_schema,sensitive_variable_names,status,system_builtin,version_no,remark,create_by,update_by,deleted)
SELECT 'MERCHANT_PAYMENT_CAPABILITY','Payment method progress','ADMIN','MERCHANT_PAYMENT_CAPABILITY','en-US','Payment method update','TEXT',
    'Payment method ${itemKey} was updated for merchant ${merchantId}. Sign in to view the status.',JSON_ARRAY('merchantId','action','itemKey','occurredAt'),JSON_ARRAY(),1,1,1,
    '通过现有可靠通知流程投递','system','system',0
WHERE NOT EXISTS (SELECT 1 FROM msg_email_template WHERE template_code='MERCHANT_PAYMENT_CAPABILITY'
    AND app_code='ADMIN' AND locale='en-US' AND deleted=0);

COMMIT;
