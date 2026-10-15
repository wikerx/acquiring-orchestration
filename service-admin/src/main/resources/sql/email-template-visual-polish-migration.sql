-- 系统内置邮件模板字体栈治理。
-- 仅统一当前启用的系统模板字体，不改变用户自建模板、业务文案或模板变量。

SET NAMES utf8mb4;
START TRANSACTION;

CREATE TABLE IF NOT EXISTS msg_email_template_visual_backup_20261014 LIKE msg_email_template;

INSERT IGNORE INTO msg_email_template_visual_backup_20261014
SELECT template.*
FROM msg_email_template template
WHERE template.system_builtin = 1
  AND template.status = 1
  AND template.deleted = 0;

UPDATE msg_email_template template
SET template.content_template = REPLACE(
        REPLACE(
            REPLACE(
                template.content_template,
                'font-family:Arial,''Microsoft YaHei'',sans-serif',
                'font-family:Arial,''Helvetica Neue'',Helvetica,''PingFang SC'',''Microsoft YaHei'',sans-serif'
            ),
            'font-family:Arial,Microsoft YaHei,sans-serif',
            'font-family:Arial,''Helvetica Neue'',Helvetica,''PingFang SC'',''Microsoft YaHei'',sans-serif'
        ),
        'font-family:Arial,sans-serif',
        'font-family:Arial,''Helvetica Neue'',Helvetica,''PingFang SC'',''Microsoft YaHei'',sans-serif'
    ),
    template.version_no = template.version_no + 1,
    template.update_by = 'system',
    template.update_time = CURRENT_TIMESTAMP(3)
WHERE template.system_builtin = 1
  AND template.status = 1
  AND template.deleted = 0
  AND (
      template.content_template LIKE '%font-family:Arial,sans-serif%'
      OR template.content_template LIKE '%font-family:Arial,''Microsoft YaHei'',sans-serif%'
      OR template.content_template LIKE '%font-family:Arial,Microsoft YaHei,sans-serif%'
  );

COMMIT;
