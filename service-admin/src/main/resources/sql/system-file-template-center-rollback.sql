-- 仅用于模板中心尚未产生正式模板数据时的整体回滚。
-- 如三张业务表存在数据，先停止回滚并走资产迁移/归档方案，禁止直接丢失模板正文定位。
SET NAMES utf8mb4;

SELECT 'rollback_blocker' check_name,
       (SELECT COUNT(*) FROM sys_file_template) template_count,
       (SELECT COUNT(*) FROM sys_file_template_version) version_count,
       (SELECT COUNT(*) FROM sys_file_template_asset) asset_count;

DROP PROCEDURE IF EXISTS assert_template_center_empty;
DELIMITER $$
CREATE PROCEDURE assert_template_center_empty()
BEGIN
    IF (SELECT COUNT(*) FROM sys_file_template) > 0
       OR (SELECT COUNT(*) FROM sys_file_template_version) > 0
       OR (SELECT COUNT(*) FROM sys_file_template_asset) > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'template center contains data; archive or migrate assets before rollback';
    END IF;
END$$
DELIMITER ;
CALL assert_template_center_empty();
DROP PROCEDURE IF EXISTS assert_template_center_empty;

START TRANSACTION;

DELETE relation FROM sys_role_permission relation
JOIN sys_permission permission ON permission.id = relation.permission_id
WHERE permission.permission_code LIKE 'system:file-template:%'
   OR permission.permission_code = 'merchant:file-template:download';

DELETE relation FROM sys_role_menu relation
JOIN sys_menu menu ON menu.id = relation.menu_id
WHERE menu.menu_code = 'admin_system_file_template_center_v1'
   OR menu.menu_code LIKE 'admin_system_file_template\_%\_v1';

DELETE FROM sys_permission
WHERE permission_code LIKE 'system:file-template:%'
   OR permission_code = 'merchant:file-template:download';

DELETE FROM sys_menu
WHERE menu_code = 'admin_system_file_template_center_v1'
   OR menu_code LIKE 'admin_system_file_template\_%\_v1';

COMMIT;

-- assert_template_center_empty 已保证下列 DROP 只会在三张表均为空时执行。
DROP TRIGGER IF EXISTS trg_sys_file_template_asset_immutable_delete;
DROP TRIGGER IF EXISTS trg_sys_file_template_asset_immutable_update;
DROP TRIGGER IF EXISTS trg_sys_file_template_version_immutable;
DROP TRIGGER IF EXISTS trg_sys_file_template_stable_identity;
DROP TABLE IF EXISTS sys_file_template_asset;
DROP TABLE IF EXISTS sys_file_template_version;
DROP TABLE IF EXISTS sys_file_template;
