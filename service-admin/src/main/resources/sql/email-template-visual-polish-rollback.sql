-- 回滚 2026-10-14 系统内置邮件模板字体栈治理。
-- 只恢复本次由旧字体栈升级的模板，避免覆盖迁移后发生的其他模板编辑。

SET NAMES utf8mb4;
START TRANSACTION;

UPDATE msg_email_template current_template
JOIN msg_email_template_visual_backup_20261014 backup
  ON backup.id = current_template.id
SET current_template.content_template = backup.content_template,
    current_template.version_no = backup.version_no,
    current_template.update_by = backup.update_by,
    current_template.update_time = backup.update_time
WHERE current_template.system_builtin = 1
  AND current_template.status = 1
  AND current_template.deleted = 0
  AND current_template.content_template LIKE '%font-family:Arial,''Helvetica Neue'',Helvetica,''PingFang SC'',''Microsoft YaHei'',sans-serif%'
  AND backup.content_template NOT LIKE '%font-family:Arial,''Helvetica Neue'',Helvetica,''PingFang SC'',''Microsoft YaHei'',sans-serif%';

DROP TABLE msg_email_template_visual_backup_20261014;

COMMIT;
