-- 回滚 2026-08-20 系统邮件蓝白主题迁移。
-- 只恢复迁移时已经备份且当前仍带迁移标记的模板记录。

SET NAMES utf8mb4;
START TRANSACTION;

UPDATE msg_email_template current_template
JOIN msg_email_template_blue_white_backup_20260820 backup
  ON backup.id = current_template.id
SET current_template.template_name = backup.template_name,
    current_template.app_code = backup.app_code,
    current_template.scene_code = backup.scene_code,
    current_template.locale = backup.locale,
    current_template.subject_template = backup.subject_template,
    current_template.content_type = backup.content_type,
    current_template.content_template = backup.content_template,
    current_template.variable_schema = backup.variable_schema,
    current_template.sensitive_variable_names = backup.sensitive_variable_names,
    current_template.status = backup.status,
    current_template.system_builtin = backup.system_builtin,
    current_template.version_no = backup.version_no,
    current_template.remark = backup.remark,
    current_template.update_by = backup.update_by,
    current_template.update_time = backup.update_time,
    current_template.deleted = backup.deleted
WHERE current_template.content_template LIKE '%data-template-theme="vexra-blue-white-v1"%';

DROP TABLE msg_email_template_blue_white_backup_20260820;

COMMIT;
