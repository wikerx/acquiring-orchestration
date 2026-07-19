-- 管理系统 Google Authenticator MFA 落地脚本
-- 适用：当前开发库。生产执行前请先确认 PAYMENT_MFA_SECRET 已配置并完成备份。

CREATE TABLE IF NOT EXISTS sys_account_mfa (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    app_id BIGINT NOT NULL COMMENT '系统应用ID',
    account_id BIGINT NOT NULL COMMENT '登录账号ID',
    user_id BIGINT NOT NULL COMMENT '用户主体ID',
    merchant_id VARCHAR(32) NULL COMMENT '商户号，管理后台账号为空',
    mfa_policy VARCHAR(30) NOT NULL COMMENT 'MFA策略：OPTIONAL未强制，REQUIRED强制，EXEMPT豁免',
    mfa_status VARCHAR(30) NOT NULL COMMENT 'MFA状态：NOT_ENABLED、PENDING_BIND、ENABLED、RESET_REQUIRED、EXEMPT、LOCKED、DISABLED',
    mfa_type VARCHAR(30) NOT NULL DEFAULT 'TOTP' COMMENT 'MFA类型，本期固定TOTP',
    secret_cipher VARCHAR(512) NULL COMMENT '已绑定TOTP密钥密文',
    pending_secret_cipher VARCHAR(512) NULL COMMENT '待绑定TOTP密钥密文',
    issuer VARCHAR(100) NULL COMMENT '验证器发行方',
    account_label VARCHAR(150) NULL COMMENT '验证器账号标签',
    bind_time DATETIME(3) NULL COMMENT '完成绑定时间',
    last_verify_time DATETIME(3) NULL COMMENT '最近验证成功时间',
    last_success_time_step BIGINT NULL COMMENT '最近验证成功TOTP时间步，用于防重放',
    failed_verify_count INT NOT NULL DEFAULT 0 COMMENT '连续验证失败次数',
    locked_until DATETIME(3) NULL COMMENT '临时锁定截止时间',
    reset_time DATETIME(3) NULL COMMENT '最近重置时间',
    exempt_reason VARCHAR(500) NULL COMMENT '豁免原因',
    exempt_until DATETIME(3) NULL COMMENT '豁免截止时间，空表示长期豁免',
    remark VARCHAR(500) NULL COMMENT '备注',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    created_by BIGINT NULL COMMENT '创建人ID',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '修改时间',
    updated_by BIGINT NULL COMMENT '修改人ID',
    deleted BIGINT NOT NULL DEFAULT 0 COMMENT '删除标识：0未删除，大于0为删除记录ID',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_account_mfa_account_deleted (app_id, account_id, deleted),
    KEY idx_sys_account_mfa_status (app_id, mfa_policy, mfa_status, deleted),
    KEY idx_sys_account_mfa_user (app_id, user_id, deleted),
    KEY idx_sys_account_mfa_merchant (merchant_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统账号MFA配置表';

CREATE TABLE IF NOT EXISTS sys_account_mfa_token (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    app_id BIGINT NOT NULL COMMENT '系统应用ID',
    account_id BIGINT NOT NULL COMMENT '登录账号ID',
    token_type VARCHAR(30) NOT NULL COMMENT '票据类型：LOGIN_MFA',
    token_hash VARCHAR(128) NOT NULL COMMENT '票据SHA-256哈希',
    challenge_type VARCHAR(40) NOT NULL COMMENT '挑战类型：BIND_REQUIRED、VERIFY_REQUIRED、RESET_BIND_REQUIRED',
    expire_at DATETIME(3) NOT NULL COMMENT '过期时间',
    used TINYINT NOT NULL DEFAULT 0 COMMENT '是否已使用：0否，1是',
    used_at DATETIME(3) NULL COMMENT '使用时间',
    client_ip VARCHAR(64) NULL COMMENT '客户端IP',
    user_agent VARCHAR(512) NULL COMMENT 'User-Agent',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '修改时间',
    deleted BIGINT NOT NULL DEFAULT 0 COMMENT '删除标识：0未删除，大于0为删除记录ID',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_account_mfa_token_hash_deleted (token_hash, deleted),
    KEY idx_sys_account_mfa_token_account (app_id, account_id, token_type, used, expire_at, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统账号MFA短期票据表';

CREATE TABLE IF NOT EXISTS sys_account_mfa_log (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    app_id BIGINT NOT NULL COMMENT '系统应用ID',
    account_id BIGINT NULL COMMENT '登录账号ID',
    user_id BIGINT NULL COMMENT '用户主体ID',
    merchant_id VARCHAR(32) NULL COMMENT '商户号，管理后台账号为空',
    action_type VARCHAR(50) NOT NULL COMMENT '操作类型',
    result VARCHAR(20) NOT NULL COMMENT '操作结果：SUCCESS、FAILED',
    reason VARCHAR(500) NULL COMMENT '操作原因或失败原因',
    before_policy VARCHAR(30) NULL COMMENT '变更前策略',
    before_status VARCHAR(30) NULL COMMENT '变更前状态',
    after_policy VARCHAR(30) NULL COMMENT '变更后策略',
    after_status VARCHAR(30) NULL COMMENT '变更后状态',
    operator_account_id BIGINT NULL COMMENT '操作人账号ID',
    operator_login_account VARCHAR(100) NULL COMMENT '操作人登录账号',
    client_ip VARCHAR(64) NULL COMMENT '客户端IP',
    user_agent VARCHAR(512) NULL COMMENT 'User-Agent',
    event_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '事件时间',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_sys_account_mfa_log_account_time (app_id, account_id, event_time),
    KEY idx_sys_account_mfa_log_action_time (app_id, action_type, result, event_time),
    KEY idx_sys_account_mfa_log_operator_time (operator_account_id, event_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统账号MFA安全操作日志表';

-- 上线前已有 ADMIN 用户默认不启用 OTP；新增用户由后端默认 REQUIRED + PENDING_BIND。
INSERT INTO sys_account_mfa (
    app_id, account_id, user_id, merchant_id, mfa_policy, mfa_status, mfa_type,
    issuer, account_label, failed_verify_count, created_at, updated_at, deleted
)
SELECT account.app_id,
       account.id,
       account.user_id,
       account.merchant_id,
       'OPTIONAL',
       CASE WHEN account.status = 0 THEN 'DISABLED' ELSE 'NOT_ENABLED' END,
       'TOTP',
       'Acquiring Admin',
       account.login_account,
       0,
       CURRENT_TIMESTAMP(3),
       CURRENT_TIMESTAMP(3),
       0
FROM sys_account account
JOIN sys_app app ON app.id = account.app_id AND app.app_code = 'ADMIN' AND app.deleted = 0
WHERE account.deleted = 0
  AND NOT EXISTS (
      SELECT 1
      FROM sys_account_mfa exists_mfa
      WHERE exists_mfa.app_id = account.app_id
        AND exists_mfa.account_id = account.id
        AND exists_mfa.deleted = 0
  );

INSERT INTO sys_permission (app_id, menu_id, permission_code, permission_name, permission_type, resource_method, resource_path, description, status, deleted)
SELECT 1, menu.id, item.permission_code, item.permission_name, item.permission_type, item.resource_method, item.resource_path, item.description, 1, 0
FROM sys_menu menu
JOIN (
    SELECT 'system_user' menu_code, 'sys:user:mfa:view' permission_code, '查看用户 OTP 状态' permission_name, 'BUTTON' permission_type, 'POST' resource_method, '/admin/system/users/search' resource_path, '查看用户列表中的OTP策略、状态和时间信息' description
    UNION ALL SELECT 'system_user', 'sys:user:mfa:require', '强制启用用户 OTP', 'BUTTON', 'POST', '/admin/system/users/mfa/require', '将用户OTP策略调整为强制启用'
    UNION ALL SELECT 'system_user', 'sys:user:mfa:exempt', '配置用户 OTP 豁免', 'BUTTON', 'POST', '/admin/system/users/mfa/exempt', '明确配置指定用户无需OTP验证'
    UNION ALL SELECT 'system_user', 'sys:user:mfa:disable', '停用用户 OTP', 'BUTTON', 'POST', '/admin/system/users/mfa/disable', '将用户OTP恢复为未启用'
    UNION ALL SELECT 'system_user', 'sys:user:mfa:reset', '重置用户 OTP', 'BUTTON', 'POST', '/admin/system/users/mfa/reset', '废弃旧OTP密钥并要求重新绑定'
    UNION ALL SELECT 'system_user', 'sys:user:mfa:unlock', '解锁用户 OTP', 'BUTTON', 'POST', '/admin/system/users/mfa/unlock', '解除连续失败导致的OTP临时锁定'
    UNION ALL SELECT 'system_user', 'sys:user:mfa:resend', '重发 OTP 绑定邮件', 'BUTTON', 'POST', '/admin/system/users/mfa/resend-bind-mail', '重新发送绑定引导邮件'
    UNION ALL SELECT 'system_user', 'sys:user:mfa:log', '查看用户 OTP 日志', 'BUTTON', 'POST', '/admin/system/users/mfa/logs/search', '查询用户OTP相关安全审计日志'
) item ON item.menu_code = menu.menu_code
WHERE menu.app_id = 1
  AND menu.deleted = 0
  AND NOT EXISTS (
      SELECT 1
      FROM sys_permission exists_permission
      WHERE exists_permission.app_id = 1
        AND exists_permission.permission_code = item.permission_code
        AND exists_permission.deleted = 0
  );

UPDATE sys_permission permission
JOIN (
    SELECT 'sys:user:mfa:view' permission_code, 'POST' resource_method, '/admin/system/users/search' resource_path
    UNION ALL SELECT 'sys:user:mfa:require', 'POST', '/admin/system/users/mfa/require'
    UNION ALL SELECT 'sys:user:mfa:exempt', 'POST', '/admin/system/users/mfa/exempt'
    UNION ALL SELECT 'sys:user:mfa:disable', 'POST', '/admin/system/users/mfa/disable'
    UNION ALL SELECT 'sys:user:mfa:reset', 'POST', '/admin/system/users/mfa/reset'
    UNION ALL SELECT 'sys:user:mfa:unlock', 'POST', '/admin/system/users/mfa/unlock'
    UNION ALL SELECT 'sys:user:mfa:resend', 'POST', '/admin/system/users/mfa/resend-bind-mail'
    UNION ALL SELECT 'sys:user:mfa:log', 'POST', '/admin/system/users/mfa/logs/search'
) item ON item.permission_code = permission.permission_code
JOIN sys_menu menu ON menu.app_id = permission.app_id AND menu.menu_code = 'system_user' AND menu.deleted = 0
SET permission.menu_id = menu.id,
    permission.resource_method = item.resource_method,
    permission.resource_path = item.resource_path,
    permission.updated_at = CURRENT_TIMESTAMP(3)
WHERE permission.app_id = 1
  AND permission.deleted = 0;

-- 默认给超级管理员角色补齐 OTP 管理权限。
INSERT INTO sys_role_permission (app_id, role_id, permission_id, created_at, deleted)
SELECT role.app_id, role.id, permission.id, CURRENT_TIMESTAMP(3), 0
FROM sys_role role
JOIN sys_permission permission ON permission.app_id = role.app_id
WHERE role.app_id = 1
  AND role.deleted = 0
  AND permission.deleted = 0
  AND role.role_code IN ('SUPER_ADMIN', 'ADMIN', 'ADMIN_OPERATOR')
  AND permission.permission_code IN (
      'sys:user:mfa:view',
      'sys:user:mfa:require',
      'sys:user:mfa:exempt',
      'sys:user:mfa:disable',
      'sys:user:mfa:reset',
      'sys:user:mfa:unlock',
      'sys:user:mfa:resend',
      'sys:user:mfa:log'
  )
  AND NOT EXISTS (
      SELECT 1
      FROM sys_role_permission exists_relation
      WHERE exists_relation.app_id = role.app_id
        AND exists_relation.role_id = role.id
        AND exists_relation.permission_id = permission.id
        AND exists_relation.deleted = 0
  );

INSERT INTO msg_email_template (
    template_code, template_name, app_code, scene_code, locale, subject_template, content_type,
    content_template, variable_schema, sensitive_variable_names, status, system_builtin, version_no,
    remark, create_by, update_by, deleted
)
SELECT item.template_code, item.template_name, 'ADMIN', 'ADMIN_MFA', 'zh-CN', item.subject_template, 'HTML',
       item.content_template, item.variable_schema, '[]', 1, 1, 1,
       item.remark, 'system', 'system', 0
FROM (
    SELECT 'ADMIN_MFA_BIND_NOTICE' template_code,
           '管理系统 OTP 绑定通知' template_name,
           '【Vexra Admin】请绑定 Google 动态验证码' subject_template,
           '<div style="margin:0;padding:36px;background:#eef3f8;font-family:Arial,''Microsoft YaHei'',sans-serif;color:#182230;"><div style="max-width:640px;margin:0 auto;background:#ffffff;border:1px solid #d9e2ec;border-radius:10px;overflow:hidden;"><div style="padding:26px 30px;background:#132238;color:#ffffff;"><div style="font-size:12px;letter-spacing:.12em;text-transform:uppercase;color:#9fb5cc;">Vexra Admin Security</div><div style="margin-top:8px;font-size:24px;font-weight:700;">Google 动态验证码绑定</div></div><div style="padding:30px;line-height:1.8;font-size:14px;"><p style="margin:0 0 14px;">您好，${loginAccount}：</p><p style="margin:0 0 14px;">您的管理系统账号已开启 Google 动态验证码。请打开管理系统登录页，完成账号密码校验后按页面提示扫码绑定。</p><div style="margin:22px 0;padding:18px 20px;background:#f8fafc;border:1px solid #dbe5ef;border-radius:8px;"><p style="margin:0 0 8px;">登录账号：<strong>${loginAccount}</strong></p><p style="margin:0 0 8px;">登录地址：<a href="${bindUrl}" style="color:#2563eb;text-decoration:none;">${bindUrl}</a></p><p style="margin:0;">操作原因：${reason}</p></div><p style="margin:0 0 10px;">请使用 Google Authenticator 或其他兼容 RFC 6238 的验证器应用。邮件不会包含 OTP 密钥、二维码或动态验证码。</p><p style="margin:0;color:#9a3412;">如非本人或授权管理员发起，请立即联系系统管理员。</p></div><div style="padding:16px 30px;background:#f8fafc;color:#64748b;font-size:12px;">Vexra Admin 安全通知，请勿转发。</div></div></div>' content_template,
           '{"loginAccount":"admin@example.com","bindUrl":"https://admin.example.com/login","reason":"安全策略启用"}' variable_schema,
           '系统内置模板：管理系统 OTP 绑定通知' remark
    UNION ALL SELECT 'ADMIN_MFA_ENABLED_NOTICE',
           '管理系统 OTP 启用通知',
           '【Vexra Admin】Google 动态验证码已开启',
           '<div style="margin:0;padding:36px;background:#eef3f8;font-family:Arial,''Microsoft YaHei'',sans-serif;color:#182230;"><div style="max-width:640px;margin:0 auto;background:#ffffff;border:1px solid #d9e2ec;border-radius:10px;overflow:hidden;"><div style="padding:26px 30px;background:#0f766e;color:#ffffff;"><div style="font-size:12px;letter-spacing:.12em;text-transform:uppercase;color:#c7f9f2;">Vexra Admin Security</div><div style="margin-top:8px;font-size:24px;font-weight:700;">Google 动态验证码已开启</div></div><div style="padding:30px;line-height:1.8;font-size:14px;"><p style="margin:0 0 14px;">您好，${loginAccount}：</p><p style="margin:0 0 14px;">管理员已为您的管理系统账号开启 Google 动态验证码。下次登录时，您需要先完成绑定，再进入系统。</p><div style="margin:22px 0;padding:18px 20px;background:#f0fdfa;border:1px solid #99f6e4;border-radius:8px;"><p style="margin:0 0 8px;">登录账号：<strong>${loginAccount}</strong></p><p style="margin:0;">操作原因：${reason}</p></div><p style="margin:0;">请前往 <a href="${bindUrl}" style="color:#2563eb;text-decoration:none;">管理系统登录页</a> 完成绑定。</p></div><div style="padding:16px 30px;background:#f8fafc;color:#64748b;font-size:12px;">Vexra Admin 安全通知，请勿转发。</div></div></div>',
           '{"loginAccount":"admin@example.com","bindUrl":"https://admin.example.com/login","reason":"安全策略启用"}',
           '系统内置模板：管理系统 OTP 启用通知'
    UNION ALL SELECT 'ADMIN_MFA_RESET_NOTICE',
           '管理系统 OTP 重置通知',
           '【Vexra Admin】Google 动态验证码已重置',
           '<div style="margin:0;padding:36px;background:#eef3f8;font-family:Arial,''Microsoft YaHei'',sans-serif;color:#182230;"><div style="max-width:640px;margin:0 auto;background:#ffffff;border:1px solid #d9e2ec;border-radius:10px;overflow:hidden;"><div style="padding:26px 30px;background:#92400e;color:#ffffff;"><div style="font-size:12px;letter-spacing:.12em;text-transform:uppercase;color:#fde68a;">Vexra Admin Security</div><div style="margin-top:8px;font-size:24px;font-weight:700;">Google 动态验证码已重置</div></div><div style="padding:30px;line-height:1.8;font-size:14px;"><p style="margin:0 0 14px;">您好，${loginAccount}：</p><p style="margin:0 0 14px;">您的原 Google 动态验证码密钥已失效。下次登录时必须重新绑定新的验证器。</p><div style="margin:22px 0;padding:18px 20px;background:#fffbeb;border:1px solid #fde68a;border-radius:8px;"><p style="margin:0 0 8px;">登录账号：<strong>${loginAccount}</strong></p><p style="margin:0;">重置原因：${reason}</p></div><p style="margin:0;">请前往 <a href="${bindUrl}" style="color:#2563eb;text-decoration:none;">管理系统登录页</a> 重新绑定。</p></div><div style="padding:16px 30px;background:#f8fafc;color:#64748b;font-size:12px;">Vexra Admin 安全通知，请勿转发。</div></div></div>',
           '{"loginAccount":"admin@example.com","bindUrl":"https://admin.example.com/login","reason":"用户更换设备"}',
           '系统内置模板：管理系统 OTP 重置通知'
    UNION ALL SELECT 'ADMIN_MFA_DISABLED_NOTICE',
           '管理系统 OTP 停用通知',
           '【Vexra Admin】Google 动态验证码已停用',
           '<div style="margin:0;padding:36px;background:#eef3f8;font-family:Arial,''Microsoft YaHei'',sans-serif;color:#182230;"><div style="max-width:640px;margin:0 auto;background:#ffffff;border:1px solid #d9e2ec;border-radius:10px;overflow:hidden;"><div style="padding:26px 30px;background:#334155;color:#ffffff;"><div style="font-size:12px;letter-spacing:.12em;text-transform:uppercase;color:#cbd5e1;">Vexra Admin Security</div><div style="margin-top:8px;font-size:24px;font-weight:700;">Google 动态验证码已停用</div></div><div style="padding:30px;line-height:1.8;font-size:14px;"><p style="margin:0 0 14px;">您好，${loginAccount}：</p><p style="margin:0 0 14px;">管理员已停用您账号的 Google 动态验证码要求。</p><div style="margin:22px 0;padding:18px 20px;background:#f8fafc;border:1px solid #dbe5ef;border-radius:8px;"><p style="margin:0 0 8px;">登录账号：<strong>${loginAccount}</strong></p><p style="margin:0;">操作原因：${reason}</p></div><p style="margin:0;color:#9a3412;">如果您不清楚该变更来源，请联系系统管理员确认。</p></div><div style="padding:16px 30px;background:#f8fafc;color:#64748b;font-size:12px;">Vexra Admin 安全通知，请勿转发。</div></div></div>',
           '{"loginAccount":"admin@example.com","reason":"特殊账号调整"}',
           '系统内置模板：管理系统 OTP 停用通知'
    UNION ALL SELECT 'ADMIN_MFA_EXEMPT_NOTICE',
           '管理系统 OTP 豁免通知',
           '【Vexra Admin】Google 动态验证码豁免已配置',
           '<div style="margin:0;padding:36px;background:#eef3f8;font-family:Arial,''Microsoft YaHei'',sans-serif;color:#182230;"><div style="max-width:640px;margin:0 auto;background:#ffffff;border:1px solid #d9e2ec;border-radius:10px;overflow:hidden;"><div style="padding:26px 30px;background:#581c87;color:#ffffff;"><div style="font-size:12px;letter-spacing:.12em;text-transform:uppercase;color:#e9d5ff;">Vexra Admin Security</div><div style="margin-top:8px;font-size:24px;font-weight:700;">Google 动态验证码豁免</div></div><div style="padding:30px;line-height:1.8;font-size:14px;"><p style="margin:0 0 14px;">您好，${loginAccount}：</p><p style="margin:0 0 14px;">管理员已为您的管理系统账号配置 Google 动态验证码豁免。</p><div style="margin:22px 0;padding:18px 20px;background:#faf5ff;border:1px solid #e9d5ff;border-radius:8px;"><p style="margin:0 0 8px;">登录账号：<strong>${loginAccount}</strong></p><p style="margin:0 0 8px;">豁免有效期：${exemptUntil}</p><p style="margin:0;">豁免原因：${reason}</p></div><p style="margin:0;color:#9a3412;">豁免账号仍应使用强密码，并限制共享和转借。</p></div><div style="padding:16px 30px;background:#f8fafc;color:#64748b;font-size:12px;">Vexra Admin 安全通知，请勿转发。</div></div></div>',
           '{"loginAccount":"admin@example.com","reason":"应急账号","exemptUntil":"长期有效"}',
           '系统内置模板：管理系统 OTP 豁免通知'
) item
WHERE NOT EXISTS (
    SELECT 1
    FROM msg_email_template exists_template
    WHERE exists_template.template_code = item.template_code
      AND exists_template.locale = 'zh-CN'
      AND exists_template.deleted = 0
);
