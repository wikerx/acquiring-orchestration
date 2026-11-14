-- 系统管理 -> 模板中心：定义、不可变版本、本地化资产、权限与菜单。
-- 执行前：确认 MinIO/S3 已创建私有 Bucket vexra-template-files，并在 Nacos 配置
-- acquiring.object-storage.template-bucket=vexra-template-files。
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS sys_file_template (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    template_code VARCHAR(64) NOT NULL COMMENT '稳定模板编码，业务代码只引用该编码',
    owner_system VARCHAR(16) NOT NULL COMMENT 'ADMIN、MERCHANT或SHARED',
    module_code VARCHAR(64) NOT NULL COMMENT '所属业务模块编码',
    name_zh VARCHAR(128) NOT NULL COMMENT '中文下载文件名，不含扩展名',
    name_en VARCHAR(128) NOT NULL COMMENT '英文下载文件名，不含扩展名',
    file_extension VARCHAR(16) NOT NULL COMMENT '系统自动拼接的扩展名',
    admin_permission_code VARCHAR(128) NULL COMMENT '管理系统消费该模板时的业务权限',
    merchant_permission_code VARCHAR(128) NULL COMMENT '商户系统消费该模板时的业务权限',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '0停用，1启用',
    current_version_id BIGINT NULL COMMENT '当前发布版本ID',
    last_operation_request_id VARCHAR(64) NULL COMMENT '最近发布或回滚幂等请求ID',
    remark VARCHAR(500) NULL COMMENT '备注',
    version BIGINT NOT NULL DEFAULT 0 COMMENT '定义与发布指针CAS版本',
    create_by VARCHAR(64) NOT NULL COMMENT '创建人',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_by VARCHAR(64) NOT NULL COMMENT '最后操作人',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '操作时间',
    deleted BIGINT NOT NULL DEFAULT 0 COMMENT '删除标识；系统模板只允许停用，不开放删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_file_template_code_deleted (template_code, deleted),
    KEY idx_sys_file_template_scope (owner_system, module_code, status, deleted),
    KEY idx_sys_file_template_current (current_version_id),
    CONSTRAINT chk_sys_file_template_owner CHECK (owner_system IN ('ADMIN', 'MERCHANT', 'SHARED')),
    CONSTRAINT chk_sys_file_template_extension CHECK (file_extension IN ('xlsx', 'csv', 'pdf', 'docx', 'zip')),
    CONSTRAINT chk_sys_file_template_status CHECK (status IN (0, 1)),
    CONSTRAINT chk_sys_file_template_version CHECK (version >= 0),
    CONSTRAINT chk_sys_file_template_permission_scope CHECK (
        (owner_system = 'ADMIN' AND admin_permission_code IS NOT NULL AND merchant_permission_code IS NULL)
        OR (owner_system = 'MERCHANT' AND admin_permission_code IS NULL AND merchant_permission_code IS NOT NULL)
        OR (owner_system = 'SHARED' AND admin_permission_code IS NOT NULL AND merchant_permission_code IS NOT NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统文件模板定义';

CREATE TABLE IF NOT EXISTS sys_file_template_version (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    template_id BIGINT NOT NULL COMMENT '模板定义ID',
    version_no INT NOT NULL COMMENT '单模板递增版本号',
    schema_version VARCHAR(32) NULL COMMENT '导入字段协议版本',
    request_id VARCHAR(64) NOT NULL COMMENT '上传幂等请求ID',
    release_note VARCHAR(500) NULL COMMENT '版本说明',
    publish_status VARCHAR(16) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT、PUBLISHED或ARCHIVED',
    published_by VARCHAR(64) NULL COMMENT '发布或回滚操作人',
    publish_reason VARCHAR(500) NULL COMMENT '发布或回滚原因',
    published_time DATETIME(3) NULL COMMENT '最近发布时间',
    create_by VARCHAR(64) NOT NULL COMMENT '上传人',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '上传时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_file_template_version_no (template_id, version_no),
    UNIQUE KEY uk_sys_file_template_version_request (template_id, request_id),
    KEY idx_sys_file_template_version_status (template_id, publish_status, version_no),
    CONSTRAINT chk_sys_file_template_version_no CHECK (version_no > 0),
    CONSTRAINT chk_sys_file_template_publish_status CHECK (publish_status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统文件模板不可变版本';

CREATE TABLE IF NOT EXISTS sys_file_template_asset (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    template_version_id BIGINT NOT NULL COMMENT '模板版本ID',
    locale VARCHAR(16) NOT NULL COMMENT 'zh-CN或en-US',
    original_filename VARCHAR(255) NOT NULL COMMENT '上传时安全文件名，仅审计展示',
    content_type VARCHAR(128) NOT NULL COMMENT '服务端识别的MIME',
    file_size BIGINT NOT NULL COMMENT '文件字节数',
    sha256 CHAR(64) NOT NULL COMMENT '正文SHA-256',
    storage_provider VARCHAR(32) NOT NULL COMMENT 'MINIO、AWS_S3或其它S3兼容供应商',
    bucket_name VARCHAR(128) NOT NULL COMMENT '模板专用私有Bucket，仅服务端使用',
    object_key VARCHAR(512) NOT NULL COMMENT '不可预测对象键，仅服务端使用',
    create_by VARCHAR(64) NOT NULL COMMENT '上传人',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '上传时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_file_template_asset_locale (template_version_id, locale),
    UNIQUE KEY uk_sys_file_template_asset_object (object_key),
    KEY idx_sys_file_template_asset_sha (sha256),
    CONSTRAINT chk_sys_file_template_asset_locale CHECK (locale IN ('zh-CN', 'en-US')),
    CONSTRAINT chk_sys_file_template_asset_size CHECK (file_size > 0),
    CONSTRAINT chk_sys_file_template_asset_sha CHECK (CHAR_LENGTH(sha256) = 64)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统文件模板本地化资产';

-- 数据库级阻止模板编码、扩展名和版本内容被绕过应用服务修改。
DROP TRIGGER IF EXISTS trg_sys_file_template_stable_identity;
DELIMITER $$
CREATE TRIGGER trg_sys_file_template_stable_identity
BEFORE UPDATE ON sys_file_template
FOR EACH ROW
BEGIN
    IF NOT (OLD.template_code <=> NEW.template_code)
       OR NOT (OLD.file_extension <=> NEW.file_extension) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'template code and file extension are immutable';
    END IF;
END$$
DELIMITER ;

DROP TRIGGER IF EXISTS trg_sys_file_template_version_immutable;
DELIMITER $$
CREATE TRIGGER trg_sys_file_template_version_immutable
BEFORE UPDATE ON sys_file_template_version
FOR EACH ROW
BEGIN
    IF NOT (OLD.template_id <=> NEW.template_id)
       OR NOT (OLD.version_no <=> NEW.version_no)
       OR NOT (OLD.schema_version <=> NEW.schema_version)
       OR NOT (OLD.request_id <=> NEW.request_id)
       OR NOT (OLD.release_note <=> NEW.release_note)
       OR NOT (OLD.create_by <=> NEW.create_by)
       OR NOT (OLD.create_time <=> NEW.create_time) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'template version content is immutable';
    END IF;
END$$
DELIMITER ;

DROP TRIGGER IF EXISTS trg_sys_file_template_asset_immutable_update;
DELIMITER $$
CREATE TRIGGER trg_sys_file_template_asset_immutable_update
BEFORE UPDATE ON sys_file_template_asset
FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'template asset is immutable';
END$$
DELIMITER ;

DROP TRIGGER IF EXISTS trg_sys_file_template_asset_immutable_delete;
DELIMITER $$
CREATE TRIGGER trg_sys_file_template_asset_immutable_delete
BEFORE DELETE ON sys_file_template_asset
FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'template asset cannot be deleted';
END$$
DELIMITER ;

START TRANSACTION;

SET @admin_app_id = (SELECT id FROM sys_app WHERE app_code = 'ADMIN' AND deleted = 0 ORDER BY id LIMIT 1);
SET @admin_system_catalog_id = (
    SELECT id FROM sys_menu
    WHERE app_id = @admin_app_id
      AND menu_code IN ('system_manage', 'admin_system_catalog_v3', 'admin_system_center')
      AND menu_type = 'CATALOG' AND status = 1 AND deleted = 0
    ORDER BY FIELD(menu_code, 'system_manage', 'admin_system_catalog_v3', 'admin_system_center'), id
    LIMIT 1
);

INSERT INTO sys_menu (
    app_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path,
    permission_code, icon, visible, keep_alive, external_link, sort_no, status, deleted
)
SELECT @admin_app_id, @admin_system_catalog_id, 'admin_system_file_template_center_v1',
       '模板中心', 'MENU', '/system/template-center', 'system/template-center',
       'system:file-template:list', 'Files', 1, 0, 0, 17, 1, 0
WHERE @admin_app_id IS NOT NULL AND @admin_system_catalog_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM sys_menu existing
      WHERE existing.app_id = @admin_app_id
        AND existing.menu_code = 'admin_system_file_template_center_v1' AND existing.deleted = 0
  );

UPDATE sys_menu
SET parent_id = @admin_system_catalog_id, menu_name = '模板中心', menu_type = 'MENU',
    route_path = '/system/template-center', component_path = 'system/template-center',
    permission_code = 'system:file-template:list', icon = 'Files', visible = 1,
    keep_alive = 0, external_link = 0, sort_no = 17, status = 1,
    updated_at = CURRENT_TIMESTAMP(3)
WHERE app_id = @admin_app_id
  AND menu_code = 'admin_system_file_template_center_v1' AND deleted = 0;

INSERT INTO sys_menu (
    app_id, parent_id, menu_code, menu_name, menu_type, permission_code,
    visible, keep_alive, external_link, sort_no, status, deleted
)
SELECT @admin_app_id, page.id, item.menu_code, item.menu_name, 'BUTTON', item.permission_code,
       0, 0, 0, item.sort_no, 1, 0
FROM sys_menu page
JOIN (
    SELECT 'admin_system_file_template_detail_v1' menu_code, '查看模板详情' menu_name,
           'system:file-template:detail' permission_code, 1 sort_no
    UNION ALL SELECT 'admin_system_file_template_add_v1', '新增模板', 'system:file-template:add', 2
    UNION ALL SELECT 'admin_system_file_template_edit_v1', '编辑模板', 'system:file-template:edit', 3
    UNION ALL SELECT 'admin_system_file_template_upload_v1', '上传模板版本', 'system:file-template:upload', 4
    UNION ALL SELECT 'admin_system_file_template_publish_v1', '发布模板版本', 'system:file-template:publish', 5
    UNION ALL SELECT 'admin_system_file_template_rollback_v1', '回滚模板版本', 'system:file-template:rollback', 6
    UNION ALL SELECT 'admin_system_file_template_download_v1', '下载模板', 'system:file-template:download', 7
) item
WHERE page.app_id = @admin_app_id
  AND page.menu_code = 'admin_system_file_template_center_v1' AND page.deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM sys_menu existing
      WHERE existing.app_id = @admin_app_id AND existing.menu_code = item.menu_code AND existing.deleted = 0
  );

INSERT INTO sys_permission (
    app_id, menu_id, permission_code, permission_name, permission_type,
    resource_method, resource_path, description, status, deleted
)
SELECT @admin_app_id, menu.id, item.permission_code, item.permission_name,
       item.permission_type, item.resource_method, item.resource_path, item.description, 1, 0
FROM (
    SELECT 'admin_system_file_template_center_v1' menu_code, 'system:file-template:list' permission_code,
           '模板中心查询' permission_name, 'MENU' permission_type, 'POST' resource_method,
           '/admin/system/file-templates/search' resource_path, '分页查询系统文件模板' description
    UNION ALL SELECT 'admin_system_file_template_detail_v1', 'system:file-template:detail', '模板详情', 'BUTTON', 'GET',
           '/admin/system/file-templates/*', '查看模板版本和安全文件元数据'
    UNION ALL SELECT 'admin_system_file_template_add_v1', 'system:file-template:add', '新增模板', 'BUTTON', 'POST',
           '/admin/system/file-templates', '新增稳定模板定义'
    UNION ALL SELECT 'admin_system_file_template_edit_v1', 'system:file-template:edit', '编辑模板', 'BUTTON', 'PUT',
           '/admin/system/file-templates/*', '编辑模板可变元数据'
    UNION ALL SELECT 'admin_system_file_template_upload_v1', 'system:file-template:upload', '上传模板版本', 'BUTTON', 'POST',
           '/admin/system/file-templates/*/versions', '上传不可变中英文模板版本'
    UNION ALL SELECT 'admin_system_file_template_publish_v1', 'system:file-template:publish', '发布模板版本', 'BUTTON', 'POST',
           '/admin/system/file-templates/*/versions/*/publish', '发布草稿模板版本'
    UNION ALL SELECT 'admin_system_file_template_rollback_v1', 'system:file-template:rollback', '回滚模板版本', 'BUTTON', 'POST',
           '/admin/system/file-templates/*/versions/*/rollback', '回滚到历史已发布版本'
    UNION ALL SELECT 'admin_system_file_template_download_v1', 'system:file-template:download', '下载模板', 'BUTTON', 'GET',
           '/admin/system/file-templates/code/*/download', '受控下载当前发布模板'
) item
JOIN sys_menu menu ON menu.app_id = @admin_app_id AND menu.menu_code = item.menu_code AND menu.deleted = 0
WHERE NOT EXISTS (
    SELECT 1 FROM sys_permission existing
    WHERE existing.app_id = @admin_app_id AND existing.permission_code = item.permission_code AND existing.deleted = 0
);

INSERT IGNORE INTO sys_role_menu (app_id, role_id, menu_id, deleted)
SELECT @admin_app_id, role.id, menu.id, 0
FROM sys_role role
JOIN sys_menu menu ON menu.app_id = role.app_id
WHERE role.app_id = @admin_app_id AND role.deleted = 0
  AND role.role_code IN ('ADMIN', 'ADMIN_OPERATOR', 'SUPER_ADMIN')
  AND (menu.menu_code = 'admin_system_file_template_center_v1'
       OR menu.menu_code LIKE 'admin_system_file_template\_%\_v1');

INSERT IGNORE INTO sys_role_permission (app_id, role_id, permission_id, deleted)
SELECT @admin_app_id, role.id, permission.id, 0
FROM sys_role role
JOIN sys_permission permission ON permission.app_id = role.app_id
WHERE role.app_id = @admin_app_id AND role.deleted = 0
  AND role.role_code IN ('ADMIN', 'ADMIN_OPERATOR', 'SUPER_ADMIN')
  AND permission.permission_code LIKE 'system:file-template:%' AND permission.deleted = 0;

SET @merchant_app_id = (SELECT id FROM sys_app WHERE app_code = 'MERCHANT' AND deleted = 0 ORDER BY id LIMIT 1);

INSERT INTO sys_permission (
    app_id, menu_id, permission_code, permission_name, permission_type,
    resource_method, resource_path, description, status, deleted
)
SELECT @merchant_app_id, NULL, 'merchant:file-template:download', '系统模板下载', 'API',
       'GET', '/merchant/system/file-templates/*/download', '下载管理端发布且授权给商户系统的文件模板', 1, 0
WHERE @merchant_app_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM sys_permission existing
      WHERE existing.app_id = @merchant_app_id
        AND existing.permission_code = 'merchant:file-template:download' AND existing.deleted = 0
  );

INSERT IGNORE INTO sys_role_permission (app_id, role_id, permission_id, deleted)
SELECT @merchant_app_id, role.id, permission.id, 0
FROM sys_role role
JOIN sys_permission permission ON permission.app_id = role.app_id
WHERE role.app_id = @merchant_app_id AND role.deleted = 0
  AND (role.role_code = 'MERCHANT_ADMIN' OR role.role_code LIKE 'MERCHANT_ADMIN\_%')
  AND permission.permission_code = 'merchant:file-template:download' AND permission.deleted = 0;

COMMIT;
