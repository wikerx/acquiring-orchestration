-- 商户资料变更申请迁移：可重复执行，新增申请表、扩展审核与资料元数据，并补齐商户端最小权限。

CREATE TABLE IF NOT EXISTS merchant_profile_change_request (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    request_no VARCHAR(40) NOT NULL COMMENT '资料变更申请编号',
    merchant_id VARCHAR(32) NOT NULL COMMENT '平台商户号',
    status VARCHAR(32) NOT NULL COMMENT 'DRAFT、PENDING_REVIEW、SUPPLEMENT_REQUIRED、APPROVED、REJECTED或WITHDRAWN',
    active_flag TINYINT NULL COMMENT '活动申请为1，终态为NULL，用于单商户活动申请唯一约束',
    current_snapshot_cipher MEDIUMTEXT NOT NULL COMMENT '创建申请时正式资料AES-GCM加密快照',
    proposed_snapshot_cipher MEDIUMTEXT NOT NULL COMMENT '拟变更资料AES-GCM加密快照',
    changed_fields_json TEXT NOT NULL COMMENT '变更字段编码JSON数组',
    submit_comment VARCHAR(1000) NULL COMMENT '商户提交说明',
    review_comment VARCHAR(1000) NULL COMMENT '审核意见或补件说明',
    submitted_by VARCHAR(64) NULL COMMENT '提交商户账号ID',
    reviewed_by VARCHAR(64) NULL COMMENT '审核管理账号ID',
    submitted_at DATETIME(3) NULL COMMENT '提交审核时间',
    reviewed_at DATETIME(3) NULL COMMENT '审核处理时间',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    gmt_create DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    gmt_modified DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '修改时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '删除标识',
    PRIMARY KEY (id),
    UNIQUE KEY uk_merchant_profile_change_request_no (request_no),
    UNIQUE KEY uk_merchant_profile_change_active (merchant_id, active_flag),
    KEY idx_merchant_profile_change_review (status, submitted_at, id),
    KEY idx_merchant_profile_change_history (merchant_id, gmt_create, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商户资料变更申请';

DROP PROCEDURE IF EXISTS ensure_merchant_profile_change_column;
DELIMITER $$
CREATE PROCEDURE ensure_merchant_profile_change_column(
    IN table_name_value VARCHAR(64),
    IN column_name_value VARCHAR(64),
    IN column_definition_value TEXT
)
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = table_name_value
          AND column_name = column_name_value
    ) THEN
        SET @column_sql = CONCAT('ALTER TABLE `', table_name_value, '` ADD COLUMN ', column_definition_value);
        PREPARE column_statement FROM @column_sql;
        EXECUTE column_statement;
        DEALLOCATE PREPARE column_statement;
    END IF;
END$$
DELIMITER ;

DROP PROCEDURE IF EXISTS ensure_merchant_profile_change_index;
DELIMITER $$
CREATE PROCEDURE ensure_merchant_profile_change_index(
    IN table_name_value VARCHAR(64),
    IN index_name_value VARCHAR(64),
    IN index_definition_value TEXT
)
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = table_name_value
          AND index_name = index_name_value
    ) THEN
        SET @index_sql = CONCAT('ALTER TABLE `', table_name_value, '` ADD ', index_definition_value);
        PREPARE index_statement FROM @index_sql;
        EXECUTE index_statement;
        DEALLOCATE PREPARE index_statement;
    END IF;
END$$
DELIMITER ;

CALL ensure_merchant_profile_change_column(
    'merchant_review_record',
    'review_scope',
    'review_scope VARCHAR(32) NOT NULL DEFAULT ''ONBOARDING'' COMMENT ''ONBOARDING或PROFILE_CHANGE'' AFTER merchant_id'
);
CALL ensure_merchant_profile_change_column(
    'merchant_review_record',
    'request_no',
    'request_no VARCHAR(40) NULL COMMENT ''资料变更申请编号'' AFTER review_scope'
);
CALL ensure_merchant_profile_change_index(
    'merchant_review_record',
    'idx_merchant_review_request',
    'INDEX idx_merchant_review_request (review_scope, request_no, gmt_create, id)'
);

CALL ensure_merchant_profile_change_column(
    'biz_document',
    'request_no',
    'request_no VARCHAR(40) NULL COMMENT ''资料变更申请编号；正式资料为空'' AFTER biz_id'
);
CALL ensure_merchant_profile_change_index(
    'biz_document',
    'idx_biz_document_request',
    'INDEX idx_biz_document_request (biz_type, biz_id, request_no, deleted, gmt_create)'
);

DROP PROCEDURE IF EXISTS ensure_merchant_profile_change_column;
DROP PROCEDURE IF EXISTS ensure_merchant_profile_change_index;

START TRANSACTION;

INSERT INTO sys_menu (
    app_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path,
    permission_code, icon, visible, keep_alive, external_link, sort_no, status, deleted
)
SELECT app.id, 0, 'merchant_info_catalog_v1', '商户信息', 'CATALOG',
       '/merchant-info', NULL, NULL, 'OfficeBuilding', 1, 1, 0, 20, 1, 0
FROM sys_app app
WHERE app.app_code = 'MERCHANT'
  AND app.deleted = 0
  AND NOT EXISTS (
      SELECT 1
      FROM sys_menu existing
      WHERE existing.app_id = app.id
        AND existing.menu_code = 'merchant_info_catalog_v1'
        AND existing.deleted = 0
  );

UPDATE sys_menu menu
JOIN sys_app app ON app.id = menu.app_id
                AND app.app_code = 'MERCHANT'
                AND app.deleted = 0
SET menu.parent_id = 0,
    menu.menu_name = '商户信息',
    menu.menu_type = 'CATALOG',
    menu.route_path = '/merchant-info',
    menu.component_path = NULL,
    menu.permission_code = NULL,
    menu.icon = 'OfficeBuilding',
    menu.visible = 1,
    menu.keep_alive = 1,
    menu.external_link = 0,
    menu.sort_no = 20,
    menu.status = 1,
    menu.updated_at = CURRENT_TIMESTAMP(3)
WHERE menu.menu_code = 'merchant_info_catalog_v1'
  AND menu.deleted = 0;

INSERT INTO sys_menu (
    app_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path,
    permission_code, icon, visible, keep_alive, external_link, sort_no, status, deleted
)
SELECT app.id, parent.id, 'merchant_profile_maintenance_v1', '资料维护', 'MENU',
       '/merchant-info/profile', 'merchant/info', 'merchant:info:view',
       'EditPen', 1, 1, 0, 20, 1, 0
FROM sys_app app
JOIN sys_menu parent ON parent.app_id = app.id
                    AND parent.menu_code = 'merchant_info_catalog_v1'
                    AND parent.deleted = 0
WHERE app.app_code = 'MERCHANT'
  AND app.deleted = 0
  AND NOT EXISTS (
      SELECT 1
      FROM sys_menu existing
      WHERE existing.app_id = app.id
        AND existing.menu_code = 'merchant_profile_maintenance_v1'
        AND existing.deleted = 0
  );

UPDATE sys_menu menu
JOIN sys_app app ON app.id = menu.app_id
                AND app.app_code = 'MERCHANT'
                AND app.deleted = 0
JOIN sys_menu parent ON parent.app_id = app.id
                    AND parent.menu_code = 'merchant_info_catalog_v1'
                    AND parent.deleted = 0
SET menu.parent_id = parent.id,
    menu.menu_name = '资料维护',
    menu.menu_type = 'MENU',
    menu.route_path = '/merchant-info/profile',
    menu.component_path = 'merchant/info',
    menu.permission_code = 'merchant:info:view',
    menu.icon = 'EditPen',
    menu.visible = 1,
    menu.keep_alive = 1,
    menu.external_link = 0,
    menu.sort_no = 20,
    menu.status = 1,
    menu.updated_at = CURRENT_TIMESTAMP(3)
WHERE menu.menu_code = 'merchant_profile_maintenance_v1'
  AND menu.deleted = 0;

INSERT INTO sys_permission (
    app_id, menu_id, permission_code, permission_name, permission_type,
    resource_method, resource_path, description, status, deleted
)
SELECT app.id, menu.id, permission_item.permission_code, permission_item.permission_name,
       permission_item.permission_type, permission_item.resource_method,
       permission_item.resource_path, permission_item.description, 1, 0
FROM sys_app app
JOIN sys_menu menu ON menu.app_id = app.id
                  AND menu.menu_code = 'merchant_profile_maintenance_v1'
                  AND menu.deleted = 0
JOIN (
    SELECT 'merchant:info:view' AS permission_code,
           '商户资料查询' AS permission_name,
           'MENU' AS permission_type,
           'GET' AS resource_method,
           '/merchant/info/**' AS resource_path,
           '查询当前登录商户的正式资料、变更申请和合规资料' AS description
    UNION ALL
    SELECT 'merchant:info:edit', '商户资料维护', 'BUTTON', '*', '/merchant/info/**',
           '维护当前登录商户资料并提交高风险字段审核'
) permission_item
WHERE app.app_code = 'MERCHANT'
  AND app.deleted = 0
  AND NOT EXISTS (
      SELECT 1
      FROM sys_permission existing
      WHERE existing.app_id = app.id
        AND existing.permission_code = permission_item.permission_code
        AND existing.deleted = 0
  );

UPDATE sys_permission permission
JOIN sys_app app ON app.id = permission.app_id
                AND app.app_code = 'MERCHANT'
                AND app.deleted = 0
JOIN sys_menu menu ON menu.app_id = app.id
                  AND menu.menu_code = 'merchant_profile_maintenance_v1'
                  AND menu.deleted = 0
SET permission.menu_id = menu.id,
    permission.permission_name = CASE permission.permission_code
        WHEN 'merchant:info:view' THEN '商户资料查询'
        ELSE '商户资料维护'
    END,
    permission.permission_type = CASE permission.permission_code
        WHEN 'merchant:info:view' THEN 'MENU'
        ELSE 'BUTTON'
    END,
    permission.resource_method = CASE permission.permission_code
        WHEN 'merchant:info:view' THEN 'GET'
        ELSE '*'
    END,
    permission.resource_path = '/merchant/info/**',
    permission.description = CASE permission.permission_code
        WHEN 'merchant:info:view' THEN '查询当前登录商户的正式资料、变更申请和合规资料'
        ELSE '维护当前登录商户资料并提交高风险字段审核'
    END,
    permission.status = 1,
    permission.updated_at = CURRENT_TIMESTAMP(3)
WHERE permission.permission_code IN ('merchant:info:view', 'merchant:info:edit')
  AND permission.deleted = 0;

INSERT IGNORE INTO sys_role_menu (app_id, role_id, menu_id, deleted)
SELECT role.app_id, role.id, menu.id, 0
FROM sys_role role
JOIN sys_app app ON app.id = role.app_id
                AND app.app_code = 'MERCHANT'
                AND app.deleted = 0
JOIN sys_menu menu ON menu.app_id = role.app_id
                  AND menu.deleted = 0
WHERE role.deleted = 0
  AND (role.role_code = 'MERCHANT_ADMIN' OR role.role_code LIKE 'MERCHANT_ADMIN\_%')
  AND menu.menu_code IN ('merchant_info_catalog_v1', 'merchant_profile_maintenance_v1');

INSERT IGNORE INTO sys_role_permission (app_id, role_id, permission_id, deleted)
SELECT role.app_id, role.id, permission.id, 0
FROM sys_role role
JOIN sys_app app ON app.id = role.app_id
                AND app.app_code = 'MERCHANT'
                AND app.deleted = 0
JOIN sys_permission permission ON permission.app_id = role.app_id
                              AND permission.deleted = 0
WHERE role.deleted = 0
  AND (role.role_code = 'MERCHANT_ADMIN' OR role.role_code LIKE 'MERCHANT_ADMIN\_%')
  AND permission.permission_code IN ('merchant:info:view', 'merchant:info:edit');

INSERT IGNORE INTO sys_merchant_menu_grant (
    merchant_id, app_id, menu_id, grant_source, status, created_at, updated_at, deleted
)
SELECT merchant.merchant_id, menu.app_id, menu.id, 'SYSTEM', 1,
       CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 0
FROM base_merchant_info merchant
JOIN sys_app app ON app.app_code = 'MERCHANT' AND app.deleted = 0
JOIN sys_menu menu ON menu.app_id = app.id AND menu.deleted = 0
WHERE merchant.deleted = 0
  AND menu.menu_code IN ('merchant_info_catalog_v1', 'merchant_profile_maintenance_v1');

INSERT IGNORE INTO sys_merchant_permission_grant (
    merchant_id, app_id, permission_id, grant_source, status, created_at, updated_at, deleted
)
SELECT merchant.merchant_id, permission.app_id, permission.id, 'SYSTEM', 1,
       CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 0
FROM base_merchant_info merchant
JOIN sys_app app ON app.app_code = 'MERCHANT' AND app.deleted = 0
JOIN sys_permission permission ON permission.app_id = app.id AND permission.deleted = 0
WHERE merchant.deleted = 0
  AND permission.permission_code IN ('merchant:info:view', 'merchant:info:edit');

COMMIT;
