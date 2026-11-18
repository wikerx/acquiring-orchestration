-- 支付链接双端菜单与独立动作权限。幂等增量，不修改已有交易权限。

SET NAMES utf8mb4;

START TRANSACTION;

INSERT INTO sys_menu (app_id,parent_id,menu_code,menu_name,menu_type,route_path,component_path,permission_code,icon,visible,keep_alive,external_link,sort_no,status,deleted)
SELECT a.id,p.id,'admin_payment_link_v1','支付链接','MENU','/transaction/payment-link','transaction/payment-link','transaction:payment-link:list','Link',1,0,0,56,1,0
FROM sys_app a JOIN sys_menu p ON p.app_id=a.id AND p.menu_code='admin_transaction_catalog_v1' AND p.deleted=0
WHERE a.app_code='ADMIN' AND a.deleted=0
ON DUPLICATE KEY UPDATE parent_id=VALUES(parent_id),menu_name=VALUES(menu_name),route_path=VALUES(route_path),component_path=VALUES(component_path),permission_code=VALUES(permission_code),deleted=0;

INSERT INTO sys_menu (app_id,parent_id,menu_code,menu_name,menu_type,permission_code,visible,keep_alive,external_link,sort_no,status,deleted)
SELECT a.id,p.id,'admin_payment_link_detail_v1','支付链接详情','BUTTON','transaction:payment-link:detail',0,0,0,1,1,0
FROM sys_app a JOIN sys_menu p ON p.app_id=a.id AND p.menu_code='admin_payment_link_v1' AND p.deleted=0
WHERE a.app_code='ADMIN' AND a.deleted=0
ON DUPLICATE KEY UPDATE parent_id=VALUES(parent_id),permission_code=VALUES(permission_code),deleted=0;

INSERT INTO sys_menu (app_id,parent_id,menu_code,menu_name,menu_type,permission_code,visible,keep_alive,external_link,sort_no,status,deleted)
SELECT a.id,p.id,'admin_payment_link_add_v1','创建支付链接','BUTTON','transaction:payment-link:add',0,0,0,2,1,0
FROM sys_app a JOIN sys_menu p ON p.app_id=a.id AND p.menu_code='admin_payment_link_v1' AND p.deleted=0
WHERE a.app_code='ADMIN' AND a.deleted=0
ON DUPLICATE KEY UPDATE parent_id=VALUES(parent_id),permission_code=VALUES(permission_code),deleted=0;

INSERT INTO sys_menu (app_id,parent_id,menu_code,menu_name,menu_type,permission_code,visible,keep_alive,external_link,sort_no,status,deleted)
SELECT a.id,p.id,'admin_payment_link_share_v1','获取支付分享地址','BUTTON','transaction:payment-link:share',0,0,0,3,1,0
FROM sys_app a JOIN sys_menu p ON p.app_id=a.id AND p.menu_code='admin_payment_link_v1' AND p.deleted=0
WHERE a.app_code='ADMIN' AND a.deleted=0
ON DUPLICATE KEY UPDATE parent_id=VALUES(parent_id),permission_code=VALUES(permission_code),deleted=0;

INSERT INTO sys_menu (app_id,parent_id,menu_code,menu_name,menu_type,permission_code,visible,keep_alive,external_link,sort_no,status,deleted)
SELECT a.id,p.id,'admin_payment_link_send_v1','发送支付邀请','BUTTON','transaction:payment-link:send',0,0,0,4,1,0
FROM sys_app a JOIN sys_menu p ON p.app_id=a.id AND p.menu_code='admin_payment_link_v1' AND p.deleted=0
WHERE a.app_code='ADMIN' AND a.deleted=0
ON DUPLICATE KEY UPDATE parent_id=VALUES(parent_id),permission_code=VALUES(permission_code),deleted=0;

INSERT INTO sys_menu (app_id,parent_id,menu_code,menu_name,menu_type,permission_code,visible,keep_alive,external_link,sort_no,status,deleted)
SELECT a.id,p.id,'admin_payment_link_delivery_v1','查询邮件投递','BUTTON','transaction:payment-link:delivery',0,0,0,5,1,0
FROM sys_app a JOIN sys_menu p ON p.app_id=a.id AND p.menu_code='admin_payment_link_v1' AND p.deleted=0
WHERE a.app_code='ADMIN' AND a.deleted=0
ON DUPLICATE KEY UPDATE parent_id=VALUES(parent_id),permission_code=VALUES(permission_code),deleted=0;

INSERT INTO sys_menu (app_id,parent_id,menu_code,menu_name,menu_type,permission_code,visible,keep_alive,external_link,sort_no,status,deleted)
SELECT a.id,p.id,'admin_payment_link_disable_v1','停用支付链接','BUTTON','transaction:payment-link:disable',0,0,0,6,1,0
FROM sys_app a JOIN sys_menu p ON p.app_id=a.id AND p.menu_code='admin_payment_link_v1' AND p.deleted=0
WHERE a.app_code='ADMIN' AND a.deleted=0
ON DUPLICATE KEY UPDATE parent_id=VALUES(parent_id),permission_code=VALUES(permission_code),deleted=0;

INSERT INTO sys_menu (app_id,parent_id,menu_code,menu_name,menu_type,permission_code,visible,keep_alive,external_link,sort_no,status,deleted)
SELECT a.id,p.id,'admin_payment_link_export_v1','导出支付链接','BUTTON','transaction:payment-link:export',0,0,0,7,1,0
FROM sys_app a JOIN sys_menu p ON p.app_id=a.id AND p.menu_code='admin_payment_link_v1' AND p.deleted=0
WHERE a.app_code='ADMIN' AND a.deleted=0
ON DUPLICATE KEY UPDATE parent_id=VALUES(parent_id),permission_code=VALUES(permission_code),deleted=0;

INSERT INTO sys_permission (app_id,menu_id,permission_code,permission_name,permission_type,resource_method,resource_path,status,deleted)
SELECT a.id,m.id,'transaction:payment-link:list','支付链接查询','MENU','POST','/admin/transactions/payment-links/search',1,0
FROM sys_app a JOIN sys_menu m ON m.app_id=a.id AND m.menu_code='admin_payment_link_v1' AND m.deleted=0
WHERE a.app_code='ADMIN' AND a.deleted=0
ON DUPLICATE KEY UPDATE menu_id=VALUES(menu_id),resource_method=VALUES(resource_method),resource_path=VALUES(resource_path),deleted=0;

INSERT INTO sys_permission (app_id,menu_id,permission_code,permission_name,permission_type,resource_method,resource_path,status,deleted)
SELECT a.id,m.id,'transaction:payment-link:detail','支付链接详情','BUTTON','GET','/admin/transactions/payment-links/*',1,0
FROM sys_app a JOIN sys_menu m ON m.app_id=a.id AND m.menu_code='admin_payment_link_detail_v1' AND m.deleted=0
WHERE a.app_code='ADMIN' AND a.deleted=0
ON DUPLICATE KEY UPDATE menu_id=VALUES(menu_id),resource_method=VALUES(resource_method),resource_path=VALUES(resource_path),deleted=0;

INSERT INTO sys_permission (app_id,menu_id,permission_code,permission_name,permission_type,resource_method,resource_path,status,deleted)
SELECT a.id,m.id,'transaction:payment-link:add','创建支付链接','BUTTON','POST','/admin/transactions/payment-links',1,0
FROM sys_app a JOIN sys_menu m ON m.app_id=a.id AND m.menu_code='admin_payment_link_add_v1' AND m.deleted=0
WHERE a.app_code='ADMIN' AND a.deleted=0
ON DUPLICATE KEY UPDATE menu_id=VALUES(menu_id),resource_method=VALUES(resource_method),resource_path=VALUES(resource_path),deleted=0;

INSERT INTO sys_permission (app_id,menu_id,permission_code,permission_name,permission_type,resource_method,resource_path,status,deleted)
SELECT a.id,m.id,'transaction:payment-link:share','获取支付分享地址','BUTTON','POST','/admin/transactions/payment-links/*/share',1,0
FROM sys_app a JOIN sys_menu m ON m.app_id=a.id AND m.menu_code='admin_payment_link_share_v1' AND m.deleted=0
WHERE a.app_code='ADMIN' AND a.deleted=0
ON DUPLICATE KEY UPDATE menu_id=VALUES(menu_id),resource_method=VALUES(resource_method),resource_path=VALUES(resource_path),deleted=0;

INSERT INTO sys_permission (app_id,menu_id,permission_code,permission_name,permission_type,resource_method,resource_path,status,deleted)
SELECT a.id,m.id,'transaction:payment-link:send','发送支付邀请','BUTTON','POST','/admin/transactions/payment-links/*/send-email',1,0
FROM sys_app a JOIN sys_menu m ON m.app_id=a.id AND m.menu_code='admin_payment_link_send_v1' AND m.deleted=0
WHERE a.app_code='ADMIN' AND a.deleted=0
ON DUPLICATE KEY UPDATE menu_id=VALUES(menu_id),resource_method=VALUES(resource_method),resource_path=VALUES(resource_path),deleted=0;

INSERT INTO sys_permission (app_id,menu_id,permission_code,permission_name,permission_type,resource_method,resource_path,status,deleted)
SELECT a.id,m.id,'transaction:payment-link:delivery','查询邮件投递','BUTTON','POST','/admin/transactions/payment-links/*/deliveries/search',1,0
FROM sys_app a JOIN sys_menu m ON m.app_id=a.id AND m.menu_code='admin_payment_link_delivery_v1' AND m.deleted=0
WHERE a.app_code='ADMIN' AND a.deleted=0
ON DUPLICATE KEY UPDATE menu_id=VALUES(menu_id),resource_method=VALUES(resource_method),resource_path=VALUES(resource_path),deleted=0;

INSERT INTO sys_permission (app_id,menu_id,permission_code,permission_name,permission_type,resource_method,resource_path,status,deleted)
SELECT a.id,m.id,'transaction:payment-link:disable','停用支付链接','BUTTON','POST','/admin/transactions/payment-links/*/disable',1,0
FROM sys_app a JOIN sys_menu m ON m.app_id=a.id AND m.menu_code='admin_payment_link_disable_v1' AND m.deleted=0
WHERE a.app_code='ADMIN' AND a.deleted=0
ON DUPLICATE KEY UPDATE menu_id=VALUES(menu_id),resource_method=VALUES(resource_method),resource_path=VALUES(resource_path),deleted=0;

INSERT INTO sys_permission (app_id,menu_id,permission_code,permission_name,permission_type,resource_method,resource_path,status,deleted)
SELECT a.id,m.id,'transaction:payment-link:export','导出支付链接','BUTTON','POST','/admin/transactions/payment-links/export',1,0
FROM sys_app a JOIN sys_menu m ON m.app_id=a.id AND m.menu_code='admin_payment_link_export_v1' AND m.deleted=0
WHERE a.app_code='ADMIN' AND a.deleted=0
ON DUPLICATE KEY UPDATE menu_id=VALUES(menu_id),resource_method=VALUES(resource_method),resource_path=VALUES(resource_path),deleted=0;

INSERT IGNORE INTO sys_role_menu (app_id,role_id,menu_id,deleted)
SELECT a.id,r.id,o.id,0 FROM sys_app a JOIN sys_role r ON r.app_id=a.id AND r.deleted=0
JOIN sys_menu o ON o.app_id=a.id AND o.deleted=0
WHERE a.app_code='ADMIN' AND a.deleted=0 AND (r.id=1 OR r.role_code IN ('ADMIN','ADMIN_OPERATOR')) AND o.menu_code LIKE 'admin_payment_link%_v1';

INSERT IGNORE INTO sys_role_permission (app_id,role_id,permission_id,deleted)
SELECT a.id,r.id,o.id,0 FROM sys_app a JOIN sys_role r ON r.app_id=a.id AND r.deleted=0
JOIN sys_permission o ON o.app_id=a.id AND o.deleted=0
WHERE a.app_code='ADMIN' AND a.deleted=0 AND (r.id=1 OR r.role_code IN ('ADMIN','ADMIN_OPERATOR')) AND o.permission_code LIKE 'transaction:payment-link:%';

INSERT INTO sys_menu (app_id,parent_id,menu_code,menu_name,menu_type,route_path,component_path,permission_code,icon,visible,keep_alive,external_link,sort_no,status,deleted)
SELECT a.id,p.id,'merchant_payment_link_v1','支付链接','MENU','/transaction/payment-link','transaction/payment-link','merchant:transaction:payment-link:list','Link',1,0,0,56,1,0
FROM sys_app a JOIN sys_menu p ON p.app_id=a.id AND p.menu_code='merchant_transaction_catalog_v1' AND p.deleted=0
WHERE a.app_code='MERCHANT' AND a.deleted=0
ON DUPLICATE KEY UPDATE parent_id=VALUES(parent_id),menu_name=VALUES(menu_name),route_path=VALUES(route_path),component_path=VALUES(component_path),permission_code=VALUES(permission_code),deleted=0;

INSERT INTO sys_menu (app_id,parent_id,menu_code,menu_name,menu_type,permission_code,visible,keep_alive,external_link,sort_no,status,deleted)
SELECT a.id,p.id,'merchant_payment_link_detail_v1','支付链接详情','BUTTON','merchant:transaction:payment-link:detail',0,0,0,1,1,0
FROM sys_app a JOIN sys_menu p ON p.app_id=a.id AND p.menu_code='merchant_payment_link_v1' AND p.deleted=0
WHERE a.app_code='MERCHANT' AND a.deleted=0
ON DUPLICATE KEY UPDATE parent_id=VALUES(parent_id),permission_code=VALUES(permission_code),deleted=0;

INSERT INTO sys_menu (app_id,parent_id,menu_code,menu_name,menu_type,permission_code,visible,keep_alive,external_link,sort_no,status,deleted)
SELECT a.id,p.id,'merchant_payment_link_add_v1','创建支付链接','BUTTON','merchant:transaction:payment-link:add',0,0,0,2,1,0
FROM sys_app a JOIN sys_menu p ON p.app_id=a.id AND p.menu_code='merchant_payment_link_v1' AND p.deleted=0
WHERE a.app_code='MERCHANT' AND a.deleted=0
ON DUPLICATE KEY UPDATE parent_id=VALUES(parent_id),permission_code=VALUES(permission_code),deleted=0;

INSERT INTO sys_menu (app_id,parent_id,menu_code,menu_name,menu_type,permission_code,visible,keep_alive,external_link,sort_no,status,deleted)
SELECT a.id,p.id,'merchant_payment_link_share_v1','获取支付分享地址','BUTTON','merchant:transaction:payment-link:share',0,0,0,3,1,0
FROM sys_app a JOIN sys_menu p ON p.app_id=a.id AND p.menu_code='merchant_payment_link_v1' AND p.deleted=0
WHERE a.app_code='MERCHANT' AND a.deleted=0
ON DUPLICATE KEY UPDATE parent_id=VALUES(parent_id),permission_code=VALUES(permission_code),deleted=0;

INSERT INTO sys_menu (app_id,parent_id,menu_code,menu_name,menu_type,permission_code,visible,keep_alive,external_link,sort_no,status,deleted)
SELECT a.id,p.id,'merchant_payment_link_send_v1','发送支付邀请','BUTTON','merchant:transaction:payment-link:send',0,0,0,4,1,0
FROM sys_app a JOIN sys_menu p ON p.app_id=a.id AND p.menu_code='merchant_payment_link_v1' AND p.deleted=0
WHERE a.app_code='MERCHANT' AND a.deleted=0
ON DUPLICATE KEY UPDATE parent_id=VALUES(parent_id),permission_code=VALUES(permission_code),deleted=0;

INSERT INTO sys_menu (app_id,parent_id,menu_code,menu_name,menu_type,permission_code,visible,keep_alive,external_link,sort_no,status,deleted)
SELECT a.id,p.id,'merchant_payment_link_delivery_v1','查询邮件投递','BUTTON','merchant:transaction:payment-link:delivery',0,0,0,5,1,0
FROM sys_app a JOIN sys_menu p ON p.app_id=a.id AND p.menu_code='merchant_payment_link_v1' AND p.deleted=0
WHERE a.app_code='MERCHANT' AND a.deleted=0
ON DUPLICATE KEY UPDATE parent_id=VALUES(parent_id),permission_code=VALUES(permission_code),deleted=0;

INSERT INTO sys_menu (app_id,parent_id,menu_code,menu_name,menu_type,permission_code,visible,keep_alive,external_link,sort_no,status,deleted)
SELECT a.id,p.id,'merchant_payment_link_disable_v1','停用支付链接','BUTTON','merchant:transaction:payment-link:disable',0,0,0,6,1,0
FROM sys_app a JOIN sys_menu p ON p.app_id=a.id AND p.menu_code='merchant_payment_link_v1' AND p.deleted=0
WHERE a.app_code='MERCHANT' AND a.deleted=0
ON DUPLICATE KEY UPDATE parent_id=VALUES(parent_id),permission_code=VALUES(permission_code),deleted=0;

INSERT INTO sys_menu (app_id,parent_id,menu_code,menu_name,menu_type,permission_code,visible,keep_alive,external_link,sort_no,status,deleted)
SELECT a.id,p.id,'merchant_payment_link_export_v1','导出支付链接','BUTTON','merchant:transaction:payment-link:export',0,0,0,7,1,0
FROM sys_app a JOIN sys_menu p ON p.app_id=a.id AND p.menu_code='merchant_payment_link_v1' AND p.deleted=0
WHERE a.app_code='MERCHANT' AND a.deleted=0
ON DUPLICATE KEY UPDATE parent_id=VALUES(parent_id),permission_code=VALUES(permission_code),deleted=0;

INSERT INTO sys_permission (app_id,menu_id,permission_code,permission_name,permission_type,resource_method,resource_path,status,deleted)
SELECT a.id,m.id,'merchant:transaction:payment-link:list','支付链接查询','MENU','POST','/merchant/transactions/payment-links/search',1,0
FROM sys_app a JOIN sys_menu m ON m.app_id=a.id AND m.menu_code='merchant_payment_link_v1' AND m.deleted=0
WHERE a.app_code='MERCHANT' AND a.deleted=0
ON DUPLICATE KEY UPDATE menu_id=VALUES(menu_id),resource_method=VALUES(resource_method),resource_path=VALUES(resource_path),deleted=0;

INSERT INTO sys_permission (app_id,menu_id,permission_code,permission_name,permission_type,resource_method,resource_path,status,deleted)
SELECT a.id,m.id,'merchant:transaction:payment-link:detail','支付链接详情','BUTTON','GET','/merchant/transactions/payment-links/*',1,0
FROM sys_app a JOIN sys_menu m ON m.app_id=a.id AND m.menu_code='merchant_payment_link_detail_v1' AND m.deleted=0
WHERE a.app_code='MERCHANT' AND a.deleted=0
ON DUPLICATE KEY UPDATE menu_id=VALUES(menu_id),resource_method=VALUES(resource_method),resource_path=VALUES(resource_path),deleted=0;

INSERT INTO sys_permission (app_id,menu_id,permission_code,permission_name,permission_type,resource_method,resource_path,status,deleted)
SELECT a.id,m.id,'merchant:transaction:payment-link:add','创建支付链接','BUTTON','POST','/merchant/transactions/payment-links',1,0
FROM sys_app a JOIN sys_menu m ON m.app_id=a.id AND m.menu_code='merchant_payment_link_add_v1' AND m.deleted=0
WHERE a.app_code='MERCHANT' AND a.deleted=0
ON DUPLICATE KEY UPDATE menu_id=VALUES(menu_id),resource_method=VALUES(resource_method),resource_path=VALUES(resource_path),deleted=0;

INSERT INTO sys_permission (app_id,menu_id,permission_code,permission_name,permission_type,resource_method,resource_path,status,deleted)
SELECT a.id,m.id,'merchant:transaction:payment-link:share','获取支付分享地址','BUTTON','POST','/merchant/transactions/payment-links/*/share',1,0
FROM sys_app a JOIN sys_menu m ON m.app_id=a.id AND m.menu_code='merchant_payment_link_share_v1' AND m.deleted=0
WHERE a.app_code='MERCHANT' AND a.deleted=0
ON DUPLICATE KEY UPDATE menu_id=VALUES(menu_id),resource_method=VALUES(resource_method),resource_path=VALUES(resource_path),deleted=0;

INSERT INTO sys_permission (app_id,menu_id,permission_code,permission_name,permission_type,resource_method,resource_path,status,deleted)
SELECT a.id,m.id,'merchant:transaction:payment-link:send','发送支付邀请','BUTTON','POST','/merchant/transactions/payment-links/*/send-email',1,0
FROM sys_app a JOIN sys_menu m ON m.app_id=a.id AND m.menu_code='merchant_payment_link_send_v1' AND m.deleted=0
WHERE a.app_code='MERCHANT' AND a.deleted=0
ON DUPLICATE KEY UPDATE menu_id=VALUES(menu_id),resource_method=VALUES(resource_method),resource_path=VALUES(resource_path),deleted=0;

INSERT INTO sys_permission (app_id,menu_id,permission_code,permission_name,permission_type,resource_method,resource_path,status,deleted)
SELECT a.id,m.id,'merchant:transaction:payment-link:delivery','查询邮件投递','BUTTON','POST','/merchant/transactions/payment-links/*/deliveries/search',1,0
FROM sys_app a JOIN sys_menu m ON m.app_id=a.id AND m.menu_code='merchant_payment_link_delivery_v1' AND m.deleted=0
WHERE a.app_code='MERCHANT' AND a.deleted=0
ON DUPLICATE KEY UPDATE menu_id=VALUES(menu_id),resource_method=VALUES(resource_method),resource_path=VALUES(resource_path),deleted=0;

INSERT INTO sys_permission (app_id,menu_id,permission_code,permission_name,permission_type,resource_method,resource_path,status,deleted)
SELECT a.id,m.id,'merchant:transaction:payment-link:disable','停用支付链接','BUTTON','POST','/merchant/transactions/payment-links/*/disable',1,0
FROM sys_app a JOIN sys_menu m ON m.app_id=a.id AND m.menu_code='merchant_payment_link_disable_v1' AND m.deleted=0
WHERE a.app_code='MERCHANT' AND a.deleted=0
ON DUPLICATE KEY UPDATE menu_id=VALUES(menu_id),resource_method=VALUES(resource_method),resource_path=VALUES(resource_path),deleted=0;

INSERT INTO sys_permission (app_id,menu_id,permission_code,permission_name,permission_type,resource_method,resource_path,status,deleted)
SELECT a.id,m.id,'merchant:transaction:payment-link:export','导出支付链接','BUTTON','POST','/merchant/transactions/payment-links/export',1,0
FROM sys_app a JOIN sys_menu m ON m.app_id=a.id AND m.menu_code='merchant_payment_link_export_v1' AND m.deleted=0
WHERE a.app_code='MERCHANT' AND a.deleted=0
ON DUPLICATE KEY UPDATE menu_id=VALUES(menu_id),resource_method=VALUES(resource_method),resource_path=VALUES(resource_path),deleted=0;

INSERT IGNORE INTO sys_role_menu (app_id,role_id,menu_id,deleted)
SELECT a.id,r.id,o.id,0 FROM sys_app a JOIN sys_role r ON r.app_id=a.id AND r.deleted=0
JOIN sys_menu o ON o.app_id=a.id AND o.deleted=0
WHERE a.app_code='MERCHANT' AND a.deleted=0 AND (r.role_code='MERCHANT_ADMIN' OR r.role_code LIKE 'MERCHANT_ADMIN\_%') AND o.menu_code LIKE 'merchant_payment_link%_v1';

INSERT IGNORE INTO sys_role_permission (app_id,role_id,permission_id,deleted)
SELECT a.id,r.id,o.id,0 FROM sys_app a JOIN sys_role r ON r.app_id=a.id AND r.deleted=0
JOIN sys_permission o ON o.app_id=a.id AND o.deleted=0
WHERE a.app_code='MERCHANT' AND a.deleted=0 AND (r.role_code='MERCHANT_ADMIN' OR r.role_code LIKE 'MERCHANT_ADMIN\_%') AND o.permission_code LIKE 'merchant:transaction:payment-link:%';

INSERT IGNORE INTO sys_merchant_menu_grant (merchant_id,app_id,menu_id,grant_source,status,created_at,updated_at,deleted)
SELECT b.merchant_id,a.id,o.id,'SYSTEM',1,CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3),0
FROM base_merchant_info b JOIN sys_app a ON a.app_code='MERCHANT' AND a.deleted=0
JOIN sys_menu o ON o.app_id=a.id AND o.deleted=0 WHERE b.deleted=0 AND o.menu_code LIKE 'merchant_payment_link%_v1';

INSERT IGNORE INTO sys_merchant_permission_grant (merchant_id,app_id,permission_id,grant_source,status,created_at,updated_at,deleted)
SELECT b.merchant_id,a.id,o.id,'SYSTEM',1,CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3),0
FROM base_merchant_info b JOIN sys_app a ON a.app_code='MERCHANT' AND a.deleted=0
JOIN sys_permission o ON o.app_id=a.id AND o.deleted=0 WHERE b.deleted=0 AND o.permission_code LIKE 'merchant:transaction:payment-link:%';

COMMIT;

SELECT a.app_code,m.menu_code,m.permission_code FROM sys_menu m JOIN sys_app a ON a.id=m.app_id WHERE m.menu_code LIKE '%payment_link%_v1' AND m.deleted=0;
