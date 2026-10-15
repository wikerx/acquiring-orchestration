-- 管理系统认证安全加固：待验证邮箱与最近密码历史。
-- 回滚建议：先回退依赖 pending_email 和密码历史的应用版本；密码历史表属于安全审计数据，建议保留而不是删除。

SET NAMES utf8mb4;

SET @pending_email_column_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'sys_account' AND column_name = 'pending_email'
);
SET @pending_email_sql = IF(
    @pending_email_column_exists = 0,
    'ALTER TABLE sys_account ADD COLUMN pending_email VARCHAR(150) NULL COMMENT ''等待所有权验证的新邮箱；验证完成前不得替换正式邮箱'' AFTER email',
    'SELECT 1'
);
PREPARE pending_email_statement FROM @pending_email_sql;
EXECUTE pending_email_statement;
DEALLOCATE PREPARE pending_email_statement;

CREATE TABLE IF NOT EXISTS sys_account_password_history (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    app_id BIGINT NOT NULL COMMENT '系统应用ID',
    account_id BIGINT NOT NULL COMMENT '登录账号ID',
    password_hash VARCHAR(255) NOT NULL COMMENT '历史密码PBKDF2哈希',
    password_salt VARCHAR(100) NOT NULL COMMENT '历史密码随机盐',
    password_algo VARCHAR(50) NOT NULL COMMENT '历史密码哈希算法',
    changed_at DATETIME(3) NOT NULL COMMENT '该密码被替换或迁移播种的时间',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_account_password_history_recent (app_id, account_id, changed_at DESC, id DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='账号历史密码哈希表';

-- 每个现有账号只播种一次当前哈希；重复执行迁移不会产生重复历史记录。
INSERT INTO sys_account_password_history (
    app_id, account_id, password_hash, password_salt, password_algo, changed_at, created_at
)
SELECT account.app_id,
       account.id,
       account.password_hash,
       account.password_salt,
       account.password_algo,
       COALESCE(account.password_updated_at, account.updated_at, account.created_at, CURRENT_TIMESTAMP(3)),
       CURRENT_TIMESTAMP(3)
FROM sys_account account
WHERE account.deleted = 0
  AND account.password_hash IS NOT NULL
  AND account.password_salt IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM sys_account_password_history history
      WHERE history.app_id = account.app_id
        AND history.account_id = account.id
  );

-- 旧的管理员改密通知包含临时明文密码变量，管理员重置已统一切换为一次性验证码流程。
DELETE FROM msg_email_template
WHERE template_code = 'ADMIN_PASSWORD_CHANGED_BY_ADMIN'
  AND system_builtin = 1;

-- 为既有找回密码模板补充直达入口；验证码仍是敏感变量，链接仅携带高熵 requestId。
UPDATE msg_email_template
SET content_template = REPLACE(
        content_template,
        '</div><div style="padding:14px 16px;background:#F8FAFC',
        '</div><div style="margin:18px 0;text-align:center;"><a href="${verificationUrl}" style="display:inline-block;padding:11px 22px;border-radius:6px;background:#2563EB;color:#FFFFFF;text-decoration:none;font-weight:700;">继续安全重置密码</a></div><div style="padding:14px 16px;background:#F8FAFC'
    ),
    variable_schema = JSON_SET(
        COALESCE(variable_schema, JSON_OBJECT()),
        '$.verificationUrl',
        'https://admin.example.com/forgot-password?requestId=example'
    ),
    version_no = version_no + 1,
    update_by = 'system',
    update_time = CURRENT_TIMESTAMP(3)
WHERE template_code = 'ADMIN_PASSWORD_RESET_VERIFY'
  AND locale = 'zh-CN'
  AND deleted = 0
  AND content_template NOT LIKE '%${verificationUrl}%';

UPDATE msg_email_template
SET content_template = REPLACE(
        content_template,
        '</div><div style="padding:14px 16px;background:#F8FAFC',
        '</div><div style="margin:18px 0;text-align:center;"><a href="${verificationUrl}" style="display:inline-block;padding:11px 22px;border-radius:6px;background:#2563EB;color:#FFFFFF;text-decoration:none;font-weight:700;">Continue secure password reset</a></div><div style="padding:14px 16px;background:#F8FAFC'
    ),
    variable_schema = JSON_SET(
        COALESCE(variable_schema, JSON_OBJECT()),
        '$.verificationUrl',
        'https://admin.example.com/forgot-password?requestId=example'
    ),
    version_no = version_no + 1,
    update_by = 'system',
    update_time = CURRENT_TIMESTAMP(3)
WHERE template_code = 'ADMIN_PASSWORD_RESET_VERIFY'
  AND locale = 'en-US'
  AND deleted = 0
  AND content_template NOT LIKE '%${verificationUrl}%';
