SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

CREATE TABLE IF NOT EXISTS sys_config (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    config_name VARCHAR(100) NOT NULL COMMENT '参数名称',
    config_key VARCHAR(150) NOT NULL COMMENT '参数键名，全局唯一，如 sys.user.init_password',
    config_value TEXT NULL COMMENT '参数键值，支持普通文本或JSON字符串',
    value_type TINYINT NOT NULL DEFAULT 1 COMMENT '值类型：1字符串，2数字，3布尔，4JSON',
    config_group VARCHAR(64) NULL COMMENT '配置分组，如 system、merchant、risk、settlement',
    system_builtin TINYINT NOT NULL DEFAULT 0 COMMENT '是否系统内置：0否，1是',
    visible TINYINT NOT NULL DEFAULT 1 COMMENT '是否前端可见：0否，1是',
    encrypted TINYINT NOT NULL DEFAULT 0 COMMENT '是否加密存储：0否，1是；密钥类配置不建议放本表',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0停用，1启用',
    remark VARCHAR(500) NULL COMMENT '备注说明',
    created_by VARCHAR(64) NULL COMMENT '创建人',
    updated_by VARCHAR(64) NULL COMMENT '更新人',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '修改时间',
    deleted BIGINT NOT NULL DEFAULT 0 COMMENT '删除标识：0未删除，大于0为删除记录ID',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_config_key_deleted (config_key, deleted),
    KEY idx_sys_config_group_status (config_group, status, deleted),
    KEY idx_sys_config_name (config_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统参数配置表';

INSERT INTO sys_config (
    config_name,
    config_key,
    config_value,
    value_type,
    config_group,
    system_builtin,
    visible,
    encrypted,
    status,
    remark,
    created_by,
    updated_by,
    deleted
) VALUES
    ('网关 Gateway Base 地址', 'platform.gateway.base-url', 'http://127.0.0.1:8000', 1, 'platform_url', 1, 1, 0, 1, '邮件模板变量：${gatewayBaseUrl}', 'system', 'system', 0),
    ('收银台前端 Base 地址', 'platform.checkout.frontend-base-url', 'http://127.0.0.1:5175', 1, 'platform_url', 1, 1, 0, 1, '邮件模板变量：${checkoutBaseUrl}', 'system', 'system', 0),
    ('商户系统 Base 地址', 'platform.merchant.frontend-base-url', 'http://127.0.0.1:5174', 1, 'platform_url', 1, 1, 0, 1, '邮件模板变量：${merchantSystemBaseUrl}', 'system', 'system', 0),
    ('管理系统 Base 地址', 'platform.admin.frontend-base-url', 'http://127.0.0.1:5173', 1, 'platform_url', 1, 1, 0, 1, '邮件模板变量：${adminSystemBaseUrl}', 'system', 'system', 0)
ON DUPLICATE KEY UPDATE
    config_name = VALUES(config_name),
    value_type = VALUES(value_type),
    config_group = VALUES(config_group),
    system_builtin = VALUES(system_builtin),
    visible = VALUES(visible),
    encrypted = VALUES(encrypted),
    status = VALUES(status),
    remark = VALUES(remark),
    updated_by = VALUES(updated_by),
    updated_at = CURRENT_TIMESTAMP(3);

CREATE TABLE IF NOT EXISTS sys_dict_type (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    dict_name VARCHAR(100) NOT NULL COMMENT '字典名称，如商户状态、风险等级',
    dict_type VARCHAR(100) NOT NULL COMMENT '字典类型编码，如 merchant_status',
    biz_domain VARCHAR(64) NULL COMMENT '业务域：system、merchant、payment、risk、settlement',
    system_builtin TINYINT NOT NULL DEFAULT 0 COMMENT '是否系统内置：0否，1是',
    editable TINYINT NOT NULL DEFAULT 1 COMMENT '是否允许编辑：0否，1是',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0停用，1启用',
    remark VARCHAR(500) NULL COMMENT '备注',
    created_by VARCHAR(64) NULL COMMENT '创建人',
    updated_by VARCHAR(64) NULL COMMENT '更新人',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '修改时间',
    deleted BIGINT NOT NULL DEFAULT 0 COMMENT '删除标识：0未删除，大于0为删除记录ID',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_dict_type_deleted (dict_type, deleted),
    KEY idx_sys_dict_type_domain_status (biz_domain, status, deleted),
    KEY idx_sys_dict_type_name (dict_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='字典类型表';

CREATE TABLE IF NOT EXISTS sys_dict_data (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    dict_type VARCHAR(100) NOT NULL COMMENT '字典类型编码，对应 sys_dict_type.dict_type',
    dict_label VARCHAR(100) NOT NULL COMMENT '字典标签，前端展示值',
    dict_value VARCHAR(100) NOT NULL COMMENT '字典键值，业务实际值',
    parent_value VARCHAR(100) NULL COMMENT '父级字典值，用于层级字典',
    locale VARCHAR(16) NOT NULL DEFAULT 'zh-CN' COMMENT '语言区域，如 zh-CN、en-US',
    dict_sort INT NOT NULL DEFAULT 0 COMMENT '排序，值越小越靠前',
    list_class VARCHAR(100) NULL COMMENT '展示样式：default、primary、success、warning、danger',
    extra_json TEXT NULL COMMENT '扩展属性JSON，如图标、颜色、渠道映射值',
    is_default TINYINT NOT NULL DEFAULT 0 COMMENT '是否默认：0否，1是',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0停用，1启用',
    remark VARCHAR(500) NULL COMMENT '备注',
    created_by VARCHAR(64) NULL COMMENT '创建人',
    updated_by VARCHAR(64) NULL COMMENT '更新人',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '修改时间',
    deleted BIGINT NOT NULL DEFAULT 0 COMMENT '删除标识：0未删除，大于0为删除记录ID',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_dict_value_deleted (dict_type, dict_value, locale, deleted),
    KEY idx_sys_dict_type_status_sort (dict_type, status, dict_sort, deleted),
    KEY idx_sys_dict_parent (dict_type, parent_value, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='字典数据表';

CREATE TABLE IF NOT EXISTS sys_oper_log (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    trace_id VARCHAR(64) NULL COMMENT '链路追踪ID',
    request_id VARCHAR(64) NULL COMMENT '请求ID',
    message_id VARCHAR(64) NULL COMMENT 'MQ消息唯一标识',
    idempotent_key VARCHAR(255) NULL COMMENT '消费幂等键',
    system_code VARCHAR(32) NULL COMMENT '系统编码，区分 ADMIN 和 MERCHANT',
    merchant_id VARCHAR(32) NULL COMMENT '商户号，后台操作涉及商户时记录',
    module_name VARCHAR(100) NULL COMMENT '模块名称，如商户管理、费率管理、系统配置',
    operation_name VARCHAR(100) NULL COMMENT '操作名称',
    business_type TINYINT NULL COMMENT '业务类型：1新增，2修改，3删除，4查询，5导出，6审核，7冻结，8解冻',
    method_name VARCHAR(255) NULL COMMENT '后端方法名称',
    request_method VARCHAR(20) NULL COMMENT '请求方式：GET、POST、PUT、DELETE',
    operator_type TINYINT NOT NULL DEFAULT 1 COMMENT '操作人类别：1后台用户，2商户用户，3系统任务',
    operator_id VARCHAR(64) NULL COMMENT '操作人ID',
    operator_name VARCHAR(100) NULL COMMENT '操作人名称',
    oper_url VARCHAR(500) NULL COMMENT '请求URL',
    oper_ip VARCHAR(45) NULL COMMENT '操作IP，支持IPv4/IPv6',
    oper_location VARCHAR(255) NULL COMMENT '操作地点',
    store_id VARCHAR(64) NULL COMMENT '店铺号',
    user_agent VARCHAR(512) NULL COMMENT '浏览器User-Agent',
    request_param TEXT NULL COMMENT '脱敏后的请求参数，禁止记录密钥、卡号、CVV、JWT明文',
    response_result TEXT NULL COMMENT '脱敏后的响应结果',
    cost_time BIGINT NULL COMMENT '执行时长，单位毫秒',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '操作状态：0失败，1成功',
    error_code VARCHAR(32) NULL COMMENT '错误码',
    error_msg VARCHAR(1000) NULL COMMENT '错误信息，禁止写入堆栈明文',
    operated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '操作时间',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_oper_idempotent_key (idempotent_key),
    KEY idx_sys_oper_trace_id (trace_id),
    KEY idx_sys_oper_request_id (request_id),
    KEY idx_sys_oper_message_id (message_id),
    KEY idx_sys_oper_merchant_time (merchant_id, operated_at),
    KEY idx_sys_oper_operator_time (operator_id, operated_at),
    KEY idx_sys_oper_time_status (operated_at, status),
    KEY idx_sys_oper_business_type (business_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统后台操作日志表';

CREATE TABLE IF NOT EXISTS sys_app (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    app_code VARCHAR(50) NOT NULL COMMENT '系统编码：ADMIN管理后台，MERCHANT商户系统',
    app_name VARCHAR(100) NOT NULL COMMENT '系统名称',
    app_type VARCHAR(30) NOT NULL COMMENT '系统类型：ADMIN管理端，MERCHANT商户端',
    domain_url VARCHAR(255) NULL COMMENT '系统访问域名或地址',
    description VARCHAR(500) NULL COMMENT '系统说明',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0停用，1启用',
    sort_no INT NOT NULL DEFAULT 0 COMMENT '排序号',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    created_by BIGINT NULL COMMENT '创建人ID',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '修改时间',
    updated_by BIGINT NULL COMMENT '修改人ID',
    deleted BIGINT NOT NULL DEFAULT 0 COMMENT '删除标识：0未删除，大于0为删除记录ID',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_app_code_deleted (app_code, deleted),
    KEY idx_sys_app_type_status (app_type, status, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统应用表';

CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    user_type VARCHAR(30) NOT NULL COMMENT '用户类型：PLATFORM平台用户，MERCHANT商户用户',
    real_name VARCHAR(100) NULL COMMENT '真实姓名',
    nickname VARCHAR(100) NULL COMMENT '昵称',
    mobile VARCHAR(30) NULL COMMENT '主体手机号',
    email VARCHAR(150) NULL COMMENT '主体邮箱',
    avatar_url VARCHAR(500) NULL COMMENT '头像地址',
    country_code VARCHAR(10) NULL COMMENT '国家地区编码',
    language VARCHAR(20) NULL COMMENT '用户语言，如 zh-CN、en-US',
    timezone VARCHAR(50) NULL COMMENT '用户时区',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0停用，1启用',
    remark VARCHAR(500) NULL COMMENT '备注',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    created_by BIGINT NULL COMMENT '创建人ID',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '修改时间',
    updated_by BIGINT NULL COMMENT '修改人ID',
    deleted BIGINT NOT NULL DEFAULT 0 COMMENT '删除标识：0未删除，大于0为删除记录ID',
    PRIMARY KEY (id),
    KEY idx_sys_user_type_status (user_type, status, deleted),
    KEY idx_sys_user_mobile (mobile),
    KEY idx_sys_user_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户主体表';

CREATE TABLE IF NOT EXISTS sys_account (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    app_id BIGINT NOT NULL COMMENT '系统应用ID',
    user_id BIGINT NOT NULL COMMENT '用户主体ID',
    merchant_id VARCHAR(32) NULL COMMENT '商户号，商户系统账号必须绑定已有 base_merchant_info',
    login_account VARCHAR(100) NOT NULL COMMENT '登录账号',
    password_hash VARCHAR(255) NOT NULL COMMENT '密码哈希值',
    password_salt VARCHAR(100) NOT NULL COMMENT '密码盐值',
    password_algo VARCHAR(50) NOT NULL COMMENT '密码算法，如 PBKDF2WithHmacSHA256',
    mobile VARCHAR(30) NULL COMMENT '该系统登录手机号',
    email VARCHAR(150) NULL COMMENT '该系统登录邮箱',
    mfa_enabled TINYINT NOT NULL DEFAULT 0 COMMENT '是否开启MFA：0否，1是',
    mfa_type VARCHAR(30) NULL COMMENT 'MFA类型：SMS短信，EMAIL邮箱，TOTP身份验证器',
    totp_secret VARCHAR(255) NULL COMMENT 'TOTP密钥，生产环境建议加密存储',
    password_expired TINYINT NOT NULL DEFAULT 0 COMMENT '密码是否过期：0否，1是',
    password_updated_at DATETIME(3) NULL COMMENT '密码更新时间',
    last_login_at DATETIME(3) NULL COMMENT '最后登录时间',
    last_login_ip VARCHAR(64) NULL COMMENT '最后登录IP',
    failed_login_count INT NOT NULL DEFAULT 0 COMMENT '连续登录失败次数',
    locked TINYINT NOT NULL DEFAULT 0 COMMENT '是否锁定：0否，1是',
    locked_at DATETIME(3) NULL COMMENT '锁定时间',
    locked_reason VARCHAR(255) NULL COMMENT '锁定原因',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0停用，1启用',
    remark VARCHAR(500) NULL COMMENT '备注',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    created_by BIGINT NULL COMMENT '创建人ID',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '修改时间',
    updated_by BIGINT NULL COMMENT '修改人ID',
    deleted BIGINT NOT NULL DEFAULT 0 COMMENT '删除标识：0未删除，大于0为删除记录ID',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_account_app_login_deleted (app_id, login_account, deleted),
    UNIQUE KEY uk_sys_account_app_user_deleted (app_id, user_id, deleted),
    KEY idx_sys_account_merchant (merchant_id, status, deleted),
    KEY idx_sys_account_status_locked (status, locked, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统登录账号表';

CREATE TABLE IF NOT EXISTS sys_verify_code (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    app_id BIGINT NOT NULL COMMENT '系统应用ID',
    scene VARCHAR(50) NOT NULL COMMENT '验证码场景：LOGIN登录，REGISTER注册，RESET_PASSWORD重置密码',
    receiver_type VARCHAR(20) NOT NULL COMMENT '接收方式：SMS短信，EMAIL邮箱，TOTP身份验证器',
    receiver VARCHAR(150) NOT NULL COMMENT '接收人手机号或邮箱',
    code_hash VARCHAR(255) NOT NULL COMMENT '验证码哈希值，不保存明文验证码',
    code_salt VARCHAR(100) NULL COMMENT '验证码盐值',
    expire_at DATETIME(3) NOT NULL COMMENT '过期时间',
    used TINYINT NOT NULL DEFAULT 0 COMMENT '是否已使用：0否，1是',
    used_at DATETIME(3) NULL COMMENT '使用时间',
    verify_count INT NOT NULL DEFAULT 0 COMMENT '验证次数',
    send_ip VARCHAR(64) NULL COMMENT '发送请求IP',
    send_channel VARCHAR(50) NULL COMMENT '发送渠道',
    send_status TINYINT NOT NULL DEFAULT 1 COMMENT '发送状态：0失败，1成功',
    send_fail_reason VARCHAR(500) NULL COMMENT '发送失败原因',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_sys_verify_app_scene_receiver (app_id, scene, receiver),
    KEY idx_sys_verify_expire_used (expire_at, used)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='动态验证码表';

CREATE TABLE IF NOT EXISTS sys_role (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    app_id BIGINT NOT NULL COMMENT '系统应用ID',
    role_code VARCHAR(80) NOT NULL COMMENT '角色编码',
    role_name VARCHAR(100) NOT NULL COMMENT '角色名称',
    role_type VARCHAR(30) NOT NULL DEFAULT 'CUSTOM' COMMENT '角色类型：SYSTEM系统内置，CUSTOM自定义',
    data_scope VARCHAR(30) NOT NULL DEFAULT 'SELF' COMMENT '数据范围：ALL全部，SELF本人，CUSTOM自定义',
    description VARCHAR(500) NULL COMMENT '角色说明',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0停用，1启用',
    sort_no INT NOT NULL DEFAULT 0 COMMENT '排序号',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    created_by BIGINT NULL COMMENT '创建人ID',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '修改时间',
    updated_by BIGINT NULL COMMENT '修改人ID',
    deleted BIGINT NOT NULL DEFAULT 0 COMMENT '删除标识：0未删除，大于0为删除记录ID',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_role_app_code_deleted (app_id, role_code, deleted),
    KEY idx_sys_role_app_status (app_id, status, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色表';

CREATE TABLE IF NOT EXISTS sys_menu (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    app_id BIGINT NOT NULL COMMENT '系统应用ID',
    parent_id BIGINT NOT NULL DEFAULT 0 COMMENT '父级菜单ID，0为顶级',
    menu_code VARCHAR(100) NOT NULL COMMENT '菜单编码',
    menu_name VARCHAR(100) NOT NULL COMMENT '菜单名称',
    menu_type VARCHAR(30) NOT NULL COMMENT '菜单类型：CATALOG目录，MENU菜单，BUTTON按钮，LINK外链',
    route_path VARCHAR(255) NULL COMMENT '前端路由路径',
    component_path VARCHAR(255) NULL COMMENT '前端组件路径',
    permission_code VARCHAR(150) NULL COMMENT '权限标识，前端按钮鉴权使用',
    icon VARCHAR(100) NULL COMMENT '菜单图标',
    redirect VARCHAR(255) NULL COMMENT '重定向地址',
    visible TINYINT NOT NULL DEFAULT 1 COMMENT '是否显示：0隐藏，1显示',
    keep_alive TINYINT NOT NULL DEFAULT 0 COMMENT '是否缓存页面：0否，1是',
    external_link TINYINT NOT NULL DEFAULT 0 COMMENT '是否外链：0否，1是',
    sort_no INT NOT NULL DEFAULT 0 COMMENT '排序号',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0停用，1启用',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    created_by BIGINT NULL COMMENT '创建人ID',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '修改时间',
    updated_by BIGINT NULL COMMENT '修改人ID',
    deleted BIGINT NOT NULL DEFAULT 0 COMMENT '删除标识：0未删除，大于0为删除记录ID',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_menu_app_code_deleted (app_id, menu_code, deleted),
    KEY idx_sys_menu_app_parent (app_id, parent_id, status, deleted),
    KEY idx_sys_menu_permission (permission_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='菜单表';

CREATE TABLE IF NOT EXISTS sys_permission (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    app_id BIGINT NOT NULL COMMENT '系统应用ID',
    menu_id BIGINT NULL COMMENT '归属菜单ID，可为空',
    permission_code VARCHAR(150) NOT NULL COMMENT '权限编码，如 merchant:user:create',
    permission_name VARCHAR(100) NOT NULL COMMENT '权限名称',
    permission_type VARCHAR(30) NOT NULL COMMENT '权限类型：MENU菜单，BUTTON按钮，API接口，DATA数据',
    resource_method VARCHAR(20) NULL COMMENT '接口请求方法',
    resource_path VARCHAR(255) NULL COMMENT '接口资源路径',
    description VARCHAR(500) NULL COMMENT '权限说明',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0停用，1启用',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    created_by BIGINT NULL COMMENT '创建人ID',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '修改时间',
    updated_by BIGINT NULL COMMENT '修改人ID',
    deleted BIGINT NOT NULL DEFAULT 0 COMMENT '删除标识：0未删除，大于0为删除记录ID',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_permission_app_code_deleted (app_id, permission_code, deleted),
    KEY idx_sys_permission_app_menu (app_id, menu_id, status, deleted),
    KEY idx_sys_permission_resource (resource_method, resource_path)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='权限资源表';

CREATE TABLE IF NOT EXISTS sys_account_role (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    app_id BIGINT NOT NULL COMMENT '系统应用ID',
    account_id BIGINT NOT NULL COMMENT '账号ID',
    role_id BIGINT NOT NULL COMMENT '角色ID',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    created_by BIGINT NULL COMMENT '创建人ID',
    deleted BIGINT NOT NULL DEFAULT 0 COMMENT '删除标识：0未删除，大于0为删除记录ID',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_account_role_deleted (app_id, account_id, role_id, deleted),
    KEY idx_sys_account_role_account (app_id, account_id, deleted),
    KEY idx_sys_account_role_role (app_id, role_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='账号角色关联表';

CREATE TABLE IF NOT EXISTS sys_user_role (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    app_id BIGINT NOT NULL COMMENT '系统应用ID',
    user_id BIGINT NOT NULL COMMENT '用户主体ID',
    role_id BIGINT NOT NULL COMMENT '角色ID',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    created_by BIGINT NULL COMMENT '创建人ID',
    deleted BIGINT NOT NULL DEFAULT 0 COMMENT '删除标识：0未删除，大于0为删除记录ID',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_user_role_deleted (app_id, user_id, role_id, deleted),
    KEY idx_sys_user_role_user (app_id, user_id, deleted),
    KEY idx_sys_user_role_role (app_id, role_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户主体角色关联表';

CREATE TABLE IF NOT EXISTS sys_merchant_user (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    merchant_info_id BIGINT NOT NULL COMMENT '商户主表ID，对应 base_merchant_info.id',
    merchant_id VARCHAR(32) NOT NULL COMMENT '支付框架商户号，对应 base_merchant_info.merchant_id',
    user_id BIGINT NULL COMMENT '用户主体ID，对应 sys_user.id',
    account_id BIGINT NULL COMMENT '登录账号ID，对应 sys_account.id',
    login_account VARCHAR(100) NULL COMMENT '商户端登录账号',
    real_name VARCHAR(100) NULL COMMENT '用户姓名',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0停用，1启用',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    created_by BIGINT NULL COMMENT '创建人ID',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '修改时间',
    updated_by BIGINT NULL COMMENT '修改人ID',
    deleted BIGINT NOT NULL DEFAULT 0 COMMENT '删除标识：0未删除，大于0为删除记录ID',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_merchant_user_account_deleted (account_id, deleted),
    KEY idx_sys_merchant_user_mid (merchant_info_id, status, deleted),
    KEY idx_sys_merchant_user_merchant (merchant_id, status, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商户端登录用户表';

CREATE TABLE IF NOT EXISTS sys_merchant_user_role (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    app_id BIGINT NOT NULL COMMENT '系统应用ID',
    merchant_info_id BIGINT NOT NULL COMMENT '商户主表ID，对应 base_merchant_info.id',
    merchant_user_id BIGINT NOT NULL COMMENT '商户端用户ID，对应 sys_merchant_user.id',
    role_id BIGINT NOT NULL COMMENT '角色ID',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    created_by BIGINT NULL COMMENT '创建人ID',
    deleted BIGINT NOT NULL DEFAULT 0 COMMENT '删除标识：0未删除，大于0为删除记录ID',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_merchant_user_role_deleted (app_id, merchant_user_id, role_id, deleted),
    KEY idx_sys_merchant_user_role_user (app_id, merchant_user_id, deleted),
    KEY idx_sys_merchant_user_role_merchant (merchant_info_id, role_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商户端用户角色关联表';

CREATE TABLE IF NOT EXISTS sys_role_menu (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    app_id BIGINT NOT NULL COMMENT '系统应用ID',
    role_id BIGINT NOT NULL COMMENT '角色ID',
    menu_id BIGINT NOT NULL COMMENT '菜单ID',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    created_by BIGINT NULL COMMENT '创建人ID',
    deleted BIGINT NOT NULL DEFAULT 0 COMMENT '删除标识：0未删除，大于0为删除记录ID',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_role_menu_deleted (app_id, role_id, menu_id, deleted),
    KEY idx_sys_role_menu_role (app_id, role_id, deleted),
    KEY idx_sys_role_menu_menu (app_id, menu_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色菜单关联表';

CREATE TABLE IF NOT EXISTS sys_role_permission (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    app_id BIGINT NOT NULL COMMENT '系统应用ID',
    role_id BIGINT NOT NULL COMMENT '角色ID',
    permission_id BIGINT NOT NULL COMMENT '权限ID',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    created_by BIGINT NULL COMMENT '创建人ID',
    deleted BIGINT NOT NULL DEFAULT 0 COMMENT '删除标识：0未删除，大于0为删除记录ID',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_role_permission_deleted (app_id, role_id, permission_id, deleted),
    KEY idx_sys_role_permission_role (app_id, role_id, deleted),
    KEY idx_sys_role_permission_permission (app_id, permission_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色权限关联表';

CREATE TABLE IF NOT EXISTS sys_role_data_scope (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    app_id BIGINT NOT NULL COMMENT '系统应用ID',
    role_id BIGINT NOT NULL COMMENT '角色ID',
    scope_type VARCHAR(30) NOT NULL COMMENT '数据范围类型：ORG机构，MERCHANT商户，STORE门店，CHANNEL渠道，CUSTOM自定义',
    scope_value VARCHAR(100) NOT NULL COMMENT '数据范围值',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    created_by BIGINT NULL COMMENT '创建人ID',
    deleted BIGINT NOT NULL DEFAULT 0 COMMENT '删除标识：0未删除，大于0为删除记录ID',
    PRIMARY KEY (id),
    KEY idx_sys_role_scope_role (app_id, role_id, scope_type, deleted),
    KEY idx_sys_role_scope_value (scope_type, scope_value, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色数据权限范围表';

CREATE TABLE IF NOT EXISTS sys_login_log (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    app_id BIGINT NOT NULL COMMENT '系统应用ID',
    account_id BIGINT NULL COMMENT '账号ID，登录失败且账号不存在时为空',
    user_id BIGINT NULL COMMENT '用户主体ID',
    merchant_id VARCHAR(32) NULL COMMENT '商户号',
    login_account VARCHAR(100) NULL COMMENT '登录账号',
    login_ip VARCHAR(64) NULL COMMENT '登录IP',
    user_agent VARCHAR(500) NULL COMMENT 'User-Agent',
    login_status TINYINT NOT NULL DEFAULT 1 COMMENT '登录状态：0失败，1成功',
    fail_reason VARCHAR(500) NULL COMMENT '失败原因',
    login_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '登录时间',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_sys_login_app_account_time (app_id, account_id, login_at),
    KEY idx_sys_login_merchant_time (merchant_id, login_at),
    KEY idx_sys_login_status_time (login_status, login_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='登录日志表';

CREATE TABLE IF NOT EXISTS sys_login_session (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    app_id BIGINT NOT NULL COMMENT '系统应用ID',
    account_id BIGINT NOT NULL COMMENT '账号ID',
    user_id BIGINT NOT NULL COMMENT '用户主体ID',
    merchant_id VARCHAR(32) NULL COMMENT '商户号',
    token_hash VARCHAR(128) NOT NULL COMMENT '登录token哈希，禁止保存token明文',
    login_ip VARCHAR(64) NULL COMMENT '登录IP',
    user_agent VARCHAR(500) NULL COMMENT 'User-Agent',
    expire_at DATETIME(3) NOT NULL COMMENT '过期时间',
    logout TINYINT NOT NULL DEFAULT 0 COMMENT '是否退出：0否，1是',
    logout_at DATETIME(3) NULL COMMENT '退出时间',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_login_session_token (token_hash),
    KEY idx_sys_login_session_account (app_id, account_id, logout, expire_at),
    KEY idx_sys_login_session_merchant (merchant_id, logout, expire_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='登录会话表';

INSERT IGNORE INTO sys_app (id, app_code, app_name, app_type, description, status, sort_no, deleted)
VALUES
    (1, 'ADMIN', '管理后台', 'ADMIN', '平台运营、合规、风控、财务等内部人员使用', 1, 1, 0),
    (2, 'MERCHANT', '商户系统', 'MERCHANT', '商户用户查看交易、结算、账户与配置', 1, 2, 0);

INSERT IGNORE INTO sys_role (id, app_id, role_code, role_name, role_type, data_scope, description, status, sort_no, deleted)
VALUES
    (1, 1, 'ADMIN_OPERATOR', '后台操作员', 'SYSTEM', 'ALL', '管理后台默认角色', 1, 1, 0),
    (2, 2, 'MERCHANT_ADMIN', '商户管理员', 'SYSTEM', 'SELF', '商户系统默认管理员角色', 1, 1, 0);

INSERT INTO sys_user (user_type, real_name, nickname, mobile, email, country_code, language, timezone, status, remark, created_at, updated_at, deleted)
SELECT 'PLATFORM', '平台超级管理员', 'admin', NULL, 'admin@local.local', 'CN', 'zh-CN', 'Asia/Shanghai', 1,
       '本地开发初始化账号，首次登录后请修改密码', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 0
WHERE NOT EXISTS (
    SELECT 1
    FROM sys_account account
    JOIN sys_app app ON app.id = account.app_id AND app.deleted = 0
    WHERE app.app_code = 'ADMIN'
      AND account.login_account = 'admin'
      AND account.deleted = 0
);

INSERT IGNORE INTO sys_account (app_id, user_id, login_account, password_hash, password_salt, password_algo, email,
                                mfa_enabled, password_expired, password_updated_at, failed_login_count, locked,
                                status, remark, created_at, updated_at, deleted)
SELECT app.id, user.id, 'admin',
       'F3jyTUusOGYVQtJAfGYb8wIZBLwL4gfaH1GZduj5C-U',
       'QWRtaW5TZWVkU2FsdDIwMjY',
       'PBKDF2WithHmacSHA256',
       'admin@local.local',
       0, 0, CURRENT_TIMESTAMP(3), 0, 0, 1,
       '初始密码 Admin@123456，仅用于本地开发和首次初始化',
       CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 0
FROM sys_app app
JOIN sys_user user ON user.email = 'admin@local.local' AND user.deleted = 0
WHERE app.app_code = 'ADMIN'
  AND app.deleted = 0
  AND NOT EXISTS (
      SELECT 1
      FROM sys_account exists_account
      WHERE exists_account.app_id = app.id
        AND exists_account.login_account = 'admin'
        AND exists_account.deleted = 0
  )
ORDER BY user.id DESC
LIMIT 1;

INSERT INTO sys_user (user_type, real_name, nickname, mobile, email, country_code, language, timezone, status, remark, created_at, updated_at, deleted)
SELECT 'PLATFORM', 'Scott', 'scott', NULL, 'scott@local.local', 'CN', 'zh-CN', 'Asia/Shanghai', 1,
       '本地开发初始化账号，首次登录后请修改密码', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 0
WHERE NOT EXISTS (
    SELECT 1
    FROM sys_account account
    JOIN sys_app app ON app.id = account.app_id AND app.deleted = 0
    WHERE app.app_code = 'ADMIN'
      AND account.login_account = 'scott'
      AND account.deleted = 0
);

INSERT IGNORE INTO sys_account (app_id, user_id, login_account, password_hash, password_salt, password_algo, email,
                                mfa_enabled, password_expired, password_updated_at, failed_login_count, locked,
                                status, remark, created_at, updated_at, deleted)
SELECT app.id, user.id, 'scott',
       '70eqcgzJv9VluOi_B5gJ--BLheHqRkYIYKVym2pXYiY',
       'oBNauY5qz6eqJfnVagG4pw',
       'PBKDF2WithHmacSHA256',
       'scott@local.local',
       0, 0, CURRENT_TIMESTAMP(3), 0, 0, 1,
       '初始密码 Admin@123456，仅用于本地开发和首次初始化',
       CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), 0
FROM sys_app app
JOIN sys_user user ON user.email = 'scott@local.local' AND user.deleted = 0
WHERE app.app_code = 'ADMIN'
  AND app.deleted = 0
  AND NOT EXISTS (
      SELECT 1
      FROM sys_account exists_account
      WHERE exists_account.app_id = app.id
        AND exists_account.login_account = 'scott'
        AND exists_account.deleted = 0
  )
ORDER BY user.id DESC
LIMIT 1;

INSERT IGNORE INTO sys_account_role (app_id, account_id, role_id, deleted)
SELECT app.id, account.id, role.id, 0
FROM sys_app app
JOIN sys_account account ON account.app_id = app.id AND account.login_account = 'admin' AND account.deleted = 0
JOIN sys_role role ON role.app_id = app.id AND role.role_code = 'ADMIN_OPERATOR' AND role.deleted = 0
WHERE app.app_code = 'ADMIN' AND app.deleted = 0;

INSERT IGNORE INTO sys_account_role (app_id, account_id, role_id, deleted)
SELECT app.id, account.id, role.id, 0
FROM sys_app app
JOIN sys_account account ON account.app_id = app.id AND account.login_account = 'scott' AND account.deleted = 0
JOIN sys_role role ON role.app_id = app.id AND role.role_code = 'ADMIN_OPERATOR' AND role.deleted = 0
WHERE app.app_code = 'ADMIN' AND app.deleted = 0;

INSERT IGNORE INTO sys_menu (id, app_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path, permission_code, icon, visible, sort_no, status, deleted)
VALUES
    (1, 1, 0, 'admin_dashboard', '控制台', 'MENU', '/dashboard', 'admin/dashboard/index', 'admin:dashboard:view', 'dashboard', 1, 1, 1, 0),
    (2, 1, 0, 'admin_system', '系统管理', 'CATALOG', '/system', NULL, 'admin:system:view', 'setting', 1, 10, 1, 0),
    (3, 1, 2, 'admin_user', '用户管理', 'MENU', '/system/users', 'admin/system/user/index', 'admin:user:view', 'user', 1, 11, 1, 0),
    (4, 1, 2, 'admin_role', '角色管理', 'MENU', '/system/roles', 'admin/system/role/index', 'admin:role:view', 'lock', 1, 12, 1, 0);

INSERT IGNORE INTO sys_menu (id, app_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path, permission_code, icon, visible, sort_no, status, deleted)
VALUES
    (9, 1, 2, 'admin_config', '参数设置', 'MENU', '/system/configs', 'admin/system/config/index', 'admin:config:view', 'setting', 1, 17, 1, 0),
    (10, 1, 2, 'admin_dict', '字典管理', 'MENU', '/system/dicts', 'admin/system/dict/index', 'admin:dict:view', 'dict', 1, 16, 1, 0),
    (11, 1, 2, 'admin_oper_log', '操作日志', 'MENU', '/system/oper-logs', 'admin/system/oper-log/index', 'admin:oper-log:view', 'log', 1, 18, 1, 0);

INSERT IGNORE INTO sys_menu (id, app_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path, permission_code, icon, visible, sort_no, status, deleted)
VALUES
    (18, 1, 2, 'admin_menu', '菜单管理', 'MENU', '/system/menus', 'admin/system/menu/index', 'admin:menu:view', 'menu', 1, 13, 1, 0),
    (19, 1, 2, 'admin_dept', '部门管理', 'MENU', '/system/departments', 'admin/system/dept/index', 'admin:dept:view', 'dept', 1, 14, 1, 0),
    (20, 1, 2, 'admin_post', '岗位管理', 'MENU', '/system/posts', 'admin/system/post/index', 'admin:post:view', 'post', 1, 15, 1, 0),
    (21, 1, 2, 'admin_login_log', '登录日志', 'MENU', '/system/login-logs', 'admin/system/login-log/index', 'admin:login-log:view', 'log', 1, 19, 1, 0),
    (22, 1, 0, 'admin_merchant_catalog', '商户管理', 'CATALOG', '/merchants', NULL, 'admin:merchant:view', 'merchant', 1, 20, 1, 0),
    (23, 1, 22, 'admin_merchant_info', '商户列表', 'MENU', '/merchants/list', 'admin/merchant/info/index', 'admin:merchant:view', 'shop', 1, 21, 1, 0),
    (24, 1, 22, 'admin_merchant_user', '商户账号', 'MENU', '/merchants/users', 'admin/merchant/user/index', 'admin:merchant-user:view', 'user', 1, 22, 1, 0),
    (25, 1, 22, 'admin_merchant_audit', '商户审核', 'MENU', '/merchants/audit', 'admin/merchant/audit/index', 'admin:merchant:audit', 'lock', 1, 23, 1, 0),
    (26, 1, 22, 'admin_merchant_key', '商户密钥', 'MENU', '/merchants/api-keys', 'admin/merchant/key/index', 'admin:merchant-key:view', 'key', 1, 24, 1, 0),
    (27, 1, 0, 'admin_base_data', '基础数据管理', 'CATALOG', '/base', NULL, 'admin:base:view', 'base', 1, 30, 1, 0),
    (28, 1, 27, 'admin_iso_country', '国家地区代码', 'MENU', '/base/countries', 'admin/base/country/index', 'admin:iso-country:view', 'country', 1, 31, 1, 0),
    (29, 1, 27, 'admin_iso_currency', '币种代码', 'MENU', '/base/currencies', 'admin/base/currency/index', 'admin:iso-currency:view', 'currency', 1, 32, 1, 0),
    (30, 1, 0, 'admin_payment_catalog', '交易管理', 'CATALOG', '/payments', NULL, 'admin:payment:view', 'payment', 1, 40, 1, 0),
    (31, 1, 30, 'admin_payment_order', '支付订单', 'MENU', '/payments/orders', 'admin/payment/order/index', 'admin:payment-order:view', 'order', 1, 41, 1, 0),
    (32, 1, 30, 'admin_refund_order', '退款管理', 'MENU', '/payments/refunds', 'admin/payment/refund/index', 'admin:refund:view', 'refund', 1, 42, 1, 0),
    (33, 1, 30, 'admin_payout_order', '代付订单', 'MENU', '/payout/orders', 'admin/payout/order/index', 'admin:payout-order:view', 'payout', 1, 43, 1, 0),
    (34, 1, 30, 'admin_settlement', '结算管理', 'MENU', '/payments/settlements', 'admin/settlement/index', 'admin:settlement:view', 'settlement', 1, 44, 1, 0),
    (35, 1, 30, 'admin_channel', '通道管理', 'MENU', '/payments/channels', 'admin/channel/index', 'admin:channel:view', 'channel', 1, 45, 1, 0),
    (36, 1, 0, 'admin_risk_catalog', '风控管理', 'CATALOG', '/risk', NULL, 'admin:risk:view', 'risk', 1, 50, 1, 0),
    (37, 1, 36, 'admin_risk_rule', '风控规则', 'MENU', '/risk/rules', 'admin/risk/rule/index', 'admin:risk-rule:view', 'risk', 1, 51, 1, 0),
    (38, 1, 36, 'admin_risk_blacklist', '黑名单管理', 'MENU', '/risk/blacklist', 'admin/risk/blacklist/index', 'admin:risk-blacklist:view', 'lock', 1, 52, 1, 0);

INSERT IGNORE INTO sys_permission (id, app_id, menu_id, permission_code, permission_name, permission_type, resource_method, resource_path, status, deleted)
VALUES
    (1, 1, 1, 'admin:dashboard:view', '后台控制台查看', 'MENU', 'GET', '/admin/dashboard/**', 1, 0),
    (2, 1, 3, 'admin:user:view', '后台用户查看', 'API', 'POST', '/admin/users/search', 1, 0),
    (3, 1, 3, 'admin:user:create', '后台用户创建', 'API', 'POST', '/admin/auth/register', 1, 0),
    (4, 1, 4, 'admin:role:view', '后台角色查看', 'API', 'POST', '/admin/roles/search', 1, 0);

INSERT IGNORE INTO sys_permission (id, app_id, menu_id, permission_code, permission_name, permission_type, resource_method, resource_path, status, deleted)
VALUES
    (10, 1, 9, 'admin:config:view', '系统配置查看', 'API', 'GET', '/admin/system/configs/**', 1, 0),
    (11, 1, 9, 'admin:config:save', '系统配置保存', 'API', 'POST', '/admin/system/configs', 1, 0),
    (12, 1, 9, 'admin:config:delete', '系统配置删除', 'API', 'DELETE', '/admin/system/configs/**', 1, 0),
    (13, 1, 10, 'admin:dict:view', '数据字典查看', 'API', 'POST', '/admin/system/dicts/**/search', 1, 0),
    (14, 1, 10, 'admin:dict:save', '数据字典保存', 'API', 'POST', '/admin/system/dicts/**', 1, 0),
    (15, 1, 10, 'admin:dict:delete', '数据字典删除', 'API', 'DELETE', '/admin/system/dicts/**', 1, 0),
    (16, 1, 11, 'admin:oper-log:view', '后台操作日志查看', 'API', 'POST', '/admin/system/oper-logs/search', 1, 0),
    (17, 1, 11, 'admin:oper-log:create', '后台操作日志写入', 'API', 'POST', '/admin/system/oper-logs', 1, 0);

INSERT IGNORE INTO sys_permission (id, app_id, menu_id, permission_code, permission_name, permission_type, resource_method, resource_path, status, deleted)
VALUES
    (28, 1, 2, 'admin:system:view', '系统管理目录查看', 'MENU', 'GET', '/system/**', 1, 0),
    (29, 1, 18, 'admin:menu:view', '菜单管理查看', 'MENU', 'GET', '/system/menus', 1, 0),
    (30, 1, 19, 'admin:dept:view', '部门管理查看', 'MENU', 'GET', '/system/departments', 1, 0),
    (31, 1, 20, 'admin:post:view', '岗位管理查看', 'MENU', 'GET', '/system/posts', 1, 0),
    (32, 1, 21, 'admin:login-log:view', '登录日志查看', 'MENU', 'GET', '/system/login-logs', 1, 0),
    (33, 1, 22, 'admin:merchant:view', '商户管理查看', 'MENU', 'GET', '/merchants/**', 1, 0),
    (34, 1, 24, 'admin:merchant-user:view', '商户账号查看', 'MENU', 'GET', '/merchants/users', 1, 0),
    (35, 1, 25, 'admin:merchant:audit', '商户审核处理', 'BUTTON', '*', '/admin/merchants/**/audit', 1, 0),
    (36, 1, 26, 'admin:merchant-key:view', '商户密钥查看', 'MENU', 'GET', '/merchants/api-keys', 1, 0),
    (37, 1, 27, 'admin:base:view', '基础数据目录查看', 'MENU', 'GET', '/base/**', 1, 0),
    (38, 1, 28, 'admin:iso-country:view', '国家地区代码查看', 'MENU', 'GET', '/base/countries', 1, 0),
    (39, 1, 29, 'admin:iso-currency:view', '币种代码查看', 'MENU', 'GET', '/base/currencies', 1, 0),
    (40, 1, 30, 'admin:payment:view', '交易管理目录查看', 'MENU', 'GET', '/payments/**', 1, 0),
    (41, 1, 31, 'admin:payment-order:view', '支付订单查看', 'MENU', 'GET', '/payments/orders', 1, 0),
    (42, 1, 32, 'admin:refund:view', '退款管理查看', 'MENU', 'GET', '/payments/refunds', 1, 0),
    (43, 1, 33, 'admin:payout-order:view', '代付订单查看', 'MENU', 'GET', '/payout/orders', 1, 0),
    (44, 1, 34, 'admin:settlement:view', '结算管理查看', 'MENU', 'GET', '/payments/settlements', 1, 0),
    (45, 1, 35, 'admin:channel:view', '通道管理查看', 'MENU', 'GET', '/payments/channels', 1, 0),
    (46, 1, 36, 'admin:risk:view', '风控管理目录查看', 'MENU', 'GET', '/risk/**', 1, 0),
    (47, 1, 37, 'admin:risk-rule:view', '风控规则查看', 'MENU', 'GET', '/risk/rules', 1, 0),
    (48, 1, 38, 'admin:risk-blacklist:view', '黑名单查看', 'MENU', 'GET', '/risk/blacklist', 1, 0);

UPDATE sys_menu
SET visible = 0, updated_at = CURRENT_TIMESTAMP(3)
WHERE app_id = 1
  AND id BETWEEN 2 AND 38
  AND deleted = 0;

UPDATE sys_menu
SET menu_name = '控制台', route_path = '/dashboard', component_path = 'dashboard/index',
    permission_code = 'admin:dashboard:view', icon = 'House', visible = 1, sort_no = 1, status = 1
WHERE id = 1 AND app_id = 1 AND deleted = 0;

INSERT IGNORE INTO sys_menu (id, app_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path, permission_code, icon, visible, sort_no, status, deleted)
VALUES
    (100, 1, 0, 'admin_system_center', '系统管理', 'CATALOG', '/system', NULL, 'admin:system:view', 'Setting', 1, 10, 1, 0),
    (101, 1, 100, 'admin_system_user', '用户管理', 'MENU', '/system/user', 'system/user/index', 'admin:user:view', 'User', 1, 11, 1, 0),
    (102, 1, 100, 'admin_system_role', '角色管理', 'MENU', '/system/role', 'system/role/index', 'admin:role:view', 'Lock', 1, 12, 1, 0),
    (103, 1, 100, 'admin_system_menu', '菜单管理', 'MENU', '/system/menu', 'system/menu/index', 'admin:menu:view', 'Menu', 1, 13, 1, 0),
    (104, 1, 100, 'admin_system_department', '部门管理', 'MENU', '/system/department', 'system/department/index', 'admin:dept:view', 'OfficeBuilding', 1, 14, 1, 0),
    (105, 1, 100, 'admin_system_post', '岗位管理', 'MENU', '/system/post', 'system/post/index', 'admin:post:view', 'Postcard', 1, 15, 1, 0),
    (106, 1, 100, 'admin_system_dict', '字典管理', 'MENU', '/system/dict', 'system/dict/index', 'admin:dict:view', 'Tickets', 1, 16, 1, 0),
    (107, 1, 100, 'admin_system_config', '参数设置', 'MENU', '/system/config', 'system/config/index', 'admin:config:view', 'Setting', 1, 17, 1, 0),
    (108, 1, 100, 'admin_system_login_log', '登录日志', 'MENU', '/system/login-log', 'system/login-log/index', 'admin:login-log:view', 'DocumentChecked', 1, 18, 1, 0),
    (109, 1, 100, 'admin_system_oper_log', '操作日志', 'MENU', '/system/oper-log', 'system/oper-log/index', 'admin:oper-log:view', 'Document', 1, 19, 1, 0),
    (120, 1, 0, 'admin_merchant_center', '商户管理', 'CATALOG', '/merchant', NULL, 'admin:merchant:view', 'Shop', 1, 20, 1, 0),
    (121, 1, 120, 'admin_merchant_info_v2', '商户信息', 'MENU', '/merchant/info', 'merchant/info/index', 'admin:merchant:view', 'OfficeBuilding', 1, 21, 1, 0),
    (122, 1, 120, 'admin_merchant_user_v2', '商户账号', 'MENU', '/merchant/user', 'merchant/user/index', 'admin:merchant-user:view', 'Avatar', 1, 22, 1, 0),
    (123, 1, 120, 'admin_merchant_role_v2', '商户角色', 'MENU', '/merchant/role', 'merchant/role/index', 'admin:merchant-role:view', 'UserFilled', 1, 23, 1, 0),
    (124, 1, 120, 'admin_merchant_jwt_key', '商户密钥', 'MENU', '/merchant/jwt-key', 'merchant/jwt-key/index', 'admin:merchant-jwt-key:view', 'Key', 1, 24, 1, 0),
    (125, 1, 120, 'admin_merchant_response_key', '商户响应公钥', 'MENU', '/merchant/response-key', 'merchant/response-key/index', 'admin:merchant-response-key:view', 'Unlock', 1, 25, 1, 0),
    (126, 1, 120, 'admin_platform_payload_key', '平台请求密钥', 'MENU', '/merchant/platform-payload-key', 'merchant/platform-payload-key/index', 'admin:platform-payload-key:view', 'Lock', 1, 26, 1, 0),
    (140, 1, 0, 'admin_base_center', '基础数据管理', 'CATALOG', '/base', NULL, 'admin:base:view', 'DataLine', 1, 30, 1, 0),
    (141, 1, 140, 'admin_base_country', '国家/地区代码', 'MENU', '/base/country', 'base/country/index', 'admin:iso-country:view', 'Location', 1, 31, 1, 0),
    (142, 1, 140, 'admin_base_currency', '币种代码', 'MENU', '/base/currency', 'base/currency/index', 'admin:iso-currency:view', 'Coin', 1, 32, 1, 0),
    (143, 1, 140, 'admin_base_region_currency', '地区币种配置', 'MENU', '/base/region-currency', 'base/region-currency/index', 'admin:region-currency:view', 'Connection', 1, 33, 1, 0);

INSERT IGNORE INTO sys_permission (id, app_id, menu_id, permission_code, permission_name, permission_type, resource_method, resource_path, status, deleted)
VALUES
    (100, 1, 100, 'admin:system:view', '系统管理目录查看', 'MENU', 'GET', '/system/**', 1, 0),
    (101, 1, 101, 'admin:user:view', '用户管理查看', 'MENU', 'GET', '/system/user', 1, 0),
    (102, 1, 102, 'admin:role:view', '角色管理查看', 'MENU', 'GET', '/system/role', 1, 0),
    (103, 1, 103, 'admin:menu:view', '菜单管理查看', 'MENU', 'GET', '/system/menu', 1, 0),
    (104, 1, 104, 'admin:dept:view', '部门管理查看', 'MENU', 'GET', '/system/department', 1, 0),
    (105, 1, 105, 'admin:post:view', '岗位管理查看', 'MENU', 'GET', '/system/post', 1, 0),
    (106, 1, 106, 'admin:dict:view', '字典管理查看', 'MENU', 'GET', '/system/dict', 1, 0),
    (107, 1, 107, 'admin:config:view', '参数设置查看', 'MENU', 'GET', '/system/config', 1, 0),
    (108, 1, 108, 'admin:login-log:view', '登录日志查看', 'MENU', 'GET', '/system/login-log', 1, 0),
    (109, 1, 109, 'admin:oper-log:view', '操作日志查看', 'MENU', 'GET', '/system/oper-log', 1, 0),
    (120, 1, 120, 'admin:merchant:view', '商户管理目录查看', 'MENU', 'GET', '/merchant/**', 1, 0),
    (122, 1, 122, 'admin:merchant-user:view', '商户账号查看', 'MENU', 'GET', '/merchant/user', 1, 0),
    (123, 1, 123, 'admin:merchant-role:view', '商户角色查看', 'MENU', 'GET', '/merchant/role', 1, 0),
    (124, 1, 124, 'admin:merchant-jwt-key:view', '商户密钥查看', 'MENU', 'GET', '/merchant/jwt-key', 1, 0),
    (125, 1, 125, 'admin:merchant-response-key:view', '商户响应公钥查看', 'MENU', 'GET', '/merchant/response-key', 1, 0),
    (126, 1, 126, 'admin:platform-payload-key:view', '平台请求密钥查看', 'MENU', 'GET', '/merchant/platform-payload-key', 1, 0),
    (140, 1, 140, 'admin:base:view', '基础数据目录查看', 'MENU', 'GET', '/base/**', 1, 0),
    (143, 1, 143, 'admin:region-currency:view', '地区币种配置查看', 'MENU', 'GET', '/base/region-currency', 1, 0);

UPDATE sys_menu
SET visible = 0, updated_at = CURRENT_TIMESTAMP(3)
WHERE app_id = 1
  AND id BETWEEN 100 AND 184
  AND deleted = 0;

UPDATE sys_menu
SET menu_name = '工作台', route_path = '/dashboard', component_path = 'dashboard',
    permission_code = 'dashboard:view', icon = 'House', visible = 0, sort_no = 1, status = 1,
    updated_at = CURRENT_TIMESTAMP(3)
WHERE id = 1 AND app_id = 1 AND deleted = 0;

INSERT IGNORE INTO sys_menu (id, app_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path, permission_code, icon, visible, sort_no, status, deleted)
VALUES
    (200, 1, 0, 'admin_home_catalog_v3', '首页', 'CATALOG', '/', NULL, NULL, 'House', 1, 1, 1, 0),
    (201, 1, 200, 'admin_dashboard_v3', '工作台', 'MENU', '/dashboard', 'dashboard', 'dashboard:view', 'House', 1, 2, 1, 0),
    (210, 1, 0, 'admin_system_catalog_v3', '系统管理', 'CATALOG', '/system', NULL, NULL, 'Setting', 1, 10, 1, 0),
    (211, 1, 210, 'admin_system_user_v3', '用户管理', 'MENU', '/system/user', 'system/user', 'system:user:list', 'User', 1, 11, 1, 0),
    (212, 1, 210, 'admin_system_role_v3', '角色管理', 'MENU', '/system/role', 'system/role', 'system:role:list', 'Lock', 1, 12, 1, 0),
    (213, 1, 210, 'admin_system_menu_v3', '菜单管理', 'MENU', '/system/menu', 'system/menu', 'system:menu:list', 'Menu', 1, 13, 1, 0),
    (214, 1, 210, 'admin_system_org_v3', '部门岗位', 'MENU', '/system/org', 'system/org', 'system:org:list', 'OfficeBuilding', 1, 14, 1, 0),
    (215, 1, 210, 'admin_system_config_center_v3', '字典参数', 'MENU', '/system/config-center', 'system/config-center', 'system:dict:list', 'Tickets', 1, 15, 1, 0),
    (216, 1, 210, 'admin_system_log_v3', '日志管理', 'MENU', '/system/log', 'system/log', 'system:login-log:list', 'DocumentChecked', 1, 16, 1, 0),
    (230, 1, 0, 'admin_merchant_catalog_v3', '商户管理', 'CATALOG', '/merchant', NULL, NULL, 'Shop', 1, 20, 1, 0),
    (231, 1, 230, 'admin_merchant_info_v3', '商户信息', 'MENU', '/merchant/info', 'merchant/info', 'merchant:info:list', 'Shop', 1, 21, 1, 0),
    (232, 1, 230, 'admin_merchant_menu_grant_v3', '商户菜单授权', 'MENU', '/merchant/menu-grant', 'merchant/menu-grant', 'merchant:menu-grant:list', 'Menu', 1, 22, 1, 0),
    (240, 1, 0, 'admin_base_catalog_v3', '基础数据', 'CATALOG', '/base', NULL, NULL, 'DataLine', 1, 30, 1, 0),
    (241, 1, 240, 'admin_base_country_v3', '国家/地区', 'MENU', '/base/country', 'base/country', 'base:country:list', 'Location', 1, 31, 1, 0),
    (242, 1, 240, 'admin_base_currency_v3', '币种管理', 'MENU', '/base/currency', 'base/currency', 'base:currency:list', 'Coin', 1, 32, 1, 0),
    (243, 1, 240, 'admin_base_region_currency_v3', '地区币种配置', 'MENU', '/base/region-currency', 'base/region-currency', 'base:countryCurrency:list', 'Connection', 1, 33, 1, 0);

INSERT IGNORE INTO sys_menu (id, app_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path, permission_code, icon, visible, sort_no, status, deleted)
VALUES
    (500, 2, 0, 'merchant_system_catalog_v1', '系统管理', 'CATALOG', '/system', NULL, NULL, 'Setting', 1, 90, 1, 0),
    (501, 2, 500, 'merchant_system_dept_v1', '组织架构', 'MENU', '/system/dept', 'system/dept', 'merchant:system:dept:list', 'OfficeBuilding', 1, 91, 1, 0),
    (502, 2, 500, 'merchant_system_post_v1', '岗位管理', 'MENU', '/system/post', 'system/post', 'merchant:system:post:list', 'Postcard', 1, 92, 1, 0),
    (503, 2, 500, 'merchant_system_account_v1', '员工账号', 'MENU', '/system/account', 'system/account', 'merchant:system:account:list', 'User', 1, 93, 1, 0),
    (504, 2, 500, 'merchant_system_role_v1', '角色管理', 'MENU', '/system/role', 'system/role', 'merchant:system:role:list', 'Lock', 1, 94, 1, 0),
    (505, 2, 500, 'merchant_system_role_auth_v1', '角色授权', 'MENU', '/system/role-auth', 'system/role-auth', 'merchant:system:role:grantMenu', 'Unlock', 1, 95, 1, 0),
    (506, 2, 0, 'merchant_openapi_keys_v1', '商户密钥管理', 'MENU', '/merchant-info/openapi-keys', 'merchant-info/openapi-keys', 'merchant:openapi:key:view', 'Key', 1, 80, 1, 0);

INSERT IGNORE INTO sys_permission (id, app_id, menu_id, permission_code, permission_name, permission_type, resource_method, resource_path, status, deleted)
VALUES
    (200, 1, 201, 'dashboard:view', '工作台查看', 'MENU', 'GET', '/admin/auth/me', 1, 0),
    (201, 1, 232, 'merchant:menu-grant:list', '商户菜单授权查询', 'MENU', 'GET', '/admin/merchant-menu-grants/*', 1, 0),
    (202, 1, 232, 'merchant:menu-grant:save', '商户菜单授权保存', 'BUTTON', 'POST', '/admin/merchant-menu-grants/*', 1, 0),
    (500, 2, 501, 'merchant:system:dept:list', '组织架构查询', 'MENU', 'GET', '/merchant/system/depts*', 1, 0),
    (501, 2, 501, 'merchant:system:dept:add', '组织架构新增', 'BUTTON', 'POST', '/merchant/system/depts', 1, 0),
    (502, 2, 501, 'merchant:system:dept:edit', '组织架构编辑', 'BUTTON', 'PUT', '/merchant/system/depts/*', 1, 0),
    (503, 2, 501, 'merchant:system:dept:delete', '组织架构删除', 'BUTTON', 'DELETE', '/merchant/system/depts/*', 1, 0),
    (504, 2, 502, 'merchant:system:post:list', '岗位查询', 'MENU', 'GET', '/merchant/system/posts', 1, 0),
    (505, 2, 502, 'merchant:system:post:add', '岗位新增', 'BUTTON', 'POST', '/merchant/system/posts', 1, 0),
    (506, 2, 502, 'merchant:system:post:edit', '岗位编辑', 'BUTTON', 'PUT', '/merchant/system/posts/*', 1, 0),
    (507, 2, 502, 'merchant:system:post:delete', '岗位删除', 'BUTTON', 'DELETE', '/merchant/system/posts/*', 1, 0),
    (508, 2, 503, 'merchant:system:account:list', '员工账号查询', 'MENU', 'GET', '/merchant/system/accounts', 1, 0),
    (509, 2, 503, 'merchant:system:account:add', '员工账号新增', 'BUTTON', 'POST', '/merchant/system/accounts', 1, 0),
    (510, 2, 503, 'merchant:system:account:edit', '员工账号编辑', 'BUTTON', 'PUT', '/merchant/system/accounts/*', 1, 0),
    (511, 2, 503, 'merchant:system:account:delete', '员工账号删除', 'BUTTON', 'DELETE', '/merchant/system/accounts/*', 1, 0),
    (512, 2, 503, 'merchant:system:account:status', '员工账号状态', 'BUTTON', 'PUT', '/merchant/system/accounts/*/status', 1, 0),
    (513, 2, 503, 'merchant:system:account:assignRole', '员工分配角色', 'BUTTON', 'POST', '/merchant/system/accounts/*/roles', 1, 0),
    (514, 2, 504, 'merchant:system:role:list', '角色查询', 'MENU', 'GET', '/merchant/system/roles', 1, 0),
    (515, 2, 504, 'merchant:system:role:add', '角色新增', 'BUTTON', 'POST', '/merchant/system/roles', 1, 0),
    (516, 2, 504, 'merchant:system:role:edit', '角色编辑', 'BUTTON', 'PUT', '/merchant/system/roles/*', 1, 0),
    (517, 2, 504, 'merchant:system:role:delete', '角色删除', 'BUTTON', 'DELETE', '/merchant/system/roles/*', 1, 0),
    (518, 2, 505, 'merchant:system:role:grantMenu', '角色菜单授权', 'MENU', '*', '/merchant/system/roles/*/menus', 1, 0),
    (519, 2, 505, 'merchant:system:role:grantPermission', '角色资源授权', 'BUTTON', '*', '/merchant/system/roles/*/permissions', 1, 0),
    (520, 2, 506, 'merchant:openapi:key:view', '商户OpenAPI密钥查看', 'MENU', '*', '/merchant/openapi/keys*', 1, 0),
    (521, 2, 506, 'merchant:openapi:key:copy', '商户OpenAPI材料复制', 'BUTTON', 'POST', '/merchant/openapi/keys/copy', 1, 0),
    (522, 2, 506, 'merchant:openapi:key:download', '商户OpenAPI材料下载', 'BUTTON', 'GET', '/merchant/openapi/keys/download', 1, 0),
    (523, 2, 506, 'merchant:openapi:key:download-private', '商户OpenAPI敏感材料导出', 'BUTTON', '*', '/merchant/openapi/keys/*', 1, 0),
    (524, 2, 506, 'merchant:openapi:key:rotate-jwt', '商户OpenAPI JWT密钥轮换', 'BUTTON', 'POST', '/merchant/openapi/keys/rotate', 1, 0),
    (525, 2, 506, 'merchant:openapi:key:rotate-response', '商户OpenAPI响应密钥轮换', 'BUTTON', 'POST', '/merchant/openapi/keys/rotate', 1, 0),
    (211, 1, 211, 'system:user:list', '用户管理查询', 'MENU', 'POST', '/admin/system/users/search', 1, 0),
    (212, 1, 211, 'system:user:add', '用户新增', 'BUTTON', 'POST', '/admin/system/users/create', 1, 0),
    (213, 1, 211, 'system:user:edit', '用户编辑', 'BUTTON', '*', '/admin/system/users/**', 1, 0),
    (214, 1, 211, 'system:user:delete', '用户删除', 'BUTTON', 'DELETE', '/admin/system/users/**', 1, 0),
    (215, 1, 211, 'system:user:resetPwd', '用户重置密码', 'BUTTON', 'POST', '/admin/system/users/reset-password', 1, 0),
    (216, 1, 211, 'system:user:assign-role', '用户分配角色', 'BUTTON', 'POST', '/admin/system/users/roles*', 1, 0),
    (221, 1, 212, 'system:role:list', '角色管理查询', 'MENU', 'POST', '/admin/system/roles/search', 1, 0),
    (222, 1, 212, 'system:role:add', '角色新增', 'BUTTON', '*', '/admin/system/roles/**', 1, 0),
    (223, 1, 212, 'system:role:edit', '角色编辑', 'BUTTON', '*', '/admin/system/roles/**', 1, 0),
    (224, 1, 212, 'system:role:delete', '角色删除', 'BUTTON', '*', '/admin/system/roles/**', 1, 0),
    (225, 1, 212, 'system:role:assign-menu', '角色分配菜单', 'BUTTON', 'POST', '/admin/system/roles/menus*', 1, 0),
    (226, 1, 212, 'system:role:assign-permission', '角色分配权限', 'BUTTON', 'POST', '/admin/system/roles/permissions*', 1, 0),
    (227, 1, 212, 'system:role:data-scope', '角色数据范围', 'BUTTON', '*', '/admin/system/roles/**/data-scope', 1, 0),
    (231, 1, 213, 'system:menu:list', '菜单管理查询', 'MENU', 'POST', '/admin/system/menus/tree', 1, 0),
    (232, 1, 213, 'system:menu:add', '菜单新增', 'BUTTON', '*', '/admin/system/menus/**', 1, 0),
    (233, 1, 213, 'system:menu:edit', '菜单编辑', 'BUTTON', '*', '/admin/system/menus/**', 1, 0),
    (234, 1, 213, 'system:menu:delete', '菜单删除', 'BUTTON', '*', '/admin/system/menus/**', 1, 0),
    (241, 1, 214, 'system:org:list', '部门岗位查询', 'MENU', 'GET', '/system/org', 1, 0),
    (242, 1, 214, 'system:department:list', '部门查询', 'BUTTON', '*', '/admin/system/departments/**', 1, 0),
    (243, 1, 214, 'system:department:add', '部门新增', 'BUTTON', '*', '/admin/system/departments/**', 1, 0),
    (244, 1, 214, 'system:department:edit', '部门编辑', 'BUTTON', '*', '/admin/system/departments/**', 1, 0),
    (245, 1, 214, 'system:department:delete', '部门删除', 'BUTTON', '*', '/admin/system/departments/**', 1, 0),
    (246, 1, 214, 'system:post:list', '岗位查询', 'BUTTON', '*', '/admin/system/posts/**', 1, 0),
    (247, 1, 214, 'system:post:add', '岗位新增', 'BUTTON', '*', '/admin/system/posts/**', 1, 0),
    (248, 1, 214, 'system:post:edit', '岗位编辑', 'BUTTON', '*', '/admin/system/posts/**', 1, 0),
    (249, 1, 214, 'system:post:delete', '岗位删除', 'BUTTON', '*', '/admin/system/posts/**', 1, 0),
    (251, 1, 215, 'system:dict:list', '字典查询', 'MENU', 'POST', '/admin/system/dicts/**/search', 1, 0),
    (252, 1, 215, 'system:dict:add', '字典新增', 'BUTTON', 'POST', '/admin/system/dicts/**', 1, 0),
    (253, 1, 215, 'system:dict:edit', '字典编辑', 'BUTTON', 'POST', '/admin/system/dicts/**', 1, 0),
    (254, 1, 215, 'system:dict:remove', '字典删除', 'BUTTON', 'DELETE', '/admin/system/dicts/**', 1, 0),
    (255, 1, 215, 'system:config:list', '参数查询', 'BUTTON', '*', '/admin/system/configs/**', 1, 0),
    (256, 1, 215, 'system:config:add', '参数新增', 'BUTTON', 'POST', '/admin/system/configs', 1, 0),
    (257, 1, 215, 'system:config:edit', '参数编辑', 'BUTTON', 'POST', '/admin/system/configs', 1, 0),
    (258, 1, 215, 'system:config:remove', '参数删除', 'BUTTON', 'DELETE', '/admin/system/configs/**', 1, 0),
    (261, 1, 216, 'system:login-log:list', '登录日志查询', 'MENU', 'POST', '/admin/system/login-logs/search', 1, 0),
    (262, 1, 216, 'system:oper-log:list', '操作日志查询', 'BUTTON', 'POST', '/admin/system/oper-logs/search', 1, 0),
    (263, 1, 216, 'system:log:export', '日志导出', 'BUTTON', '*', '/admin/system/logs/export', 1, 0),
    (301, 1, 231, 'merchant:info:list', '商户信息查询', 'MENU', 'GET', '/merchant/info', 1, 0),
    (302, 1, 231, 'merchant:info:detail', '商户详情', 'BUTTON', '*', '/admin/merchants/**', 1, 0),
    (303, 1, 231, 'merchant:info:edit', '商户编辑', 'BUTTON', '*', '/admin/merchants/**', 1, 0),
    (304, 1, 231, 'merchant:info:disable', '商户停用', 'BUTTON', '*', '/admin/merchants/**/disable', 1, 0),
    (311, 1, 231, 'merchant:account:list', '商户账号查询', 'BUTTON', '*', '/admin/merchant-accounts/**', 1, 0),
    (312, 1, 231, 'merchant:account:add', '商户账号新增', 'BUTTON', '*', '/admin/merchant-accounts/**', 1, 0),
    (313, 1, 231, 'merchant:account:edit', '商户账号编辑', 'BUTTON', '*', '/admin/merchant-accounts/**', 1, 0),
    (314, 1, 231, 'merchant:account:disable', '商户账号停用', 'BUTTON', '*', '/admin/merchant-accounts/**', 1, 0),
    (315, 1, 231, 'merchant:account:assign-role', '商户账号分配角色', 'BUTTON', '*', '/admin/merchant-accounts/**/roles', 1, 0),
    (321, 1, 231, 'merchant:role:list', '商户角色查询', 'BUTTON', '*', '/admin/merchant-roles/**', 1, 0),
    (322, 1, 231, 'merchant:role:assign', '商户角色授权', 'BUTTON', '*', '/admin/merchant-roles/**/grant', 1, 0),
    (331, 1, 231, 'merchant:key:view', '商户密钥查看', 'BUTTON', '*', '/admin/merchant-keys/**', 1, 0),
    (332, 1, 231, 'merchant:key:rotate', '商户密钥轮换', 'BUTTON', '*', '/admin/merchant-keys/**/rotate', 1, 0),
    (333, 1, 231, 'merchant:key:disable', '商户密钥停用', 'BUTTON', '*', '/admin/merchant-keys/**/disable', 1, 0),
    (341, 1, 231, 'merchant:response-key:view', '商户响应公钥查看', 'BUTTON', '*', '/admin/merchant-response-keys/**', 1, 0),
    (342, 1, 231, 'merchant:response-key:update', '商户响应公钥更新', 'BUTTON', '*', '/admin/merchant-response-keys/**', 1, 0),
    (343, 1, 231, 'merchant:response-key:disable', '商户响应公钥停用', 'BUTTON', '*', '/admin/merchant-response-keys/**/disable', 1, 0),
    (351, 1, 231, 'merchant:platform-payload-key:view', '平台请求密钥查看', 'BUTTON', '*', '/admin/platform-payload-keys/**', 1, 0),
    (352, 1, 231, 'merchant:platform-payload-key:rotate', '平台请求密钥轮换', 'BUTTON', '*', '/admin/platform-payload-keys/**/rotate', 1, 0),
    (353, 1, 231, 'merchant:platform-payload-key:download', '平台请求密钥下载', 'BUTTON', '*', '/admin/platform-payload-keys/**/download', 1, 0),
    (361, 1, 231, 'merchant:operation-log:list', '商户操作日志查询', 'BUTTON', '*', '/admin/merchant-operation-logs/**', 1, 0),
    (401, 1, 241, 'base:country:list', '国家地区查询', 'MENU', 'GET', '/base/country', 1, 0),
    (402, 1, 241, 'base:country:add', '国家地区新增', 'BUTTON', '*', '/admin/base/countries/**', 1, 0),
    (403, 1, 241, 'base:country:edit', '国家地区编辑', 'BUTTON', '*', '/admin/base/countries/**', 1, 0),
    (404, 1, 241, 'base:country:remove', '国家地区删除', 'BUTTON', '*', '/admin/base/countries/**', 1, 0),
    (405, 1, 241, 'base:country:import', '国家地区导入', 'BUTTON', '*', '/admin/base/countries/import', 1, 0),
    (406, 1, 241, 'base:country:export', '国家地区导出', 'BUTTON', '*', '/admin/base/countries/export', 1, 0),
    (411, 1, 242, 'base:currency:list', '币种查询', 'MENU', 'GET', '/base/currency', 1, 0),
    (412, 1, 242, 'base:currency:add', '币种新增', 'BUTTON', '*', '/admin/base/currencies/**', 1, 0),
    (413, 1, 242, 'base:currency:edit', '币种编辑', 'BUTTON', '*', '/admin/base/currencies/**', 1, 0),
    (414, 1, 242, 'base:currency:remove', '币种删除', 'BUTTON', '*', '/admin/base/currencies/**', 1, 0),
    (415, 1, 242, 'base:currency:import', '币种导入', 'BUTTON', '*', '/admin/base/currencies/import', 1, 0),
    (416, 1, 242, 'base:currency:export', '币种导出', 'BUTTON', '*', '/admin/base/currencies/export', 1, 0),
    (421, 1, 243, 'base:countryCurrency:list', '地区币种配置查询', 'MENU', 'GET', '/base/region-currency', 1, 0),
    (422, 1, 243, 'base:countryCurrency:add', '地区币种配置新增', 'BUTTON', '*', '/admin/base/region-currencies/**', 1, 0),
    (423, 1, 243, 'base:countryCurrency:edit', '地区币种配置编辑', 'BUTTON', '*', '/admin/base/region-currencies/**', 1, 0),
    (424, 1, 243, 'base:countryCurrency:remove', '地区币种配置删除', 'BUTTON', '*', '/admin/base/region-currencies/**', 1, 0);

UPDATE sys_menu
SET visible = 0,
    status = 0
WHERE app_id = 1
  AND id < 200
  AND deleted = 0;

UPDATE sys_permission
SET status = 0
WHERE app_id = 1
  AND id < 200
  AND deleted = 0;

UPDATE sys_permission
SET resource_method = CASE permission_code
        WHEN 'dashboard:view' THEN 'GET'
        WHEN 'system:user:list' THEN 'POST'
        WHEN 'system:user:add' THEN 'POST'
        WHEN 'system:user:resetPwd' THEN 'POST'
        WHEN 'system:user:assign-role' THEN 'POST'
        WHEN 'system:role:list' THEN 'POST'
        WHEN 'system:role:assign-menu' THEN 'POST'
        WHEN 'system:role:assign-permission' THEN 'POST'
        WHEN 'system:menu:list' THEN 'POST'
        WHEN 'system:login-log:list' THEN 'POST'
        WHEN 'system:oper-log:list' THEN 'POST'
        ELSE resource_method
    END,
    resource_path = CASE permission_code
        WHEN 'dashboard:view' THEN '/admin/auth/me'
        WHEN 'system:user:list' THEN '/admin/system/users/search'
        WHEN 'system:user:add' THEN '/admin/system/users/create'
        WHEN 'system:user:resetPwd' THEN '/admin/system/users/reset-password'
        WHEN 'system:user:assign-role' THEN '/admin/system/users/roles*'
        WHEN 'system:role:list' THEN '/admin/system/roles/search'
        WHEN 'system:role:assign-menu' THEN '/admin/system/roles/menus*'
        WHEN 'system:role:assign-permission' THEN '/admin/system/roles/permissions*'
        WHEN 'system:menu:list' THEN '/admin/system/menus/tree'
        WHEN 'system:login-log:list' THEN '/admin/system/login-logs/search'
        WHEN 'system:oper-log:list' THEN '/admin/system/oper-logs/search'
        ELSE resource_path
    END
WHERE app_id = 1
  AND permission_code IN (
      'dashboard:view',
      'system:user:list',
      'system:user:add',
      'system:user:resetPwd',
      'system:user:assign-role',
      'system:role:list',
      'system:role:assign-menu',
      'system:role:assign-permission',
      'system:menu:list',
      'system:login-log:list',
      'system:oper-log:list'
  )
  AND deleted = 0;

UPDATE sys_role_menu role_menu
JOIN sys_menu menu ON menu.id = role_menu.menu_id AND menu.app_id = role_menu.app_id
SET role_menu.deleted = role_menu.id
WHERE role_menu.app_id = 1
  AND menu.id < 200
  AND role_menu.deleted = 0;

UPDATE sys_role_permission role_permission
JOIN sys_permission permission ON permission.id = role_permission.permission_id AND permission.app_id = role_permission.app_id
SET role_permission.deleted = role_permission.id
WHERE role_permission.app_id = 1
  AND permission.id < 200
  AND role_permission.deleted = 0;

INSERT IGNORE INTO sys_role (app_id, role_code, role_name, role_type, data_scope, description, status, sort_no, deleted)
SELECT 2,
       CONCAT('MERCHANT_ADMIN_', merchant_id),
       CONCAT(merchant_name, ' 商户管理员'),
       'SYSTEM',
       'SELF',
       CONCAT('绑定商户ID=', id, '，商户号=', merchant_id, '，默认拥有商户端全部菜单和权限'),
       1,
       1,
       0
FROM base_merchant_info
WHERE merchant_status = 1 AND deleted = 0;

INSERT IGNORE INTO sys_role (app_id, role_code, role_name, role_type, data_scope, description, status, sort_no, deleted)
SELECT 2,
       CONCAT('MERCHANT_OPERATOR_', merchant_id),
       CONCAT(merchant_name, ' 商户操作员'),
       'SYSTEM',
       'SELF',
       CONCAT('绑定商户ID=', id, '，商户号=', merchant_id, '，默认拥有商户端业务操作权限'),
       1,
       2,
       0
FROM base_merchant_info
WHERE merchant_status = 1 AND deleted = 0;

INSERT IGNORE INTO sys_role (app_id, role_code, role_name, role_type, data_scope, description, status, sort_no, deleted)
SELECT 2,
       CONCAT('MERCHANT_VIEWER_', merchant_id),
       CONCAT(merchant_name, ' 商户查看员'),
       'SYSTEM',
       'SELF',
       CONCAT('绑定商户ID=', id, '，商户号=', merchant_id, '，默认仅拥有商户端查询权限'),
       1,
       3,
       0
FROM base_merchant_info
WHERE merchant_status = 1 AND deleted = 0;

INSERT IGNORE INTO sys_role_data_scope (app_id, role_id, scope_type, scope_value, deleted)
SELECT r.app_id, r.id, 'MERCHANT', m.merchant_id, 0
FROM sys_role r
JOIN base_merchant_info m ON r.role_code IN (
    CONCAT('MERCHANT_ADMIN_', m.merchant_id),
    CONCAT('MERCHANT_OPERATOR_', m.merchant_id),
    CONCAT('MERCHANT_VIEWER_', m.merchant_id)
)
WHERE r.app_id = 2 AND m.merchant_status = 1 AND m.deleted = 0 AND r.deleted = 0;

INSERT IGNORE INTO sys_role_menu (app_id, role_id, menu_id, deleted)
SELECT app_id, 1, id, 0 FROM sys_menu WHERE app_id = 1 AND status = 1 AND visible = 1 AND deleted = 0;

INSERT IGNORE INTO sys_role_menu (app_id, role_id, menu_id, deleted)
SELECT app_id, 2, id, 0 FROM sys_menu WHERE app_id = 2 AND deleted = 0;

INSERT IGNORE INTO sys_role_menu (app_id, role_id, menu_id, deleted)
SELECT r.app_id, r.id, m.id, 0
FROM sys_role r
JOIN sys_menu m ON m.app_id = r.app_id AND m.deleted = 0
WHERE r.app_id = 2
  AND r.deleted = 0
  AND r.role_code LIKE 'MERCHANT_ADMIN\_%';

INSERT IGNORE INTO sys_role_menu (app_id, role_id, menu_id, deleted)
SELECT r.app_id, r.id, m.id, 0
FROM sys_role r
JOIN sys_menu m ON m.app_id = r.app_id AND m.deleted = 0
WHERE r.app_id = 2
  AND r.deleted = 0
  AND r.role_code LIKE 'MERCHANT_OPERATOR\_%'
  AND m.menu_code IN ('merchant_openapi_keys_v1', 'merchant_system_catalog_v1',
                      'merchant_system_dept_v1', 'merchant_system_post_v1',
                      'merchant_system_account_v1', 'merchant_system_role_v1',
                      'merchant_system_role_auth_v1');

INSERT IGNORE INTO sys_role_menu (app_id, role_id, menu_id, deleted)
SELECT r.app_id, r.id, m.id, 0
FROM sys_role r
JOIN sys_menu m ON m.app_id = r.app_id AND m.deleted = 0
WHERE r.app_id = 2
  AND r.deleted = 0
  AND r.role_code LIKE 'MERCHANT_VIEWER\_%'
  AND m.permission_code IN ('merchant:openapi:key:view', 'merchant:system:dept:list',
                            'merchant:system:post:list', 'merchant:system:account:list',
                            'merchant:system:role:list');

INSERT IGNORE INTO sys_role_permission (app_id, role_id, permission_id, deleted)
SELECT app_id, 1, id, 0 FROM sys_permission WHERE app_id = 1 AND status = 1 AND deleted = 0;

INSERT IGNORE INTO sys_role_permission (app_id, role_id, permission_id, deleted)
SELECT app_id, 2, id, 0 FROM sys_permission WHERE app_id = 2 AND deleted = 0;

INSERT IGNORE INTO sys_role_permission (app_id, role_id, permission_id, deleted)
SELECT r.app_id, r.id, p.id, 0
FROM sys_role r
JOIN sys_permission p ON p.app_id = r.app_id AND p.deleted = 0
WHERE r.app_id = 2
  AND r.deleted = 0
  AND r.role_code LIKE 'MERCHANT_ADMIN\_%';

INSERT IGNORE INTO sys_role_permission (app_id, role_id, permission_id, deleted)
SELECT r.app_id, r.id, p.id, 0
FROM sys_role r
JOIN sys_permission p ON p.app_id = r.app_id AND p.deleted = 0
WHERE r.app_id = 2
  AND r.deleted = 0
  AND r.role_code LIKE 'MERCHANT_OPERATOR\_%'
  AND p.permission_code IN ('merchant:openapi:key:view', 'merchant:openapi:key:copy',
                            'merchant:openapi:key:download', 'merchant:system:dept:list',
                            'merchant:system:post:list', 'merchant:system:account:list',
                            'merchant:system:role:list');

INSERT IGNORE INTO sys_role_permission (app_id, role_id, permission_id, deleted)
SELECT r.app_id, r.id, p.id, 0
FROM sys_role r
JOIN sys_permission p ON p.app_id = r.app_id AND p.deleted = 0
WHERE r.app_id = 2
  AND r.deleted = 0
  AND r.role_code LIKE 'MERCHANT_VIEWER\_%'
  AND p.permission_code IN ('merchant:openapi:key:view', 'merchant:system:dept:list',
                            'merchant:system:post:list', 'merchant:system:account:list',
                            'merchant:system:role:list');

INSERT IGNORE INTO sys_user_role (app_id, user_id, role_id, deleted)
SELECT ar.app_id, a.user_id, ar.role_id, 0
FROM sys_account_role ar
JOIN sys_account a ON a.id = ar.account_id AND a.app_id = ar.app_id AND a.deleted = 0
WHERE ar.deleted = 0;

INSERT IGNORE INTO sys_merchant_user (merchant_info_id, merchant_id, user_id, account_id, login_account, real_name, status, deleted)
SELECT m.id, m.merchant_id, a.user_id, a.id, a.login_account, u.real_name, a.status, 0
FROM sys_account a
JOIN sys_user u ON u.id = a.user_id AND u.deleted = 0
JOIN base_merchant_info m ON m.merchant_id = a.merchant_id AND m.deleted = 0
WHERE a.app_id = 2 AND a.deleted = 0 AND a.merchant_id IS NOT NULL;

INSERT IGNORE INTO sys_merchant_user_role (app_id, merchant_info_id, merchant_user_id, role_id, deleted)
SELECT ar.app_id, mu.merchant_info_id, mu.id, ar.role_id, 0
FROM sys_merchant_user mu
JOIN sys_account_role ar ON ar.account_id = mu.account_id AND ar.app_id = 2 AND ar.deleted = 0
WHERE mu.deleted = 0;

SET FOREIGN_KEY_CHECKS = 1;

-- ===================== 部门管理 =====================
CREATE TABLE IF NOT EXISTS sys_dept (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    app_id BIGINT NOT NULL COMMENT '系统应用ID',
    parent_id BIGINT NOT NULL DEFAULT 0 COMMENT '父部门ID，0为根节点',
    dept_name VARCHAR(100) NOT NULL COMMENT '部门名称',
    sort_no INT NOT NULL DEFAULT 100 COMMENT '显示排序',
    leader VARCHAR(50) NULL COMMENT '负责人',
    phone VARCHAR(30) NULL COMMENT '联系电话',
    email VARCHAR(150) NULL COMMENT '邮箱',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0停用，1启用',
    deleted BIGINT NOT NULL DEFAULT 0 COMMENT '删除标识：0未删除',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_sys_dept_app_parent (app_id, parent_id, status, deleted),
    KEY idx_sys_dept_deleted (deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='部门管理表';

-- ===================== 岗位管理 =====================
CREATE TABLE IF NOT EXISTS sys_post (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    app_id BIGINT NOT NULL COMMENT '系统应用ID',
    post_code VARCHAR(80) NOT NULL COMMENT '岗位编码',
    post_name VARCHAR(100) NOT NULL COMMENT '岗位名称',
    sort_no INT NOT NULL DEFAULT 100 COMMENT '显示排序',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0停用，1启用',
    remark VARCHAR(500) NULL COMMENT '备注',
    deleted BIGINT NOT NULL DEFAULT 0 COMMENT '删除标识：0未删除',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_post_app_code_deleted (app_id, post_code, deleted),
    KEY idx_sys_post_deleted (deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='岗位管理表';

-- ===================== 系统监控菜单 =====================
INSERT IGNORE INTO sys_menu (id, app_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path, permission_code, icon, visible, external_link, sort_no, status, deleted)
VALUES
    (220, 1, 0, 'system_monitor', '系统监控', 'CATALOG', '/monitor', NULL, NULL, 'Monitor', 1, 0, 80, 1, 0),
    (221, 1, 220, 'monitor_online', '在线用户', 'MENU', '/monitor/online', 'monitor/online/index', 'system:online:list', 'User', 1, 0, 81, 1, 0),
    (222, 1, 220, 'monitor_server', '服务监控', 'MENU', '/monitor/server', 'monitor/server/index', 'system:server:list', 'Cpu', 1, 0, 82, 1, 0),
    (223, 1, 220, 'monitor_cache', '缓存监控', 'MENU', '/monitor/cache', 'monitor/cache/index', 'system:cache:list', 'Coin', 1, 0, 83, 1, 0),
    (224, 1, 220, 'monitor_job', '任务调度', 'MENU', '/monitor/job', 'monitor/job/index', 'monitor:job:list', 'Clock', 1, 0, 84, 1, 0),
    (225, 1, 220, 'monitor_job_log', '任务日志', 'MENU', '/monitor/job-log', 'monitor/job-log/index', 'monitor:jobLog:list', 'Document', 1, 0, 85, 1, 0),
    (226, 1, 220, 'monitor_job_node', '执行节点', 'MENU', '/monitor/job-node', 'monitor/job-node/index', 'monitor:jobNode:list', 'Connection', 1, 0, 86, 1, 0),
    (227, 1, 220, 'monitor_datasource', '数据源监控', 'MENU', '/monitor/datasource', 'monitor/datasource/index', 'monitor:datasource:view', 'DataLine', 1, 0, 87, 1, 0),
    (228, 1, 220, 'monitor_rocketmq', 'RocketMQ 控制台', 'LINK', 'http://localhost:8088', NULL, 'monitor:rocketmq:view', 'Connection', 1, 1, 88, 1, 0),
    (229, 1, 220, 'monitor_nacos', 'Nacos 控制台', 'LINK', 'http://localhost:8848/nacos', NULL, 'monitor:nacos:view', 'Monitor', 1, 1, 89, 1, 0);

-- ===================== 部门/岗位/字典/参数/日志权限（挂载到正确的 menu_id） =====================
INSERT IGNORE INTO sys_permission (id, app_id, menu_id, permission_code, permission_name, permission_type, resource_method, resource_path, status, deleted)
VALUES
    (632, 1, 214, 'system:dept:list', '部门管理查询', 'BUTTON', 'GET', '/admin/system/dept/**', 1, 0),
    (633, 1, 214, 'system:dept:add', '部门管理新增', 'BUTTON', 'POST', '/admin/system/dept', 1, 0),
    (634, 1, 214, 'system:dept:edit', '部门管理编辑', 'BUTTON', 'PUT', '/admin/system/dept/**', 1, 0),
    (635, 1, 214, 'system:dept:remove', '部门管理删除', 'BUTTON', 'DELETE', '/admin/system/dept/**', 1, 0),
    (636, 1, 217, 'system:post:list', '岗位管理查询', 'BUTTON', 'GET', '/admin/system/post/**', 1, 0),
    (637, 1, 217, 'system:post:add', '岗位管理新增', 'BUTTON', 'POST', '/admin/system/post', 1, 0),
    (638, 1, 217, 'system:post:edit', '岗位管理编辑', 'BUTTON', 'PUT', '/admin/system/post/**', 1, 0),
    (639, 1, 217, 'system:post:remove', '岗位管理删除', 'BUTTON', 'DELETE', '/admin/system/post/**', 1, 0),
    (640, 1, 218, 'system:config:add', '参数管理新增', 'BUTTON', 'POST', '/admin/system/config/**', 1, 0),
    (641, 1, 218, 'system:config:edit', '参数管理编辑', 'BUTTON', 'PUT', '/admin/system/config/**', 1, 0),
    (642, 1, 215, 'system:dict:add', '字典管理新增', 'BUTTON', 'POST', '/admin/system/dict/**', 1, 0),
    (643, 1, 215, 'system:dict:edit', '字典管理编辑', 'BUTTON', 'PUT', '/admin/system/dict/**', 1, 0),
    (644, 1, 223, 'system:cache:query', '缓存详情查询', 'BUTTON', 'GET', '/admin/monitor/cache/keys', 1, 0),
    (645, 1, 223, 'system:cache:clear', '缓存删除', 'BUTTON', 'DELETE', '/admin/monitor/cache/key', 1, 0),
    (646, 1, 224, 'monitor:job:handler:list', '任务处理器查询', 'BUTTON', 'GET', '/admin/monitor/job/handlers', 1, 0),
    (647, 1, 224, 'monitor:job:query', '任务详情', 'BUTTON', 'POST', '/admin/monitor/job/search', 1, 0),
    (648, 1, 224, 'monitor:job:add', '任务新增', 'BUTTON', 'POST', '/admin/monitor/job', 1, 0),
    (649, 1, 224, 'monitor:job:edit', '任务修改', 'BUTTON', 'PUT', '/admin/monitor/job/**', 1, 0),
    (650, 1, 224, 'monitor:job:remove', '任务删除', 'BUTTON', 'DELETE', '/admin/monitor/job/**', 1, 0),
    (651, 1, 224, 'monitor:job:run', '任务手动执行', 'BUTTON', 'POST', '/admin/monitor/job/**/trigger', 1, 0),
    (652, 1, 224, 'monitor:job:start', '任务启用', 'BUTTON', 'PUT', '/admin/monitor/job/**/status', 1, 0),
    (653, 1, 224, 'monitor:job:stop', '任务停用', 'BUTTON', 'PUT', '/admin/monitor/job/**/status', 1, 0),
    (654, 1, 225, 'monitor:jobLog:query', '任务日志详情', 'BUTTON', 'POST', '/admin/monitor/job-log/search', 1, 0),
    (655, 1, 227, 'monitor:datasource:view', '数据源监控查看', 'MENU', NULL, NULL, 1, 0),
    (660, 1, 227, 'monitor:datasource:export', '数据源监控导出', 'BUTTON', 'GET', '/admin/monitor/datasource/export', 1, 0),
    (656, 1, 228, 'monitor:rocketmq:view', 'RocketMQ 控制台查看', 'MENU', NULL, NULL, 1, 0),
    (657, 1, 229, 'monitor:nacos:view', 'Nacos 控制台查看', 'MENU', NULL, NULL, 1, 0),
    (658, 1, 226, 'monitor:jobNode:query', '任务节点详情', 'BUTTON', 'GET', '/admin/monitor/job-node/list', 1, 0),
    (659, 1, 226, 'monitor:jobNode:refresh', '任务节点刷新', 'BUTTON', 'GET', '/admin/monitor/job-node/list', 1, 0);

INSERT IGNORE INTO sys_menu (id, app_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path, permission_code, icon, visible, sort_no, status, deleted)
VALUES
    (389, 1, 224, 'monitor_job_query', '任务详情', 'BUTTON', NULL, NULL, 'monitor:job:query', NULL, 0, 1, 1, 0),
    (390, 1, 224, 'monitor_job_add', '任务新增', 'BUTTON', NULL, NULL, 'monitor:job:add', NULL, 0, 2, 1, 0),
    (391, 1, 224, 'monitor_job_edit', '任务修改', 'BUTTON', NULL, NULL, 'monitor:job:edit', NULL, 0, 3, 1, 0),
    (392, 1, 224, 'monitor_job_remove', '任务删除', 'BUTTON', NULL, NULL, 'monitor:job:remove', NULL, 0, 4, 1, 0),
    (393, 1, 224, 'monitor_job_run', '手动执行', 'BUTTON', NULL, NULL, 'monitor:job:run', NULL, 0, 5, 1, 0),
    (394, 1, 224, 'monitor_job_start', '任务启用', 'BUTTON', NULL, NULL, 'monitor:job:start', NULL, 0, 6, 1, 0),
    (395, 1, 224, 'monitor_job_stop', '任务停用', 'BUTTON', NULL, NULL, 'monitor:job:stop', NULL, 0, 7, 1, 0),
    (396, 1, 225, 'monitor_job_log_query', '日志详情', 'BUTTON', NULL, NULL, 'monitor:jobLog:query', NULL, 0, 1, 1, 0),
    (400, 1, 226, 'monitor_job_node_query', '节点详情', 'BUTTON', NULL, NULL, 'monitor:jobNode:query', NULL, 0, 1, 1, 0),
    (401, 1, 226, 'monitor_job_node_refresh', '节点刷新', 'BUTTON', NULL, NULL, 'monitor:jobNode:refresh', NULL, 0, 2, 1, 0),
    (402, 1, 227, 'monitor_datasource_export', '数据源监控导出', 'BUTTON', NULL, NULL, 'monitor:datasource:export', NULL, 0, 1, 1, 0);

-- 将新权限授予 admin 角色（role_id=1）
INSERT IGNORE INTO sys_role_permission (app_id, role_id, permission_id, deleted)
SELECT 1, 1, id, 0 FROM sys_permission WHERE id BETWEEN 632 AND 660 AND deleted = 0;

INSERT IGNORE INTO sys_role_menu (app_id, role_id, menu_id, deleted)
SELECT 1, 1, id, 0 FROM sys_menu WHERE (id BETWEEN 227 AND 229 OR id BETWEEN 389 AND 402) AND deleted = 0;

-- =============================================================================
-- 国际化字典种子数据 (sys_dict_type + sys_dict_data)
-- 覆盖系统管理中所有前端需要的字典类型，支持 zh-CN 和 en-US
-- =============================================================================

-- ===================== 字典类型定义 =====================
INSERT IGNORE INTO sys_dict_type (id, dict_name, dict_type, biz_domain, system_builtin, editable, status, deleted) VALUES
(1,  '系统开关',      'sys_normal_disable',    'system', 1, 0, 1, 0),
(2,  '显示状态',      'sys_show_hide',          'system', 1, 0, 1, 0),
(3,  '是否',          'sys_yes_no',             'system', 1, 0, 1, 0),
(4,  '通知类型',      'sys_notice_type',        'system', 1, 0, 1, 0),
(5,  '菜单类型',      'sys_menu_type',          'system', 1, 0, 1, 0),
(6,  '权限类型',      'sys_permission_type',    'system', 1, 0, 1, 0),
(7,  '操作类型',      'sys_operation_type',     'system', 1, 0, 1, 0),
(8,  '操作状态',      'sys_oper_status',        'system', 1, 0, 1, 0),
(9,  '登录状态',      'sys_login_status',       'system', 1, 0, 1, 0),
(10, '角色类型',      'sys_role_type',          'system', 1, 0, 1, 0),
(11, '数据范围',      'sys_data_scope',         'system', 1, 0, 1, 0),
(12, '商户状态',      'sys_merchant_status',    'merchant', 1, 0, 1, 0),
(13, '风险等级',      'sys_risk_level',         'merchant', 1, 0, 1, 0),
(14, '配置值类型',    'sys_config_value_type',  'system', 1, 0, 1, 0),
(15, '用户状态',      'sys_user_status',        'system', 1, 0, 1, 0),
(16, '账号状态',      'sys_account_status',     'system', 1, 0, 1, 0),
(17, '岗位状态',      'sys_post_status',        'system', 1, 0, 1, 0);

-- ===================== 字典数据 — zh-CN =====================
INSERT IGNORE INTO sys_dict_data (id, dict_type, dict_label, dict_value, locale, dict_sort, list_class, is_default, status, deleted) VALUES
-- 系统开关
(100, 'sys_normal_disable', '启用', '1', 'zh-CN', 1, 'success', 1, 1, 0),
(101, 'sys_normal_disable', '停用', '0', 'zh-CN', 2, 'danger',  0, 1, 0),
-- 显示状态
(110, 'sys_show_hide', '显示', '1', 'zh-CN', 1, 'success', 1, 1, 0),
(111, 'sys_show_hide', '隐藏', '0', 'zh-CN', 2, 'warning', 0, 1, 0),
-- 是否
(120, 'sys_yes_no', '是', 'Y', 'zh-CN', 1, 'success', 1, 1, 0),
(121, 'sys_yes_no', '否', 'N', 'zh-CN', 2, 'danger',  0, 1, 0),
-- 通知类型
(130, 'sys_notice_type', '通知',   '1', 'zh-CN', 1, 'primary', 1, 1, 0),
(131, 'sys_notice_type', '公告',   '2', 'zh-CN', 2, 'warning', 0, 1, 0),
-- 菜单类型
(140, 'sys_menu_type', '目录', 'CATALOG', 'zh-CN', 1, 'primary', 1, 1, 0),
(141, 'sys_menu_type', '菜单', 'MENU',    'zh-CN', 2, 'success', 0, 1, 0),
(142, 'sys_menu_type', '按钮', 'BUTTON',  'zh-CN', 3, 'warning', 0, 1, 0),
(143, 'sys_menu_type', '外链', 'LINK',    'zh-CN', 4, 'info',    0, 1, 0),
-- 权限类型
(150, 'sys_permission_type', '菜单权限', 'MENU',   'zh-CN', 1, 'primary', 1, 1, 0),
(151, 'sys_permission_type', '按钮权限', 'BUTTON', 'zh-CN', 2, 'warning', 0, 1, 0),
(152, 'sys_permission_type', '接口权限', 'API',    'zh-CN', 3, 'success', 0, 1, 0),
(153, 'sys_permission_type', '数据权限', 'DATA',   'zh-CN', 4, 'info',    0, 1, 0),
-- 操作类型
(160, 'sys_operation_type', '新增', '1', 'zh-CN', 1, 'primary', 1, 1, 0),
(161, 'sys_operation_type', '修改', '2', 'zh-CN', 2, 'warning', 0, 1, 0),
(162, 'sys_operation_type', '删除', '3', 'zh-CN', 3, 'danger',  0, 1, 0),
(163, 'sys_operation_type', '查询', '4', 'zh-CN', 4, 'info',    0, 1, 0),
(164, 'sys_operation_type', '导出', '5', 'zh-CN', 5, 'success', 0, 1, 0),
(165, 'sys_operation_type', '审核', '6', 'zh-CN', 6, 'warning', 0, 1, 0),
(166, 'sys_operation_type', '冻结', '7', 'zh-CN', 7, 'danger',  0, 1, 0),
(167, 'sys_operation_type', '解冻', '8', 'zh-CN', 8, 'success', 0, 1, 0),
-- 操作状态
(170, 'sys_oper_status', '成功', '1', 'zh-CN', 1, 'success', 1, 1, 0),
(171, 'sys_oper_status', '失败', '0', 'zh-CN', 2, 'danger',  0, 1, 0),
-- 登录状态
(180, 'sys_login_status', '成功', '1', 'zh-CN', 1, 'success', 1, 1, 0),
(181, 'sys_login_status', '失败', '0', 'zh-CN', 2, 'danger',  0, 1, 0),
-- 角色类型
(190, 'sys_role_type', '系统角色',   'SYSTEM', 'zh-CN', 1, 'primary', 1, 1, 0),
(191, 'sys_role_type', '自定义角色', 'CUSTOM', 'zh-CN', 2, 'info',    0, 1, 0),
-- 数据范围
(200, 'sys_data_scope', '全部数据权限', 'ALL',      'zh-CN', 1, 'primary', 1, 1, 0),
(201, 'sys_data_scope', '自身数据权限', 'SELF',     'zh-CN', 2, 'success', 0, 1, 0),
(202, 'sys_data_scope', '自定义数据权限', 'CUSTOM', 'zh-CN', 3, 'warning', 0, 1, 0),
(203, 'sys_data_scope', '组织数据权限', 'ORG',      'zh-CN', 4, 'info',    0, 1, 0),
(204, 'sys_data_scope', '商户数据权限', 'MERCHANT', 'zh-CN', 5, 'info',    0, 1, 0),
(205, 'sys_data_scope', '店铺数据权限', 'STORE',    'zh-CN', 6, 'info',    0, 1, 0),
(206, 'sys_data_scope', '渠道数据权限', 'CHANNEL',  'zh-CN', 7, 'info',    0, 1, 0),
-- 商户状态
(210, 'sys_merchant_status', '正常', '1', 'zh-CN', 1, 'success', 1, 1, 0),
(211, 'sys_merchant_status', '冻结', '2', 'zh-CN', 2, 'warning', 0, 1, 0),
(212, 'sys_merchant_status', '关闭', '3', 'zh-CN', 3, 'danger',  0, 1, 0),
-- 风险等级
(220, 'sys_risk_level', '低', '1', 'zh-CN', 1, 'success', 1, 1, 0),
(221, 'sys_risk_level', '中', '2', 'zh-CN', 2, 'warning', 0, 1, 0),
(222, 'sys_risk_level', '高', '3', 'zh-CN', 3, 'danger',  0, 1, 0),
-- 配置值类型
(230, 'sys_config_value_type', '字符串', '1', 'zh-CN', 1, 'primary', 1, 1, 0),
(231, 'sys_config_value_type', '数字',   '2', 'zh-CN', 2, 'success', 0, 1, 0),
(232, 'sys_config_value_type', '布尔',   '3', 'zh-CN', 3, 'warning', 0, 1, 0),
(233, 'sys_config_value_type', 'JSON',   '4', 'zh-CN', 4, 'info',    0, 1, 0),
-- 用户状态 / 账号状态 / 岗位状态 (复用系统开关的值和颜色，仅标签略有不同)
(240, 'sys_user_status', '正常', '1', 'zh-CN', 1, 'success', 1, 1, 0),
(241, 'sys_user_status', '停用', '0', 'zh-CN', 2, 'danger',  0, 1, 0),
(250, 'sys_account_status', '正常', '1', 'zh-CN', 1, 'success', 1, 1, 0),
(251, 'sys_account_status', '停用', '0', 'zh-CN', 2, 'danger',  0, 1, 0),
(260, 'sys_post_status', '正常', '1', 'zh-CN', 1, 'success', 1, 1, 0),
(261, 'sys_post_status', '停用', '0', 'zh-CN', 2, 'danger',  0, 1, 0);

-- ===================== 字典数据 — en-US =====================
INSERT IGNORE INTO sys_dict_data (id, dict_type, dict_label, dict_value, locale, dict_sort, list_class, is_default, status, deleted) VALUES
-- Normal/Disable
(1100, 'sys_normal_disable', 'Enabled',  '1', 'en-US', 1, 'success', 1, 1, 0),
(1101, 'sys_normal_disable', 'Disabled', '0', 'en-US', 2, 'danger',  0, 1, 0),
-- Show/Hide
(1110, 'sys_show_hide', 'Show', '1', 'en-US', 1, 'success', 1, 1, 0),
(1111, 'sys_show_hide', 'Hide', '0', 'en-US', 2, 'warning', 0, 1, 0),
-- Yes/No
(1120, 'sys_yes_no', 'Yes', 'Y', 'en-US', 1, 'success', 1, 1, 0),
(1121, 'sys_yes_no', 'No',  'N', 'en-US', 2, 'danger',  0, 1, 0),
-- Notice Type
(1130, 'sys_notice_type', 'Notice',       '1', 'en-US', 1, 'primary', 1, 1, 0),
(1131, 'sys_notice_type', 'Announcement', '2', 'en-US', 2, 'warning', 0, 1, 0),
-- Menu Type
(1140, 'sys_menu_type', 'Catalog', 'CATALOG', 'en-US', 1, 'primary', 1, 1, 0),
(1141, 'sys_menu_type', 'Menu',    'MENU',    'en-US', 2, 'success', 0, 1, 0),
(1142, 'sys_menu_type', 'Button',  'BUTTON',  'en-US', 3, 'warning', 0, 1, 0),
(1143, 'sys_menu_type', 'Link',    'LINK',    'en-US', 4, 'info',    0, 1, 0),
-- Permission Type
(1150, 'sys_permission_type', 'Menu Permission',       'MENU',   'en-US', 1, 'primary', 1, 1, 0),
(1151, 'sys_permission_type', 'Button Permission',     'BUTTON', 'en-US', 2, 'warning', 0, 1, 0),
(1152, 'sys_permission_type', 'API Permission',        'API',    'en-US', 3, 'success', 0, 1, 0),
(1153, 'sys_permission_type', 'Data Scope Permission', 'DATA',   'en-US', 4, 'info',    0, 1, 0),
-- Operation Type
(1160, 'sys_operation_type', 'Create',   '1', 'en-US', 1, 'primary', 1, 1, 0),
(1161, 'sys_operation_type', 'Update',   '2', 'en-US', 2, 'warning', 0, 1, 0),
(1162, 'sys_operation_type', 'Delete',   '3', 'en-US', 3, 'danger',  0, 1, 0),
(1163, 'sys_operation_type', 'Query',    '4', 'en-US', 4, 'info',    0, 1, 0),
(1164, 'sys_operation_type', 'Export',   '5', 'en-US', 5, 'success', 0, 1, 0),
(1165, 'sys_operation_type', 'Audit',    '6', 'en-US', 6, 'warning', 0, 1, 0),
(1166, 'sys_operation_type', 'Freeze',   '7', 'en-US', 7, 'danger',  0, 1, 0),
(1167, 'sys_operation_type', 'Unfreeze', '8', 'en-US', 8, 'success', 0, 1, 0),
-- Operation Status
(1170, 'sys_oper_status', 'Success', '1', 'en-US', 1, 'success', 1, 1, 0),
(1171, 'sys_oper_status', 'Failure', '0', 'en-US', 2, 'danger',  0, 1, 0),
-- Login Status
(1180, 'sys_login_status', 'Success', '1', 'en-US', 1, 'success', 1, 1, 0),
(1181, 'sys_login_status', 'Failure', '0', 'en-US', 2, 'danger',  0, 1, 0),
-- Role Type
(1190, 'sys_role_type', 'System',  'SYSTEM', 'en-US', 1, 'primary', 1, 1, 0),
(1191, 'sys_role_type', 'Custom',  'CUSTOM', 'en-US', 2, 'info',    0, 1, 0),
-- Data Scope
(1200, 'sys_data_scope', 'All Data',          'ALL',      'en-US', 1, 'primary', 1, 1, 0),
(1201, 'sys_data_scope', 'Self Data',         'SELF',     'en-US', 2, 'success', 0, 1, 0),
(1202, 'sys_data_scope', 'Custom Data',       'CUSTOM',   'en-US', 3, 'warning', 0, 1, 0),
(1203, 'sys_data_scope', 'Organization Data', 'ORG',      'en-US', 4, 'info',    0, 1, 0),
(1204, 'sys_data_scope', 'Merchant Data',     'MERCHANT', 'en-US', 5, 'info',    0, 1, 0),
(1205, 'sys_data_scope', 'Store Data',        'STORE',    'en-US', 6, 'info',    0, 1, 0),
(1206, 'sys_data_scope', 'Channel Data',      'CHANNEL',  'en-US', 7, 'info',    0, 1, 0),
-- Merchant Status
(1210, 'sys_merchant_status', 'Active', '1', 'en-US', 1, 'success', 1, 1, 0),
(1211, 'sys_merchant_status', 'Frozen', '2', 'en-US', 2, 'warning', 0, 1, 0),
(1212, 'sys_merchant_status', 'Closed', '3', 'en-US', 3, 'danger',  0, 1, 0),
-- Risk Level
(1220, 'sys_risk_level', 'Low',    '1', 'en-US', 1, 'success', 1, 1, 0),
(1221, 'sys_risk_level', 'Medium', '2', 'en-US', 2, 'warning', 0, 1, 0),
(1222, 'sys_risk_level', 'High',   '3', 'en-US', 3, 'danger',  0, 1, 0),
-- Config Value Type
(1230, 'sys_config_value_type', 'String',  '1', 'en-US', 1, 'primary', 1, 1, 0),
(1231, 'sys_config_value_type', 'Number',  '2', 'en-US', 2, 'success', 0, 1, 0),
(1232, 'sys_config_value_type', 'Boolean', '3', 'en-US', 3, 'warning', 0, 1, 0),
(1233, 'sys_config_value_type', 'JSON',    '4', 'en-US', 4, 'info',    0, 1, 0),
-- User / Account / Post Status
(1240, 'sys_user_status', 'Active',   '1', 'en-US', 1, 'success', 1, 1, 0),
(1241, 'sys_user_status', 'Inactive', '0', 'en-US', 2, 'danger',  0, 1, 0),
(1250, 'sys_account_status', 'Active',   '1', 'en-US', 1, 'success', 1, 1, 0),
(1251, 'sys_account_status', 'Inactive', '0', 'en-US', 2, 'danger',  0, 1, 0),
(1260, 'sys_post_status', 'Active',   '1', 'en-US', 1, 'success', 1, 1, 0),
(1261, 'sys_post_status', 'Inactive', '0', 'en-US', 2, 'danger',  0, 1, 0);

-- =============================================================================
-- 渠道管理基础表、字典、菜单与权限
-- =============================================================================

CREATE TABLE IF NOT EXISTS channel_info (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    channel_code VARCHAR(64) NOT NULL COMMENT '渠道编码，全局唯一',
    channel_cn_name VARCHAR(128) NOT NULL COMMENT '渠道中文名称',
    channel_en_name VARCHAR(128) NOT NULL COMMENT '渠道英文名称',
    channel_status TINYINT NOT NULL DEFAULT 1 COMMENT '渠道状态：0停用，1启用',
    support_acquiring TINYINT NOT NULL DEFAULT 0 COMMENT '是否支持收单：0否，1是',
    support_payout TINYINT NOT NULL DEFAULT 0 COMMENT '是否支持代付：0否，1是',
    support_3ds TINYINT NOT NULL DEFAULT 0 COMMENT '是否支持3DS：0否，1是',
    default_request_url VARCHAR(512) NULL COMMENT '默认渠道请求地址',
    default_interaction_mode VARCHAR(32) NULL COMMENT '默认交互方式',
    sort_order INT NOT NULL DEFAULT 0 COMMENT '排序',
    remark VARCHAR(512) NULL COMMENT '备注',
    create_by VARCHAR(64) NULL COMMENT '创建人',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_by VARCHAR(64) NULL COMMENT '更新人',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted BIGINT NOT NULL DEFAULT 0 COMMENT '删除标识：0未删除，大于0为删除记录ID',
    PRIMARY KEY (id),
    UNIQUE KEY uk_channel_info_code_deleted (channel_code, deleted),
    KEY idx_channel_info_status (channel_status, deleted),
    KEY idx_channel_info_capability (support_acquiring, support_payout, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='渠道信息表';

CREATE TABLE IF NOT EXISTS channel_payment_capability (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    channel_id BIGINT NOT NULL COMMENT '渠道ID',
    channel_code VARCHAR(64) NOT NULL COMMENT '渠道编码',
    business_type VARCHAR(32) NOT NULL COMMENT '业务类型：ACQUIRING/PAYOUT',
    payment_method VARCHAR(64) NOT NULL COMMENT '支付方式',
    transaction_type VARCHAR(512) NOT NULL DEFAULT 'NONE' COMMENT '交易类型列表，多个以英文逗号分隔，代付为NONE',
    support_3ds TINYINT NOT NULL DEFAULT 0 COMMENT '是否支持3DS：0否，1是',
    support_incremental_authorization TINYINT NOT NULL DEFAULT 0 COMMENT '是否支持增量授权：0否，1是',
    capability_status TINYINT NOT NULL DEFAULT 1 COMMENT '能力状态：0停用，1启用',
    sort_order INT NOT NULL DEFAULT 0 COMMENT '排序',
    remark VARCHAR(512) NULL COMMENT '备注',
    create_by VARCHAR(64) NULL COMMENT '创建人',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_by VARCHAR(64) NULL COMMENT '更新人',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted BIGINT NOT NULL DEFAULT 0 COMMENT '删除标识：0未删除，大于0为删除记录ID',
    PRIMARY KEY (id),
    UNIQUE KEY uk_channel_capability_scope (channel_id, business_type, payment_method, deleted),
    KEY idx_channel_capability_channel (channel_id, deleted),
    KEY idx_channel_capability_code (channel_code, deleted),
    KEY idx_channel_capability_method (business_type, payment_method, deleted),
    KEY idx_channel_capability_status (capability_status, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='渠道支付能力表';

CREATE TABLE IF NOT EXISTS channel_capability_currency (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    capability_id BIGINT NOT NULL COMMENT '渠道支付能力ID',
    channel_id BIGINT NOT NULL COMMENT '渠道ID',
    channel_code VARCHAR(64) NOT NULL COMMENT '渠道编码',
    currency_code VARCHAR(3) NOT NULL COMMENT '币种代码',
    currency_status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0停用，1启用',
    create_by VARCHAR(64) NULL COMMENT '创建人',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_by VARCHAR(64) NULL COMMENT '更新人',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted BIGINT NOT NULL DEFAULT 0 COMMENT '删除标识：0未删除，大于0为删除记录ID',
    PRIMARY KEY (id),
    UNIQUE KEY uk_capability_currency_deleted (capability_id, currency_code, deleted),
    KEY idx_capability_currency_channel (channel_id, deleted),
    KEY idx_capability_currency_code (currency_code, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='渠道支付能力币种表';

CREATE TABLE IF NOT EXISTS channel_capability_card_brand (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    capability_id BIGINT NOT NULL COMMENT '渠道支付能力ID',
    channel_id BIGINT NOT NULL COMMENT '渠道ID',
    channel_code VARCHAR(64) NOT NULL COMMENT '渠道编码',
    card_brand VARCHAR(64) NOT NULL COMMENT '卡品牌，如 VISA/MASTERCARD',
    brand_status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0停用，1启用',
    sort_order INT NOT NULL DEFAULT 0 COMMENT '排序',
    create_by VARCHAR(64) NULL COMMENT '创建人',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_by VARCHAR(64) NULL COMMENT '更新人',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted BIGINT NOT NULL DEFAULT 0 COMMENT '删除标识：0未删除，大于0为删除记录ID',
    PRIMARY KEY (id),
    UNIQUE KEY uk_capability_card_brand_deleted (capability_id, card_brand, deleted),
    KEY idx_capability_card_brand_channel (channel_id, deleted),
    KEY idx_capability_card_brand_brand (card_brand, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='渠道支付能力卡品牌表';

CREATE TABLE IF NOT EXISTS channel_limit_rule (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    channel_id BIGINT NOT NULL COMMENT '渠道ID',
    channel_code VARCHAR(64) NOT NULL COMMENT '渠道编码',
    business_type VARCHAR(32) NOT NULL COMMENT '业务类型：ACQUIRING/PAYOUT',
    payment_method VARCHAR(64) NOT NULL DEFAULT 'ALL' COMMENT '支付方式，ALL表示渠道级限额',
    card_brand VARCHAR(64) NOT NULL DEFAULT 'ALL' COMMENT '卡品牌，ALL表示不限卡品牌',
    limit_type VARCHAR(32) NOT NULL COMMENT '限额类型：SINGLE_MIN/SINGLE_MAX/DAILY/WEEKLY/MONTHLY',
    limit_currency VARCHAR(3) NOT NULL DEFAULT 'USD' COMMENT '限额币种，当前固定USD',
    limit_amount DECIMAL(20, 6) NOT NULL COMMENT '限额金额',
    rule_status TINYINT NOT NULL DEFAULT 1 COMMENT '规则状态：0停用，1启用',
    remark VARCHAR(512) NULL COMMENT '备注',
    create_by VARCHAR(64) NULL COMMENT '创建人',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_by VARCHAR(64) NULL COMMENT '更新人',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted BIGINT NOT NULL DEFAULT 0 COMMENT '删除标识：0未删除，大于0为删除记录ID',
    PRIMARY KEY (id),
    UNIQUE KEY uk_channel_limit_scope_deleted (channel_id, business_type, payment_method, card_brand, limit_type, deleted),
    KEY idx_channel_limit_channel (channel_id, deleted),
    KEY idx_channel_limit_code (channel_code, deleted),
    KEY idx_channel_limit_status (rule_status, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='渠道限额规则表';

SET @drop_channel_limit_transaction_type = (
    SELECT IF(COUNT(*) > 0, 'ALTER TABLE channel_limit_rule DROP COLUMN transaction_type', 'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'channel_limit_rule'
      AND column_name = 'transaction_type'
);
PREPARE stmt FROM @drop_channel_limit_transaction_type;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @drop_channel_limit_effective_start_time = (
    SELECT IF(COUNT(*) > 0, 'ALTER TABLE channel_limit_rule DROP COLUMN effective_start_time', 'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'channel_limit_rule'
      AND column_name = 'effective_start_time'
);
PREPARE stmt FROM @drop_channel_limit_effective_start_time;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @drop_channel_limit_effective_end_time = (
    SELECT IF(COUNT(*) > 0, 'ALTER TABLE channel_limit_rule DROP COLUMN effective_end_time', 'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'channel_limit_rule'
      AND column_name = 'effective_end_time'
);
PREPARE stmt FROM @drop_channel_limit_effective_end_time;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

INSERT IGNORE INTO sys_dict_type (id, dict_name, dict_type, biz_domain, system_builtin, editable, status, deleted) VALUES
(30, '收单支付方式', 'acquiring_payment_method', 'channel', 1, 1, 1, 0),
(31, '代付支付方式', 'payout_payment_method', 'channel', 1, 1, 1, 0),
(32, '交易类型', 'transaction_type', 'payment', 1, 1, 1, 0),
(33, '交易状态', 'transaction_status', 'payment', 1, 1, 1, 0),
(34, '卡品牌', 'card_brand', 'channel', 1, 1, 1, 0),
(35, '渠道业务类型', 'channel_business_type', 'channel', 1, 1, 1, 0),
(36, '渠道交互方式', 'channel_interaction_mode', 'channel', 1, 1, 1, 0),
(37, '渠道环境', 'channel_env_mode', 'channel', 1, 1, 1, 0),
(38, '渠道限额类型', 'channel_limit_type', 'channel', 1, 1, 1, 0);

INSERT IGNORE INTO sys_dict_data (id, dict_type, dict_label, dict_value, locale, dict_sort, list_class, extra_json, is_default, status, deleted) VALUES
(3000, 'acquiring_payment_method', '银行卡', 'BANK_CARD', 'zh-CN', 1, 'primary', '{"logoKey":"bankCard","logoKeys":["visa","mastercard","jcb","maestro"]}', 1, 1, 0),
(3001, 'acquiring_payment_method', 'PayPal', 'PAYPAL', 'zh-CN', 2, 'primary', '{"logoKey":"paypal","logoKeys":["paypal"]}', 0, 1, 0),
(3002, 'acquiring_payment_method', 'Apple Pay', 'APPLE_PAY', 'zh-CN', 3, 'primary', '{"logoKey":"applePay","logoKeys":["applePay"]}', 0, 1, 0),
(3003, 'acquiring_payment_method', 'Google Pay', 'GOOGLE_PAY', 'zh-CN', 4, 'primary', '{"logoKey":"googlePay","logoKeys":["googlePay"]}', 0, 1, 0),
(3004, 'acquiring_payment_method', 'Cash App Pay', 'CASH_APP_PAY', 'zh-CN', 5, 'primary', '{"logoKey":"cashAppPay","logoKeys":["cashAppPay"]}', 0, 1, 0),
(3005, 'acquiring_payment_method', 'ACH Debit', 'ACH_DEBIT', 'zh-CN', 6, 'primary', '{"logoKey":"achDebit","logoKeys":["achDebit"]}', 0, 1, 0),
(3006, 'acquiring_payment_method', '银行转账', 'BANK_TRANSFER', 'zh-CN', 7, 'primary', '{"logoKey":"bankTransfer","logoKeys":["bankTransfer"]}', 0, 1, 0),
(3100, 'payout_payment_method', 'PayPal', 'PAYPAL', 'zh-CN', 1, 'primary', '{"logoKey":"paypal","logoKeys":["paypal"]}', 1, 1, 0),
(3101, 'payout_payment_method', 'Cash App Pay', 'CASH_APP_PAY', 'zh-CN', 2, 'primary', '{"logoKey":"cashAppPay","logoKeys":["cashAppPay"]}', 0, 1, 0),
(3102, 'payout_payment_method', '银行转账', 'BANK_TRANSFER', 'zh-CN', 3, 'primary', '{"logoKey":"bankTransfer","logoKeys":["bankTransfer"]}', 0, 1, 0),
(3200, 'transaction_type', '授权', 'AUTHORIZATION', 'zh-CN', 1, 'primary', NULL, 0, 1, 0),
(3201, 'transaction_type', '请款', 'CAPTURE', 'zh-CN', 2, 'primary', NULL, 0, 1, 0),
(3202, 'transaction_type', '支付', 'PAYMENT', 'zh-CN', 3, 'primary', NULL, 1, 1, 0),
(3203, 'transaction_type', '预授权', 'PRE_AUTHORIZATION', 'zh-CN', 4, 'primary', NULL, 0, 1, 0),
(3204, 'transaction_type', '预授权完成', 'PRE_AUTH_COMPLETION', 'zh-CN', 5, 'primary', NULL, 0, 1, 0),
(3205, 'transaction_type', '退款', 'REFUND', 'zh-CN', 6, 'warning', NULL, 0, 1, 0),
(3206, 'transaction_type', '撤销', 'VOID', 'zh-CN', 7, 'warning', NULL, 0, 1, 0),
(3207, 'transaction_type', '冲正', 'REVERSAL', 'zh-CN', 8, 'warning', NULL, 0, 1, 0),
(3208, 'transaction_type', '拒付', 'CHARGEBACK', 'zh-CN', 9, 'danger', NULL, 0, 1, 0),
(3209, 'transaction_type', '二次请款', 'REPRESENTMENT', 'zh-CN', 10, 'warning', NULL, 0, 1, 0),
(3210, 'transaction_type', '调单', 'RETRIEVAL_REQUEST', 'zh-CN', 11, 'info', NULL, 0, 1, 0),
(3300, 'transaction_status', '成功', 'SUCCESS', 'zh-CN', 1, 'success', NULL, 0, 1, 0),
(3301, 'transaction_status', '失败', 'FAILED', 'zh-CN', 2, 'danger', NULL, 0, 1, 0),
(3302, 'transaction_status', '待处理', 'PENDING', 'zh-CN', 3, 'warning', NULL, 0, 1, 0),
(3303, 'transaction_status', '处理中', 'PROCESSING', 'zh-CN', 4, 'primary', NULL, 0, 1, 0),
(3400, 'card_brand', 'Visa', 'VISA', 'zh-CN', 1, 'primary', '{"logoKey":"visa","logoKeys":["visa"]}', 0, 1, 0),
(3401, 'card_brand', 'Mastercard', 'MASTERCARD', 'zh-CN', 2, 'primary', '{"logoKey":"mastercard","logoKeys":["mastercard"]}', 0, 1, 0),
(3402, 'card_brand', 'JCB', 'JCB', 'zh-CN', 3, 'primary', '{"logoKey":"jcb","logoKeys":["jcb"]}', 0, 1, 0),
(3403, 'card_brand', 'Maestro', 'MAESTRO', 'zh-CN', 4, 'primary', '{"logoKey":"maestro","logoKeys":["maestro"]}', 0, 1, 0),
(3404, 'card_brand', 'American Express', 'AMEX', 'zh-CN', 5, 'primary', '{"logoKey":"americanExpress","logoKeys":["americanExpress"]}', 0, 1, 0),
(3405, 'card_brand', 'Diners Club', 'DINERS_CLUB', 'zh-CN', 6, 'primary', '{"logoKey":"dinersClub","logoKeys":["dinersClub"]}', 0, 1, 0),
(3406, 'card_brand', 'Discover', 'DISCOVER', 'zh-CN', 7, 'primary', '{"logoKey":"discover","logoKeys":["discover"]}', 0, 1, 0),
(3407, 'card_brand', 'UnionPay', 'UNIONPAY', 'zh-CN', 8, 'primary', '{"logoKey":"unionPay","logoKeys":["unionPay"]}', 0, 1, 0),
(3408, 'card_brand', 'Cartes Bancaires', 'CARTES_BANCAIRES', 'zh-CN', 9, 'primary', NULL, 0, 1, 0),
(3409, 'card_brand', 'eftpos Australia', 'EFTPOS_AUSTRALIA', 'zh-CN', 10, 'primary', NULL, 0, 1, 0),
(3410, 'card_brand', 'Interac', 'INTERAC', 'zh-CN', 11, 'primary', NULL, 0, 1, 0),
(3500, 'channel_business_type', '收单', 'ACQUIRING', 'zh-CN', 1, 'primary', NULL, 1, 1, 0),
(3501, 'channel_business_type', '代付', 'PAYOUT', 'zh-CN', 2, 'primary', NULL, 0, 1, 0),
(3600, 'channel_interaction_mode', 'API Key', 'API_KEY', 'zh-CN', 1, 'primary', NULL, 1, 1, 0),
(3601, 'channel_interaction_mode', '单向证书', 'SINGLE_CERT', 'zh-CN', 2, 'primary', NULL, 0, 1, 0),
(3602, 'channel_interaction_mode', '双向证书', 'MUTUAL_TLS', 'zh-CN', 3, 'primary', NULL, 0, 1, 0),
(3603, 'channel_interaction_mode', 'API + 双向证书', 'API_MUTUAL_TLS', 'zh-CN', 4, 'primary', NULL, 0, 1, 0),
(3700, 'channel_env_mode', '测试环境', 'TEST', 'zh-CN', 1, 'warning', NULL, 1, 1, 0),
(3701, 'channel_env_mode', '生产环境', 'PROD', 'zh-CN', 2, 'danger', NULL, 0, 1, 0),
(3800, 'channel_limit_type', '单笔最低限额', 'SINGLE_MIN', 'zh-CN', 1, 'primary', NULL, 0, 1, 0),
(3801, 'channel_limit_type', '单笔最高限额', 'SINGLE_MAX', 'zh-CN', 2, 'primary', NULL, 0, 1, 0),
(3802, 'channel_limit_type', '日限额', 'DAILY', 'zh-CN', 3, 'primary', NULL, 0, 1, 0),
(3803, 'channel_limit_type', '周限额', 'WEEKLY', 'zh-CN', 4, 'primary', NULL, 0, 1, 0),
(3804, 'channel_limit_type', '月限额', 'MONTHLY', 'zh-CN', 5, 'primary', NULL, 0, 1, 0);

INSERT INTO sys_dict_data (dict_type, dict_label, dict_value, locale, dict_sort, list_class, extra_json, is_default, status, deleted)
SELECT 'card_brand', '未知', 'UNKNOWN', 'zh-CN', 99, 'info', NULL, 0, 1, 0
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict_data WHERE dict_type = 'card_brand' AND dict_value = 'UNKNOWN' AND locale = 'zh-CN' AND deleted = 0
);

INSERT IGNORE INTO sys_dict_data (id, dict_type, dict_label, dict_value, locale, dict_sort, list_class, extra_json, is_default, status, deleted) VALUES
(13000, 'acquiring_payment_method', 'Bank Card', 'BANK_CARD', 'en-US', 1, 'primary', '{"logoKey":"bankCard","logoKeys":["visa","mastercard","jcb","maestro"]}', 1, 1, 0),
(13001, 'acquiring_payment_method', 'PayPal', 'PAYPAL', 'en-US', 2, 'primary', '{"logoKey":"paypal","logoKeys":["paypal"]}', 0, 1, 0),
(13002, 'acquiring_payment_method', 'Apple Pay', 'APPLE_PAY', 'en-US', 3, 'primary', '{"logoKey":"applePay","logoKeys":["applePay"]}', 0, 1, 0),
(13003, 'acquiring_payment_method', 'Google Pay', 'GOOGLE_PAY', 'en-US', 4, 'primary', '{"logoKey":"googlePay","logoKeys":["googlePay"]}', 0, 1, 0),
(13004, 'acquiring_payment_method', 'Cash App Pay', 'CASH_APP_PAY', 'en-US', 5, 'primary', '{"logoKey":"cashAppPay","logoKeys":["cashAppPay"]}', 0, 1, 0),
(13005, 'acquiring_payment_method', 'ACH Debit', 'ACH_DEBIT', 'en-US', 6, 'primary', '{"logoKey":"achDebit","logoKeys":["achDebit"]}', 0, 1, 0),
(13006, 'acquiring_payment_method', 'Bank Transfer', 'BANK_TRANSFER', 'en-US', 7, 'primary', '{"logoKey":"bankTransfer","logoKeys":["bankTransfer"]}', 0, 1, 0),
(13100, 'payout_payment_method', 'PayPal', 'PAYPAL', 'en-US', 1, 'primary', '{"logoKey":"paypal","logoKeys":["paypal"]}', 1, 1, 0),
(13101, 'payout_payment_method', 'Cash App Pay', 'CASH_APP_PAY', 'en-US', 2, 'primary', '{"logoKey":"cashAppPay","logoKeys":["cashAppPay"]}', 0, 1, 0),
(13102, 'payout_payment_method', 'Bank Transfer', 'BANK_TRANSFER', 'en-US', 3, 'primary', '{"logoKey":"bankTransfer","logoKeys":["bankTransfer"]}', 0, 1, 0),
(13200, 'transaction_type', 'Authorization', 'AUTHORIZATION', 'en-US', 1, 'primary', NULL, 0, 1, 0),
(13201, 'transaction_type', 'Capture', 'CAPTURE', 'en-US', 2, 'primary', NULL, 0, 1, 0),
(13202, 'transaction_type', 'Payment', 'PAYMENT', 'en-US', 3, 'primary', NULL, 1, 1, 0),
(13203, 'transaction_type', 'Pre-Authorization', 'PRE_AUTHORIZATION', 'en-US', 4, 'primary', NULL, 0, 1, 0),
(13204, 'transaction_type', 'Pre-Authorization Completion', 'PRE_AUTH_COMPLETION', 'en-US', 5, 'primary', NULL, 0, 1, 0),
(13205, 'transaction_type', 'Refund', 'REFUND', 'en-US', 6, 'warning', NULL, 0, 1, 0),
(13206, 'transaction_type', 'Void', 'VOID', 'en-US', 7, 'warning', NULL, 0, 1, 0),
(13207, 'transaction_type', 'Reversal', 'REVERSAL', 'en-US', 8, 'warning', NULL, 0, 1, 0),
(13208, 'transaction_type', 'Chargeback', 'CHARGEBACK', 'en-US', 9, 'danger', NULL, 0, 1, 0),
(13209, 'transaction_type', 'Representment', 'REPRESENTMENT', 'en-US', 10, 'warning', NULL, 0, 1, 0),
(13210, 'transaction_type', 'Retrieval Request', 'RETRIEVAL_REQUEST', 'en-US', 11, 'info', NULL, 0, 1, 0),
(13300, 'transaction_status', 'Success', 'SUCCESS', 'en-US', 1, 'success', NULL, 0, 1, 0),
(13301, 'transaction_status', 'Failed', 'FAILED', 'en-US', 2, 'danger', NULL, 0, 1, 0),
(13302, 'transaction_status', 'Pending', 'PENDING', 'en-US', 3, 'warning', NULL, 0, 1, 0),
(13303, 'transaction_status', 'Processing', 'PROCESSING', 'en-US', 4, 'primary', NULL, 0, 1, 0),
(13400, 'card_brand', 'Visa', 'VISA', 'en-US', 1, 'primary', '{"logoKey":"visa","logoKeys":["visa"]}', 0, 1, 0),
(13401, 'card_brand', 'Mastercard', 'MASTERCARD', 'en-US', 2, 'primary', '{"logoKey":"mastercard","logoKeys":["mastercard"]}', 0, 1, 0),
(13402, 'card_brand', 'JCB', 'JCB', 'en-US', 3, 'primary', '{"logoKey":"jcb","logoKeys":["jcb"]}', 0, 1, 0),
(13403, 'card_brand', 'Maestro', 'MAESTRO', 'en-US', 4, 'primary', '{"logoKey":"maestro","logoKeys":["maestro"]}', 0, 1, 0),
(13404, 'card_brand', 'American Express', 'AMEX', 'en-US', 5, 'primary', '{"logoKey":"americanExpress","logoKeys":["americanExpress"]}', 0, 1, 0),
(13405, 'card_brand', 'Diners Club', 'DINERS_CLUB', 'en-US', 6, 'primary', '{"logoKey":"dinersClub","logoKeys":["dinersClub"]}', 0, 1, 0),
(13406, 'card_brand', 'Discover', 'DISCOVER', 'en-US', 7, 'primary', '{"logoKey":"discover","logoKeys":["discover"]}', 0, 1, 0),
(13407, 'card_brand', 'UnionPay', 'UNIONPAY', 'en-US', 8, 'primary', '{"logoKey":"unionPay","logoKeys":["unionPay"]}', 0, 1, 0),
(13408, 'card_brand', 'Cartes Bancaires', 'CARTES_BANCAIRES', 'en-US', 9, 'primary', NULL, 0, 1, 0),
(13409, 'card_brand', 'eftpos Australia', 'EFTPOS_AUSTRALIA', 'en-US', 10, 'primary', NULL, 0, 1, 0),
(13410, 'card_brand', 'Interac', 'INTERAC', 'en-US', 11, 'primary', NULL, 0, 1, 0);

INSERT INTO sys_dict_data (dict_type, dict_label, dict_value, locale, dict_sort, list_class, extra_json, is_default, status, deleted)
SELECT 'card_brand', 'Unknown', 'UNKNOWN', 'en-US', 99, 'info', NULL, 0, 1, 0
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict_data WHERE dict_type = 'card_brand' AND dict_value = 'UNKNOWN' AND locale = 'en-US' AND deleted = 0
);

UPDATE base_mcc_risk_policy
SET card_scheme = 'AMEX'
WHERE card_scheme = 'AMERICAN_EXPRESS'
  AND deleted = 0;

DELETE FROM sys_dict_data
WHERE dict_type IN ('card_scheme', 'deprecated_card_scheme');

DELETE FROM sys_dict_type
WHERE dict_type IN ('card_scheme', 'deprecated_card_scheme');

UPDATE sys_menu
SET visible = 0, status = 0,
    updated_at = CURRENT_TIMESTAMP(3)
WHERE app_id = 1 AND menu_code = 'admin_channel' AND deleted = 0;

UPDATE sys_menu
SET parent_id = 230, updated_at = CURRENT_TIMESTAMP(3)
WHERE app_id = 1
  AND menu_code IN ('admin_merchant_menu_manage_v1', 'admin_merchant_menu_grant_v3', 'admin_merchant_user_query_v1')
  AND deleted = 0;

UPDATE sys_menu
SET parent_id = 240, updated_at = CURRENT_TIMESTAMP(3)
WHERE app_id = 1
  AND menu_code = 'base_mcc'
  AND deleted = 0;

INSERT IGNORE INTO sys_menu (id, app_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path, permission_code, icon, visible, sort_no, status, deleted)
VALUES
(280, 1, 0, 'admin_channel_catalog_v1', '渠道管理', 'CATALOG', '/channel', NULL, 'channel', 'Connection', 1, 40, 1, 0),
(281, 1, 280, 'admin_channel_info_v1', '渠道信息管理', 'MENU', '/channel/info', 'channel/info', 'channel:info:list', 'Connection', 1, 41, 1, 0),
(282, 1, 280, 'admin_channel_capability_v1', '渠道支付能力管理', 'MENU', '/channel/capability', 'channel/capability', 'channel:capability:list', 'CreditCard', 1, 42, 1, 0),
(283, 1, 280, 'admin_channel_limit_v1', '渠道限额管理', 'MENU', '/channel/limit', 'channel/limit', 'channel:limit:list', 'Money', 1, 43, 1, 0);

INSERT IGNORE INTO sys_permission (id, app_id, menu_id, permission_code, permission_name, permission_type, resource_method, resource_path, status, deleted)
VALUES
(700, 1, 280, 'channel', '渠道管理目录', 'MENU', 'GET', '/channel/**', 1, 0),
(701, 1, 281, 'channel:info:list', '渠道信息查询', 'MENU', 'POST', '/admin/channel/info/search', 1, 0),
(702, 1, 281, 'channel:info:detail', '渠道信息详情', 'BUTTON', 'GET', '/admin/channel/info/*', 1, 0),
(703, 1, 281, 'channel:info:add', '渠道信息新增', 'BUTTON', 'POST', '/admin/channel/info', 1, 0),
(704, 1, 281, 'channel:info:edit', '渠道信息修改', 'BUTTON', 'PUT', '/admin/channel/info/*', 1, 0),
(705, 1, 281, 'channel:info:remove', '渠道信息删除', 'BUTTON', 'DELETE', '/admin/channel/info/*', 1, 0),
(706, 1, 281, 'channel:info:status', '渠道信息状态', 'BUTTON', 'PUT', '/admin/channel/info/*/status', 1, 0),
(710, 1, 282, 'channel:capability:list', '渠道支付能力查询', 'MENU', 'POST', '/admin/channel/capabilities/search', 1, 0),
(711, 1, 282, 'channel:capability:detail', '渠道支付能力详情', 'BUTTON', 'GET', '/admin/channel/capabilities/*', 1, 0),
(712, 1, 282, 'channel:capability:add', '渠道支付能力新增', 'BUTTON', 'POST', '/admin/channel/capabilities', 1, 0),
(713, 1, 282, 'channel:capability:edit', '渠道支付能力修改', 'BUTTON', 'PUT', '/admin/channel/capabilities/*', 1, 0),
(714, 1, 282, 'channel:capability:remove', '渠道支付能力删除', 'BUTTON', 'DELETE', '/admin/channel/capabilities/*', 1, 0),
(715, 1, 282, 'channel:capability:status', '渠道支付能力状态', 'BUTTON', 'PUT', '/admin/channel/capabilities/*/status', 1, 0),
(720, 1, 283, 'channel:limit:list', '渠道限额查询', 'MENU', 'POST', '/admin/channel/limits/search', 1, 0),
(721, 1, 283, 'channel:limit:detail', '渠道限额详情', 'BUTTON', 'GET', '/admin/channel/limits/*', 1, 0),
(722, 1, 283, 'channel:limit:add', '渠道限额新增', 'BUTTON', 'POST', '/admin/channel/limits', 1, 0),
(723, 1, 283, 'channel:limit:edit', '渠道限额修改', 'BUTTON', 'PUT', '/admin/channel/limits/*', 1, 0),
(724, 1, 283, 'channel:limit:remove', '渠道限额删除', 'BUTTON', 'DELETE', '/admin/channel/limits/*', 1, 0),
(725, 1, 283, 'channel:limit:status', '渠道限额状态', 'BUTTON', 'PUT', '/admin/channel/limits/*/status', 1, 0),
(726, 1, 283, 'channel:limit:dimensionEdit', '渠道限额维度编辑', 'BUTTON', 'PUT', '/admin/channel/limits/dimension', 1, 0);

INSERT INTO sys_menu (app_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path, permission_code, icon, visible, sort_no, status, deleted)
SELECT 1, parent.id, button.menu_code, button.menu_name, 'BUTTON', NULL, NULL, button.permission_code, NULL, 0, button.sort_no, 1, 0
FROM sys_menu parent
JOIN (
    SELECT 'admin_channel_info_v1' parent_code, 'admin_channel_info_detail_v1' menu_code, '渠道信息详情' menu_name, 'channel:info:detail' permission_code, 1 sort_no
    UNION ALL SELECT 'admin_channel_info_v1', 'admin_channel_info_add_v1', '渠道信息新增', 'channel:info:add', 2
    UNION ALL SELECT 'admin_channel_info_v1', 'admin_channel_info_edit_v1', '渠道信息修改', 'channel:info:edit', 3
    UNION ALL SELECT 'admin_channel_info_v1', 'admin_channel_info_remove_v1', '渠道信息删除', 'channel:info:remove', 4
    UNION ALL SELECT 'admin_channel_info_v1', 'admin_channel_info_status_v1', '渠道信息状态', 'channel:info:status', 5
    UNION ALL SELECT 'admin_channel_capability_v1', 'admin_channel_capability_detail_v1', '渠道支付能力详情', 'channel:capability:detail', 1
    UNION ALL SELECT 'admin_channel_capability_v1', 'admin_channel_capability_add_v1', '渠道支付能力新增', 'channel:capability:add', 2
    UNION ALL SELECT 'admin_channel_capability_v1', 'admin_channel_capability_edit_v1', '渠道支付能力修改', 'channel:capability:edit', 3
    UNION ALL SELECT 'admin_channel_capability_v1', 'admin_channel_capability_remove_v1', '渠道支付能力删除', 'channel:capability:remove', 4
    UNION ALL SELECT 'admin_channel_capability_v1', 'admin_channel_capability_status_v1', '渠道支付能力状态', 'channel:capability:status', 5
    UNION ALL SELECT 'admin_channel_limit_v1', 'admin_channel_limit_detail_v1', '渠道限额详情', 'channel:limit:detail', 1
    UNION ALL SELECT 'admin_channel_limit_v1', 'admin_channel_limit_add_v1', '渠道限额新增', 'channel:limit:add', 2
    UNION ALL SELECT 'admin_channel_limit_v1', 'admin_channel_limit_edit_v1', '渠道限额修改', 'channel:limit:edit', 3
    UNION ALL SELECT 'admin_channel_limit_v1', 'admin_channel_limit_dimension_edit_v1', '渠道限额维度编辑', 'channel:limit:dimensionEdit', 4
    UNION ALL SELECT 'admin_channel_limit_v1', 'admin_channel_limit_remove_v1', '渠道限额删除', 'channel:limit:remove', 5
    UNION ALL SELECT 'admin_channel_limit_v1', 'admin_channel_limit_status_v1', '渠道限额状态', 'channel:limit:status', 6
) button ON button.parent_code = parent.menu_code
WHERE parent.app_id = 1
  AND parent.deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM sys_menu exists_menu
      WHERE exists_menu.app_id = 1
        AND exists_menu.menu_code = button.menu_code
        AND exists_menu.deleted = 0
  );

INSERT IGNORE INTO sys_role_menu (app_id, role_id, menu_id, deleted)
SELECT 1, 1, id, 0
FROM sys_menu
WHERE app_id = 1
  AND deleted = 0
  AND (
      id BETWEEN 280 AND 284
      OR menu_code LIKE 'admin_channel_%_v1'
  );

INSERT IGNORE INTO sys_role_permission (app_id, role_id, permission_id, deleted)
SELECT 1, 1, id, 0
FROM sys_permission
WHERE app_id = 1
  AND deleted = 0
  AND (permission_code = 'channel' OR permission_code LIKE 'channel:%');

CREATE TABLE IF NOT EXISTS msg_email_account (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    account_code VARCHAR(64) NOT NULL COMMENT '账户编码',
    account_name VARCHAR(100) NOT NULL COMMENT '账户名称',
    app_code VARCHAR(32) NOT NULL COMMENT '所属系统：ADMIN管理系统，MERCHANT商户系统',
    scope_type VARCHAR(32) NOT NULL COMMENT '配置范围：SYSTEM系统默认，MERCHANT指定商户',
    merchant_id VARCHAR(64) NULL COMMENT '商户ID',
    merchant_no VARCHAR(64) NULL COMMENT '商户号',
    merchant_name VARCHAR(200) NULL COMMENT '商户名称',
    scene_code VARCHAR(64) NOT NULL DEFAULT 'COMMON' COMMENT '适用场景',
    provider_type VARCHAR(32) NOT NULL DEFAULT 'SMTP' COMMENT '邮件服务商类型',
    from_name VARCHAR(100) NOT NULL COMMENT '发件人名称',
    from_email VARCHAR(255) NOT NULL COMMENT '发件邮箱',
    reply_to_email VARCHAR(255) NULL COMMENT '回复邮箱',
    smtp_host VARCHAR(255) NOT NULL COMMENT 'SMTP服务器地址',
    smtp_port INT NOT NULL COMMENT 'SMTP端口',
    encryption_type VARCHAR(32) NOT NULL DEFAULT 'SSL' COMMENT '加密方式：SSL/TLS/STARTTLS/NONE',
    smtp_auth_required TINYINT NOT NULL DEFAULT 1 COMMENT '是否需要SMTP认证：0否，1是',
    smtp_username VARCHAR(255) NOT NULL COMMENT 'SMTP账号',
    smtp_password_cipher TEXT NULL COMMENT 'SMTP密码密文',
    password_updated_time DATETIME(3) NULL COMMENT '密码更新时间',
    connect_timeout_ms INT NOT NULL DEFAULT 10000 COMMENT '连接超时时间，单位毫秒',
    read_timeout_ms INT NOT NULL DEFAULT 30000 COMMENT '读取超时时间，单位毫秒',
    default_flag TINYINT NOT NULL DEFAULT 0 COMMENT '是否默认账户：0否，1是',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0停用，1启用',
    verify_status TINYINT NOT NULL DEFAULT 0 COMMENT '验证状态：0未验证，1验证成功，2验证失败',
    last_test_time DATETIME(3) NULL COMMENT '最近测试时间',
    last_error_message VARCHAR(1000) NULL COMMENT '最近失败原因',
    minute_limit INT NOT NULL DEFAULT 60 COMMENT '单分钟最大发送数',
    daily_limit INT NOT NULL DEFAULT 10000 COMMENT '单日最大发送数',
    remark VARCHAR(500) NULL COMMENT '备注',
    sort_order INT NOT NULL DEFAULT 0 COMMENT '排序',
    create_by VARCHAR(64) NULL COMMENT '创建人',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_by VARCHAR(64) NULL COMMENT '更新人',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted BIGINT NOT NULL DEFAULT 0 COMMENT '删除标识：0未删除，大于0为删除记录ID',
    PRIMARY KEY (id),
    UNIQUE KEY uk_email_account_code_deleted (account_code, deleted),
    KEY idx_email_account_route (app_code, scope_type, merchant_id, scene_code, default_flag, status, deleted),
    KEY idx_email_account_merchant (merchant_id, merchant_no, deleted),
    KEY idx_email_account_from_email (from_email),
    KEY idx_email_account_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='邮件发件账户配置表';

CREATE TABLE IF NOT EXISTS msg_email_template (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    template_code VARCHAR(100) NOT NULL COMMENT '模板编码',
    template_name VARCHAR(150) NOT NULL COMMENT '模板名称',
    app_code VARCHAR(32) NOT NULL COMMENT '所属系统：ADMIN管理系统，MERCHANT商户系统，COMMON通用',
    scene_code VARCHAR(64) NOT NULL COMMENT '模板场景',
    locale VARCHAR(20) NOT NULL DEFAULT 'zh-CN' COMMENT '语言',
    subject_template VARCHAR(500) NOT NULL COMMENT '邮件标题模板',
    content_type VARCHAR(20) NOT NULL DEFAULT 'HTML' COMMENT '内容类型：HTML/TEXT',
    content_template LONGTEXT NOT NULL COMMENT '邮件正文模板',
    variable_schema JSON NULL COMMENT '模板变量定义',
    sensitive_variable_names JSON NULL COMMENT '敏感变量名称',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0停用，1启用',
    system_builtin TINYINT NOT NULL DEFAULT 0 COMMENT '是否系统内置：0否，1是',
    version_no INT NOT NULL DEFAULT 1 COMMENT '版本号',
    remark VARCHAR(500) NULL COMMENT '备注',
    create_by VARCHAR(64) NULL COMMENT '创建人',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_by VARCHAR(64) NULL COMMENT '更新人',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted BIGINT NOT NULL DEFAULT 0 COMMENT '删除标识：0未删除，大于0为删除记录ID',
    PRIMARY KEY (id),
    UNIQUE KEY uk_email_template_code_locale_deleted (template_code, locale, deleted),
    KEY idx_email_template_app_scene (app_code, scene_code, locale, status, deleted),
    KEY idx_email_template_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='邮件模板表';

CREATE TABLE IF NOT EXISTS msg_email_send_record (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    email_no VARCHAR(64) NOT NULL COMMENT '邮件流水号',
    app_code VARCHAR(32) NOT NULL COMMENT '所属系统',
    merchant_id VARCHAR(64) NULL COMMENT '商户ID',
    merchant_no VARCHAR(64) NULL COMMENT '商户号',
    merchant_name VARCHAR(200) NULL COMMENT '商户名称',
    scene_code VARCHAR(64) NOT NULL COMMENT '邮件场景',
    template_code VARCHAR(100) NULL COMMENT '模板编码',
    template_name VARCHAR(150) NULL COMMENT '模板名称',
    locale VARCHAR(20) NOT NULL DEFAULT 'zh-CN' COMMENT '语言',
    account_id BIGINT NULL COMMENT '发件账户ID',
    account_code VARCHAR(64) NULL COMMENT '发件账户编码',
    provider_type VARCHAR(32) NULL COMMENT '邮件服务商类型',
    from_name VARCHAR(100) NULL COMMENT '发件人名称',
    from_email VARCHAR(255) NULL COMMENT '发件邮箱',
    reply_to_email VARCHAR(255) NULL COMMENT '回复邮箱',
    to_emails TEXT NOT NULL COMMENT '收件人邮箱JSON数组',
    cc_emails TEXT NULL COMMENT '抄送邮箱JSON数组',
    bcc_emails TEXT NULL COMMENT '密送邮箱JSON数组',
    subject VARCHAR(500) NOT NULL COMMENT '邮件标题',
    content_snapshot LONGTEXT NULL COMMENT '邮件正文快照，敏感内容需脱敏',
    variables_snapshot JSON NULL COMMENT '模板变量快照，敏感变量需脱敏',
    biz_type VARCHAR(64) NULL COMMENT '业务类型',
    biz_no VARCHAR(100) NULL COMMENT '业务单号',
    send_status TINYINT NOT NULL DEFAULT 0 COMMENT '发送状态：0待发送，1发送中，2发送成功，3发送失败，4重试中，5已取消',
    retry_count INT NOT NULL DEFAULT 0 COMMENT '已重试次数',
    max_retry_count INT NOT NULL DEFAULT 0 COMMENT '最大重试次数',
    next_retry_time DATETIME(3) NULL COMMENT '下次重试时间',
    send_start_time DATETIME(3) NULL COMMENT '发送开始时间',
    send_end_time DATETIME(3) NULL COMMENT '发送结束时间',
    send_success_time DATETIME(3) NULL COMMENT '发送成功时间',
    cost_ms BIGINT NULL COMMENT '发送耗时，单位毫秒',
    error_code VARCHAR(100) NULL COMMENT '错误编码',
    error_message VARCHAR(2000) NULL COMMENT '错误信息',
    operator_id BIGINT NULL COMMENT '操作人ID',
    operator_name VARCHAR(100) NULL COMMENT '操作人名称',
    create_by VARCHAR(64) NULL COMMENT '创建人',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_by VARCHAR(64) NULL COMMENT '更新人',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted BIGINT NOT NULL DEFAULT 0 COMMENT '删除标识：0未删除，大于0为删除记录ID',
    PRIMARY KEY (id),
    UNIQUE KEY uk_email_send_record_no (email_no),
    KEY idx_email_record_app_scene (app_code, scene_code, send_status, deleted),
    KEY idx_email_record_merchant (merchant_id, merchant_no, deleted),
    KEY idx_email_record_template (template_code),
    KEY idx_email_record_biz (biz_type, biz_no),
    KEY idx_email_record_create_time (create_time),
    KEY idx_email_record_send_time (send_success_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='邮件发送记录表';

INSERT INTO sys_dict_type (id, dict_name, dict_type, biz_domain, system_builtin, editable, status, deleted)
SELECT item.id, item.dict_name, item.dict_type, 'email', 1, 1, 1, 0
FROM (
    SELECT 50 id, '邮件所属系统' dict_name, 'email_app_code' dict_type
    UNION ALL SELECT 51, '邮件账户范围', 'email_scope_type'
    UNION ALL SELECT 52, '邮件场景', 'email_scene_code'
    UNION ALL SELECT 53, '邮件服务商', 'email_provider_type'
    UNION ALL SELECT 54, 'SMTP加密方式', 'email_encryption_type'
    UNION ALL SELECT 55, '邮件验证状态', 'email_verify_status'
    UNION ALL SELECT 56, '邮件发送状态', 'email_send_status'
    UNION ALL SELECT 57, '邮件内容类型', 'email_content_type'
) item
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict_type exists_type
    WHERE exists_type.dict_type = item.dict_type AND exists_type.deleted = 0
);

INSERT IGNORE INTO sys_dict_data (id, dict_type, dict_label, dict_value, locale, dict_sort, list_class, is_default, status, deleted)
VALUES
(5000, 'email_app_code', '管理系统', 'ADMIN', 'zh-CN', 1, 'primary', 1, 1, 0),
(5001, 'email_app_code', '商户系统', 'MERCHANT', 'zh-CN', 2, 'success', 0, 1, 0),
(5002, 'email_app_code', '通用', 'COMMON', 'zh-CN', 3, 'info', 0, 1, 0),
(5010, 'email_scope_type', '系统默认', 'SYSTEM', 'zh-CN', 1, 'primary', 1, 1, 0),
(5011, 'email_scope_type', '指定商户', 'MERCHANT', 'zh-CN', 2, 'success', 0, 1, 0),
(5020, 'email_scene_code', '通用', 'COMMON', 'zh-CN', 1, 'primary', 1, 1, 0),
(5021, 'email_scene_code', '登录验证码', 'LOGIN_OTP', 'zh-CN', 2, 'warning', 0, 1, 0),
(5022, 'email_scene_code', '找回密码', 'PASSWORD_RESET', 'zh-CN', 3, 'warning', 0, 1, 0),
(5023, 'email_scene_code', '账号创建通知', 'ACCOUNT_CREATED', 'zh-CN', 4, 'success', 0, 1, 0),
(5024, 'email_scene_code', '商户开户通知', 'MERCHANT_ONBOARDING', 'zh-CN', 5, 'success', 0, 1, 0),
(5025, 'email_scene_code', '密钥变更通知', 'API_KEY_CHANGED', 'zh-CN', 6, 'danger', 0, 1, 0),
(5030, 'email_provider_type', 'SMTP', 'SMTP', 'zh-CN', 1, 'primary', 1, 1, 0),
(5040, 'email_encryption_type', 'SSL', 'SSL', 'zh-CN', 1, 'primary', 1, 1, 0),
(5041, 'email_encryption_type', 'TLS', 'TLS', 'zh-CN', 2, 'primary', 0, 1, 0),
(5042, 'email_encryption_type', 'STARTTLS', 'STARTTLS', 'zh-CN', 3, 'success', 0, 1, 0),
(5043, 'email_encryption_type', '不加密', 'NONE', 'zh-CN', 4, 'info', 0, 1, 0),
(5050, 'email_verify_status', '未验证', '0', 'zh-CN', 1, 'info', 1, 1, 0),
(5051, 'email_verify_status', '验证成功', '1', 'zh-CN', 2, 'success', 0, 1, 0),
(5052, 'email_verify_status', '验证失败', '2', 'zh-CN', 3, 'danger', 0, 1, 0),
(5060, 'email_send_status', '待发送', '0', 'zh-CN', 1, 'info', 1, 1, 0),
(5061, 'email_send_status', '发送中', '1', 'zh-CN', 2, 'warning', 0, 1, 0),
(5062, 'email_send_status', '发送成功', '2', 'zh-CN', 3, 'success', 0, 1, 0),
(5063, 'email_send_status', '发送失败', '3', 'zh-CN', 4, 'danger', 0, 1, 0),
(5064, 'email_send_status', '重试中', '4', 'zh-CN', 5, 'warning', 0, 1, 0),
(5065, 'email_send_status', '已取消', '5', 'zh-CN', 6, 'info', 0, 1, 0),
(5070, 'email_content_type', 'HTML', 'HTML', 'zh-CN', 1, 'primary', 1, 1, 0),
(5071, 'email_content_type', '纯文本', 'TEXT', 'zh-CN', 2, 'info', 0, 1, 0),
(15000, 'email_app_code', 'Admin', 'ADMIN', 'en-US', 1, 'primary', 1, 1, 0),
(15001, 'email_app_code', 'Merchant', 'MERCHANT', 'en-US', 2, 'success', 0, 1, 0),
(15002, 'email_app_code', 'Common', 'COMMON', 'en-US', 3, 'info', 0, 1, 0),
(15010, 'email_scope_type', 'System Default', 'SYSTEM', 'en-US', 1, 'primary', 1, 1, 0),
(15011, 'email_scope_type', 'Merchant Account', 'MERCHANT', 'en-US', 2, 'success', 0, 1, 0),
(15020, 'email_scene_code', 'Common', 'COMMON', 'en-US', 1, 'primary', 1, 1, 0),
(15021, 'email_scene_code', 'Login OTP', 'LOGIN_OTP', 'en-US', 2, 'warning', 0, 1, 0),
(15022, 'email_scene_code', 'Password Reset', 'PASSWORD_RESET', 'en-US', 3, 'warning', 0, 1, 0),
(15023, 'email_scene_code', 'Account Created', 'ACCOUNT_CREATED', 'en-US', 4, 'success', 0, 1, 0),
(15024, 'email_scene_code', 'Merchant Onboarding', 'MERCHANT_ONBOARDING', 'en-US', 5, 'success', 0, 1, 0),
(15025, 'email_scene_code', 'API Key Changed', 'API_KEY_CHANGED', 'en-US', 6, 'danger', 0, 1, 0),
(15030, 'email_provider_type', 'SMTP', 'SMTP', 'en-US', 1, 'primary', 1, 1, 0),
(15040, 'email_encryption_type', 'SSL', 'SSL', 'en-US', 1, 'primary', 1, 1, 0),
(15041, 'email_encryption_type', 'TLS', 'TLS', 'en-US', 2, 'primary', 0, 1, 0),
(15042, 'email_encryption_type', 'STARTTLS', 'STARTTLS', 'en-US', 3, 'success', 0, 1, 0),
(15043, 'email_encryption_type', 'None', 'NONE', 'en-US', 4, 'info', 0, 1, 0),
(15050, 'email_verify_status', 'Unverified', '0', 'en-US', 1, 'info', 1, 1, 0),
(15051, 'email_verify_status', 'Verified', '1', 'en-US', 2, 'success', 0, 1, 0),
(15052, 'email_verify_status', 'Failed', '2', 'en-US', 3, 'danger', 0, 1, 0),
(15060, 'email_send_status', 'Pending', '0', 'en-US', 1, 'info', 1, 1, 0),
(15061, 'email_send_status', 'Sending', '1', 'en-US', 2, 'warning', 0, 1, 0),
(15062, 'email_send_status', 'Success', '2', 'en-US', 3, 'success', 0, 1, 0),
(15063, 'email_send_status', 'Failed', '3', 'en-US', 4, 'danger', 0, 1, 0),
(15064, 'email_send_status', 'Retrying', '4', 'en-US', 5, 'warning', 0, 1, 0),
(15065, 'email_send_status', 'Cancelled', '5', 'en-US', 6, 'info', 0, 1, 0),
(15070, 'email_content_type', 'HTML', 'HTML', 'en-US', 1, 'primary', 1, 1, 0),
(15071, 'email_content_type', 'Text', 'TEXT', 'en-US', 2, 'info', 0, 1, 0);

INSERT INTO msg_email_template (
    template_code, template_name, app_code, scene_code, locale, subject_template, content_type,
    content_template, variable_schema, sensitive_variable_names, status, system_builtin, version_no,
    remark, create_by, update_by, deleted
)
SELECT item.template_code, item.template_name, item.app_code, item.scene_code, item.locale, item.subject_template, item.content_type,
       item.content_template, item.variable_schema, item.sensitive_variable_names, 1, 1, 1,
       item.remark, 'system', 'system', 0
FROM (
    SELECT 'ADMIN_LOGIN_OTP' template_code, '管理系统登录验证码' template_name, 'ADMIN' app_code, 'LOGIN_OTP' scene_code, 'zh-CN' locale,
           '【${systemName}】登录验证码' subject_template, 'HTML' content_type,
           '<p>您好，${userName}：</p><p>您正在登录 ${systemName}，本次登录验证码为：</p><p style="font-size: 24px; font-weight: bold;">${verifyCode}</p><p>验证码有效期为 ${expireMinutes} 分钟，请勿将验证码泄露给他人。</p><p>如果本次操作不是您本人发起，请立即联系系统管理员。</p><p>${systemName}</p>' content_template,
           '{"systemName":"Vexra Admin","userName":"张三","verifyCode":"123456","expireMinutes":"5"}' variable_schema,
           '["verifyCode"]' sensitive_variable_names, '系统内置模板：管理系统登录验证码' remark
    UNION ALL SELECT 'MERCHANT_LOGIN_OTP', '商户系统登录验证码', 'MERCHANT', 'LOGIN_OTP', 'zh-CN',
           '【${systemName}】登录验证码', 'HTML',
           '<p>您好，${userName}：</p><p>您正在登录 ${systemName}，本次登录验证码为：</p><p style="font-size: 24px; font-weight: bold;">${verifyCode}</p><p>验证码有效期为 ${expireMinutes} 分钟，请勿将验证码泄露给他人。</p><p>如非本人操作，请及时修改密码或联系平台客服。</p><p>${systemName}</p>',
           '{"systemName":"Vexra Merchant","userName":"张三","verifyCode":"123456","expireMinutes":"5"}',
           '["verifyCode"]', '系统内置模板：商户系统登录验证码'
    UNION ALL SELECT 'ADMIN_PASSWORD_RESET', '管理系统找回密码', 'ADMIN', 'PASSWORD_RESET', 'zh-CN',
           '【${systemName}】找回密码验证', 'HTML',
           '<p>您好，${userName}：</p><p>您正在进行 ${systemName} 找回密码操作。</p><p>验证码为：</p><p style="font-size: 24px; font-weight: bold;">${verifyCode}</p><p>验证码有效期为 ${expireMinutes} 分钟，请在有效期内完成密码重置。</p><p>如果不是您本人操作，请忽略本邮件并及时联系系统管理员。</p><p>${systemName}</p>',
           '{"systemName":"Vexra Admin","userName":"张三","verifyCode":"123456","expireMinutes":"5","resetLink":"https://admin.example.com/reset"}',
           '["verifyCode","resetLink"]', '系统内置模板：管理系统找回密码'
    UNION ALL SELECT 'MERCHANT_PASSWORD_RESET', '商户系统找回密码', 'MERCHANT', 'PASSWORD_RESET', 'zh-CN',
           '【${systemName}】找回密码验证', 'HTML',
           '<p>您好，${userName}：</p><p>您正在进行 ${systemName} 找回密码操作。</p><p>验证码为：</p><p style="font-size: 24px; font-weight: bold;">${verifyCode}</p><p>验证码有效期为 ${expireMinutes} 分钟，请在有效期内完成密码重置。</p><p>如果不是您本人操作，请忽略本邮件。</p><p>${systemName}</p>',
           '{"systemName":"Vexra Merchant","userName":"张三","verifyCode":"123456","expireMinutes":"5","resetLink":"https://merchant.example.com/reset"}',
           '["verifyCode","resetLink"]', '系统内置模板：商户系统找回密码'
    UNION ALL SELECT 'ADMIN_ACCOUNT_CREATED', '管理系统账号创建通知', 'ADMIN', 'ACCOUNT_CREATED', 'zh-CN',
           '【${systemName}】账号创建通知', 'HTML',
           '<p>您好，${userName}：</p><p>您的 ${systemName} 账号已创建成功。</p><p>登录账号：${loginAccount}</p><p>登录地址：${loginUrl}</p><p>如系统生成了初始密码，请在首次登录后立即修改密码。</p><p>如果您未申请该账号，请联系系统管理员。</p><p>${systemName}</p>',
           '{"systemName":"Vexra Admin","userName":"张三","loginAccount":"admin@example.com","loginUrl":"https://admin.example.com/login","initialPassword":"******"}',
           '["initialPassword"]', '系统内置模板：管理系统账号创建通知'
    UNION ALL SELECT 'MERCHANT_ACCOUNT_CREATED', '商户系统账号创建通知', 'MERCHANT', 'ACCOUNT_CREATED', 'zh-CN',
           '【${systemName}】账号创建通知', 'HTML',
           '<p>您好，${userName}：</p><p>您的 ${systemName} 账号已创建成功。</p><p>商户名称：${merchantName}</p><p>登录账号：${loginAccount}</p><p>登录地址：${loginUrl}</p><p>请妥善保管账号信息，并在首次登录后及时修改密码。</p><p>${systemName}</p>',
           '{"systemName":"Vexra Merchant","userName":"张三","merchantName":"示例商户","loginAccount":"merchant@example.com","loginUrl":"https://merchant.example.com/login","initialPassword":"******"}',
           '["initialPassword"]', '系统内置模板：商户系统账号创建通知'
    UNION ALL SELECT 'MERCHANT_ONBOARDING_APPROVED', '商户开户审核成功通知', 'MERCHANT', 'MERCHANT_ONBOARDING', 'zh-CN',
           '【${systemName}】商户开户审核通过', 'HTML',
           '<p>您好，${merchantName}：</p><p>您的商户开户申请已审核通过。</p><p>商户号：${merchantNo}</p><p>审核时间：${reviewTime}</p><p>您可以登录商户系统查看商户资料、配置 API 密钥并进行后续对接。</p><p>登录地址：${loginUrl}</p><p>${systemName}</p>',
           '{"systemName":"Vexra Merchant","merchantName":"示例商户","merchantNo":"M10000001","reviewTime":"2026-07-04 10:00:00","loginUrl":"https://merchant.example.com/login"}',
           '[]', '系统内置模板：商户开户审核成功通知'
    UNION ALL SELECT 'MERCHANT_ONBOARDING_REJECTED', '商户开户审核失败通知', 'MERCHANT', 'MERCHANT_ONBOARDING', 'zh-CN',
           '【${systemName}】商户开户审核未通过', 'HTML',
           '<p>您好，${merchantName}：</p><p>您的商户开户申请暂未通过审核。</p><p>商户号：${merchantNo}</p><p>审核时间：${reviewTime}</p><p>未通过原因：${rejectReason}</p><p>请根据提示补充或修改资料后重新提交。</p><p>${systemName}</p>',
           '{"systemName":"Vexra Merchant","merchantName":"示例商户","merchantNo":"M10000001","reviewTime":"2026-07-04 10:00:00","rejectReason":"资料不完整"}',
           '[]', '系统内置模板：商户开户审核失败通知'
    UNION ALL SELECT 'API_KEY_CREATED', 'API 密钥生成通知', 'MERCHANT', 'API_KEY_CHANGED', 'zh-CN',
           '【${systemName}】API 密钥生成通知', 'HTML',
           '<p>您好，${merchantName}：</p><p>您的商户 API 密钥已生成。</p><p>商户号：${merchantNo}</p><p>密钥名称：${keyName}</p><p>密钥尾号：${keyLast4}</p><p>操作人：${operatorName}</p><p>操作时间：${operationTime}</p><p>为保障账户安全，邮件中不会展示完整密钥内容。请登录商户系统查看或下载相关密钥信息。</p><p>${systemName}</p>',
           '{"systemName":"Vexra Merchant","merchantName":"示例商户","merchantNo":"M10000001","keyName":"默认 API 密钥","keyLast4":"1234","operatorName":"张三","operationTime":"2026-07-04 10:00:00"}',
           '[]', '系统内置模板：API 密钥生成通知'
    UNION ALL SELECT 'API_KEY_RESET', 'API 密钥重置通知', 'MERCHANT', 'API_KEY_CHANGED', 'zh-CN',
           '【${systemName}】API 密钥重置通知', 'HTML',
           '<p>您好，${merchantName}：</p><p>您的商户 API 密钥已被重置。</p><p>商户号：${merchantNo}</p><p>密钥名称：${keyName}</p><p>新密钥尾号：${keyLast4}</p><p>操作人：${operatorName}</p><p>操作时间：${operationTime}</p><p>请确认该操作是否由授权人员发起。如非本人或授权人员操作，请立即联系平台客服。</p><p>${systemName}</p>',
           '{"systemName":"Vexra Merchant","merchantName":"示例商户","merchantNo":"M10000001","keyName":"默认 API 密钥","keyLast4":"5678","operatorName":"张三","operationTime":"2026-07-04 10:00:00"}',
           '[]', '系统内置模板：API 密钥重置通知'
    UNION ALL SELECT 'API_KEY_ENABLED', 'API 密钥启用通知', 'MERCHANT', 'API_KEY_CHANGED', 'zh-CN',
           '【${systemName}】API 密钥启用通知', 'HTML',
           '<p>您好，${merchantName}：</p><p>您的商户 API 密钥已启用。</p><p>商户号：${merchantNo}</p><p>密钥名称：${keyName}</p><p>密钥尾号：${keyLast4}</p><p>操作人：${operatorName}</p><p>操作时间：${operationTime}</p><p>${systemName}</p>',
           '{"systemName":"Vexra Merchant","merchantName":"示例商户","merchantNo":"M10000001","keyName":"默认 API 密钥","keyLast4":"1234","operatorName":"张三","operationTime":"2026-07-04 10:00:00"}',
           '[]', '系统内置模板：API 密钥启用通知'
    UNION ALL SELECT 'API_KEY_DISABLED', 'API 密钥停用通知', 'MERCHANT', 'API_KEY_CHANGED', 'zh-CN',
           '【${systemName}】API 密钥停用通知', 'HTML',
           '<p>您好，${merchantName}：</p><p>您的商户 API 密钥已停用。</p><p>商户号：${merchantNo}</p><p>密钥名称：${keyName}</p><p>密钥尾号：${keyLast4}</p><p>操作人：${operatorName}</p><p>操作时间：${operationTime}</p><p>如果该操作不是您或授权人员发起，请及时联系平台客服。</p><p>${systemName}</p>',
           '{"systemName":"Vexra Merchant","merchantName":"示例商户","merchantNo":"M10000001","keyName":"默认 API 密钥","keyLast4":"1234","operatorName":"张三","operationTime":"2026-07-04 10:00:00"}',
           '[]', '系统内置模板：API 密钥停用通知'
) item
WHERE NOT EXISTS (
    SELECT 1 FROM msg_email_template exists_template
    WHERE exists_template.template_code = item.template_code
      AND exists_template.locale = item.locale
      AND exists_template.deleted = 0
);

UPDATE msg_email_template
SET content_template = CASE template_code
    WHEN 'ADMIN_LOGIN_OTP' THEN '<div style="margin:0;padding:32px;background:#f4f7fb;font-family:Arial,sans-serif;color:#1f2937;"><div style="max-width:640px;margin:0 auto;background:#ffffff;border:1px solid #e5e7eb;border-radius:10px;overflow:hidden;"><div style="padding:22px 28px;background:#0f172a;color:#ffffff;"><div style="font-size:13px;opacity:.78;">${systemName}</div><div style="margin-top:4px;font-size:20px;font-weight:700;">登录验证码</div></div><div style="padding:28px;line-height:1.8;font-size:14px;"><p style="margin:0 0 16px;">您好，${userName}：</p><p style="margin:0 0 16px;">您正在登录 ${systemName}，本次登录验证码为：</p><div style="margin:20px 0;padding:20px 24px;background:#f8fafc;border:1px solid #e2e8f0;border-radius:8px;text-align:center;"><div style="font-size:13px;color:#64748b;">验证码</div><div style="margin-top:6px;font-size:32px;letter-spacing:4px;font-weight:700;color:#0f172a;">${verifyCode}</div></div><p style="margin:0 0 12px;">验证码有效期为 ${expireMinutes} 分钟，请勿将验证码泄露给他人。</p><p style="margin:0;color:#b45309;">如果本次操作不是您本人发起，请立即联系系统管理员。</p></div><div style="padding:16px 28px;background:#f8fafc;color:#64748b;font-size:12px;">此邮件由 ${systemName} 自动发送，请勿直接回复。</div></div></div>'
    WHEN 'MERCHANT_LOGIN_OTP' THEN '<div style="margin:0;padding:32px;background:#f4f7fb;font-family:Arial,sans-serif;color:#1f2937;"><div style="max-width:640px;margin:0 auto;background:#ffffff;border:1px solid #e5e7eb;border-radius:10px;overflow:hidden;"><div style="padding:22px 28px;background:#0f172a;color:#ffffff;"><div style="font-size:13px;opacity:.78;">${systemName}</div><div style="margin-top:4px;font-size:20px;font-weight:700;">登录验证码</div></div><div style="padding:28px;line-height:1.8;font-size:14px;"><p style="margin:0 0 16px;">您好，${userName}：</p><p style="margin:0 0 16px;">您正在登录 ${systemName}，本次登录验证码为：</p><div style="margin:20px 0;padding:20px 24px;background:#f8fafc;border:1px solid #e2e8f0;border-radius:8px;text-align:center;"><div style="font-size:13px;color:#64748b;">验证码</div><div style="margin-top:6px;font-size:32px;letter-spacing:4px;font-weight:700;color:#0f172a;">${verifyCode}</div></div><p style="margin:0 0 12px;">验证码有效期为 ${expireMinutes} 分钟，请勿将验证码泄露给他人。</p><p style="margin:0;color:#b45309;">如非本人操作，请及时修改密码或联系平台客服。</p></div><div style="padding:16px 28px;background:#f8fafc;color:#64748b;font-size:12px;">此邮件由 ${systemName} 自动发送，请勿直接回复。</div></div></div>'
    WHEN 'ADMIN_PASSWORD_RESET' THEN '<div style="margin:0;padding:32px;background:#f4f7fb;font-family:Arial,sans-serif;color:#1f2937;"><div style="max-width:640px;margin:0 auto;background:#ffffff;border:1px solid #e5e7eb;border-radius:10px;overflow:hidden;"><div style="padding:22px 28px;background:#0f172a;color:#ffffff;"><div style="font-size:13px;opacity:.78;">${systemName}</div><div style="margin-top:4px;font-size:20px;font-weight:700;">找回密码验证</div></div><div style="padding:28px;line-height:1.8;font-size:14px;"><p style="margin:0 0 16px;">您好，${userName}：</p><p style="margin:0 0 16px;">您正在进行 ${systemName} 找回密码操作。请使用以下验证码完成身份验证：</p><div style="margin:20px 0;padding:20px 24px;background:#f8fafc;border:1px solid #e2e8f0;border-radius:8px;text-align:center;"><div style="font-size:13px;color:#64748b;">找回密码验证码</div><div style="margin-top:6px;font-size:32px;letter-spacing:4px;font-weight:700;color:#0f172a;">${verifyCode}</div></div><p style="margin:0 0 12px;">验证码有效期为 ${expireMinutes} 分钟，请在有效期内完成密码重置。</p><p style="margin:0;color:#b45309;">如果不是您本人操作，请忽略本邮件并及时联系系统管理员。</p></div><div style="padding:16px 28px;background:#f8fafc;color:#64748b;font-size:12px;">此邮件由 ${systemName} 自动发送，请勿直接回复。</div></div></div>'
    WHEN 'MERCHANT_PASSWORD_RESET' THEN '<div style="margin:0;padding:32px;background:#f4f7fb;font-family:Arial,sans-serif;color:#1f2937;"><div style="max-width:640px;margin:0 auto;background:#ffffff;border:1px solid #e5e7eb;border-radius:10px;overflow:hidden;"><div style="padding:22px 28px;background:#0f172a;color:#ffffff;"><div style="font-size:13px;opacity:.78;">${systemName}</div><div style="margin-top:4px;font-size:20px;font-weight:700;">找回密码验证</div></div><div style="padding:28px;line-height:1.8;font-size:14px;"><p style="margin:0 0 16px;">您好，${userName}：</p><p style="margin:0 0 16px;">您正在进行 ${systemName} 找回密码操作。请使用以下验证码完成身份验证：</p><div style="margin:20px 0;padding:20px 24px;background:#f8fafc;border:1px solid #e2e8f0;border-radius:8px;text-align:center;"><div style="font-size:13px;color:#64748b;">找回密码验证码</div><div style="margin-top:6px;font-size:32px;letter-spacing:4px;font-weight:700;color:#0f172a;">${verifyCode}</div></div><p style="margin:0 0 12px;">验证码有效期为 ${expireMinutes} 分钟，请在有效期内完成密码重置。</p><p style="margin:0;color:#b45309;">如果不是您本人操作，请忽略本邮件。</p></div><div style="padding:16px 28px;background:#f8fafc;color:#64748b;font-size:12px;">此邮件由 ${systemName} 自动发送，请勿直接回复。</div></div></div>'
    WHEN 'ADMIN_ACCOUNT_CREATED' THEN '<div style="margin:0;padding:32px;background:#f4f7fb;font-family:Arial,sans-serif;color:#1f2937;"><div style="max-width:640px;margin:0 auto;background:#ffffff;border:1px solid #e5e7eb;border-radius:10px;overflow:hidden;"><div style="padding:22px 28px;background:#0f172a;color:#ffffff;"><div style="font-size:13px;opacity:.78;">${systemName}</div><div style="margin-top:4px;font-size:20px;font-weight:700;">账号创建通知</div></div><div style="padding:28px;line-height:1.8;font-size:14px;"><p style="margin:0 0 16px;">您好，${userName}：</p><p style="margin:0 0 16px;">您的 ${systemName} 账号已创建成功。</p><div style="margin:20px 0;padding:18px 20px;background:#f8fafc;border:1px solid #e2e8f0;border-radius:8px;"><p style="margin:0 0 8px;">登录账号：<strong>${loginAccount}</strong></p><p style="margin:0;">登录地址：<a href="${loginUrl}" style="color:#2563eb;text-decoration:none;">${loginUrl}</a></p></div><p style="margin:0 0 12px;">如系统生成了初始密码，请在首次登录后立即修改密码。</p><p style="margin:0;color:#b45309;">如果您未申请该账号，请联系系统管理员。</p></div><div style="padding:16px 28px;background:#f8fafc;color:#64748b;font-size:12px;">此邮件由 ${systemName} 自动发送，请勿直接回复。</div></div></div>'
    WHEN 'MERCHANT_ACCOUNT_CREATED' THEN '<div style="margin:0;padding:32px;background:#f4f7fb;font-family:Arial,sans-serif;color:#1f2937;"><div style="max-width:640px;margin:0 auto;background:#ffffff;border:1px solid #e5e7eb;border-radius:10px;overflow:hidden;"><div style="padding:22px 28px;background:#0f172a;color:#ffffff;"><div style="font-size:13px;opacity:.78;">${systemName}</div><div style="margin-top:4px;font-size:20px;font-weight:700;">账号创建通知</div></div><div style="padding:28px;line-height:1.8;font-size:14px;"><p style="margin:0 0 16px;">您好，${userName}：</p><p style="margin:0 0 16px;">您的 ${systemName} 账号已创建成功。</p><div style="margin:20px 0;padding:18px 20px;background:#f8fafc;border:1px solid #e2e8f0;border-radius:8px;"><p style="margin:0 0 8px;">商户名称：<strong>${merchantName}</strong></p><p style="margin:0 0 8px;">登录账号：<strong>${loginAccount}</strong></p><p style="margin:0;">登录地址：<a href="${loginUrl}" style="color:#2563eb;text-decoration:none;">${loginUrl}</a></p></div><p style="margin:0;">请妥善保管账号信息，并在首次登录后及时修改密码。</p></div><div style="padding:16px 28px;background:#f8fafc;color:#64748b;font-size:12px;">此邮件由 ${systemName} 自动发送，请勿直接回复。</div></div></div>'
    WHEN 'MERCHANT_ONBOARDING_APPROVED' THEN '<div style="margin:0;padding:32px;background:#f4f7fb;font-family:Arial,sans-serif;color:#1f2937;"><div style="max-width:640px;margin:0 auto;background:#ffffff;border:1px solid #e5e7eb;border-radius:10px;overflow:hidden;"><div style="padding:22px 28px;background:#065f46;color:#ffffff;"><div style="font-size:13px;opacity:.78;">${systemName}</div><div style="margin-top:4px;font-size:20px;font-weight:700;">商户开户审核通过</div></div><div style="padding:28px;line-height:1.8;font-size:14px;"><p style="margin:0 0 16px;">您好，${merchantName}：</p><p style="margin:0 0 16px;">您的商户开户申请已审核通过。</p><div style="margin:20px 0;padding:18px 20px;background:#f0fdf4;border:1px solid #bbf7d0;border-radius:8px;"><p style="margin:0 0 8px;">商户号：<strong>${merchantNo}</strong></p><p style="margin:0;">审核时间：${reviewTime}</p></div><p style="margin:0 0 12px;">您可以登录商户系统查看商户资料、配置 API 密钥并进行后续对接。</p><p style="margin:0;">登录地址：<a href="${loginUrl}" style="color:#2563eb;text-decoration:none;">${loginUrl}</a></p></div><div style="padding:16px 28px;background:#f8fafc;color:#64748b;font-size:12px;">此邮件由 ${systemName} 自动发送，请勿直接回复。</div></div></div>'
    WHEN 'MERCHANT_ONBOARDING_REJECTED' THEN '<div style="margin:0;padding:32px;background:#f4f7fb;font-family:Arial,sans-serif;color:#1f2937;"><div style="max-width:640px;margin:0 auto;background:#ffffff;border:1px solid #e5e7eb;border-radius:10px;overflow:hidden;"><div style="padding:22px 28px;background:#991b1b;color:#ffffff;"><div style="font-size:13px;opacity:.78;">${systemName}</div><div style="margin-top:4px;font-size:20px;font-weight:700;">商户开户审核未通过</div></div><div style="padding:28px;line-height:1.8;font-size:14px;"><p style="margin:0 0 16px;">您好，${merchantName}：</p><p style="margin:0 0 16px;">您的商户开户申请暂未通过审核。</p><div style="margin:20px 0;padding:18px 20px;background:#fef2f2;border:1px solid #fecaca;border-radius:8px;"><p style="margin:0 0 8px;">商户号：<strong>${merchantNo}</strong></p><p style="margin:0 0 8px;">审核时间：${reviewTime}</p><p style="margin:0;">未通过原因：${rejectReason}</p></div><p style="margin:0;">请根据提示补充或修改资料后重新提交。</p></div><div style="padding:16px 28px;background:#f8fafc;color:#64748b;font-size:12px;">此邮件由 ${systemName} 自动发送，请勿直接回复。</div></div></div>'
    WHEN 'API_KEY_CREATED' THEN '<div style="margin:0;padding:32px;background:#f4f7fb;font-family:Arial,sans-serif;color:#1f2937;"><div style="max-width:640px;margin:0 auto;background:#ffffff;border:1px solid #e5e7eb;border-radius:10px;overflow:hidden;"><div style="padding:22px 28px;background:#0f172a;color:#ffffff;"><div style="font-size:13px;opacity:.78;">${systemName}</div><div style="margin-top:4px;font-size:20px;font-weight:700;">API 密钥生成通知</div></div><div style="padding:28px;line-height:1.8;font-size:14px;"><p style="margin:0 0 16px;">您好，${merchantName}：</p><p style="margin:0 0 16px;">您的商户 API 密钥已生成。</p><div style="margin:20px 0;padding:18px 20px;background:#f8fafc;border:1px solid #e2e8f0;border-radius:8px;"><p style="margin:0 0 8px;">商户号：<strong>${merchantNo}</strong></p><p style="margin:0 0 8px;">密钥名称：${keyName}</p><p style="margin:0 0 8px;">密钥尾号：<strong>${keyLast4}</strong></p><p style="margin:0 0 8px;">操作人：${operatorName}</p><p style="margin:0;">操作时间：${operationTime}</p></div><p style="margin:0;color:#b45309;">为保障账户安全，邮件中不会展示完整密钥内容。请登录商户系统查看或下载相关密钥信息。</p></div><div style="padding:16px 28px;background:#f8fafc;color:#64748b;font-size:12px;">此邮件由 ${systemName} 自动发送，请勿直接回复。</div></div></div>'
    WHEN 'API_KEY_RESET' THEN '<div style="margin:0;padding:32px;background:#f4f7fb;font-family:Arial,sans-serif;color:#1f2937;"><div style="max-width:640px;margin:0 auto;background:#ffffff;border:1px solid #e5e7eb;border-radius:10px;overflow:hidden;"><div style="padding:22px 28px;background:#7c2d12;color:#ffffff;"><div style="font-size:13px;opacity:.78;">${systemName}</div><div style="margin-top:4px;font-size:20px;font-weight:700;">API 密钥重置通知</div></div><div style="padding:28px;line-height:1.8;font-size:14px;"><p style="margin:0 0 16px;">您好，${merchantName}：</p><p style="margin:0 0 16px;">您的商户 API 密钥已被重置。</p><div style="margin:20px 0;padding:18px 20px;background:#fff7ed;border:1px solid #fed7aa;border-radius:8px;"><p style="margin:0 0 8px;">商户号：<strong>${merchantNo}</strong></p><p style="margin:0 0 8px;">密钥名称：${keyName}</p><p style="margin:0 0 8px;">新密钥尾号：<strong>${keyLast4}</strong></p><p style="margin:0 0 8px;">操作人：${operatorName}</p><p style="margin:0;">操作时间：${operationTime}</p></div><p style="margin:0;color:#b45309;">请确认该操作是否由授权人员发起。如非本人或授权人员操作，请立即联系平台客服。</p></div><div style="padding:16px 28px;background:#f8fafc;color:#64748b;font-size:12px;">此邮件由 ${systemName} 自动发送，请勿直接回复。</div></div></div>'
    WHEN 'API_KEY_ENABLED' THEN '<div style="margin:0;padding:32px;background:#f4f7fb;font-family:Arial,sans-serif;color:#1f2937;"><div style="max-width:640px;margin:0 auto;background:#ffffff;border:1px solid #e5e7eb;border-radius:10px;overflow:hidden;"><div style="padding:22px 28px;background:#065f46;color:#ffffff;"><div style="font-size:13px;opacity:.78;">${systemName}</div><div style="margin-top:4px;font-size:20px;font-weight:700;">API 密钥启用通知</div></div><div style="padding:28px;line-height:1.8;font-size:14px;"><p style="margin:0 0 16px;">您好，${merchantName}：</p><p style="margin:0 0 16px;">您的商户 API 密钥已启用。</p><div style="margin:20px 0;padding:18px 20px;background:#f0fdf4;border:1px solid #bbf7d0;border-radius:8px;"><p style="margin:0 0 8px;">商户号：<strong>${merchantNo}</strong></p><p style="margin:0 0 8px;">密钥名称：${keyName}</p><p style="margin:0 0 8px;">密钥尾号：<strong>${keyLast4}</strong></p><p style="margin:0 0 8px;">操作人：${operatorName}</p><p style="margin:0;">操作时间：${operationTime}</p></div></div><div style="padding:16px 28px;background:#f8fafc;color:#64748b;font-size:12px;">此邮件由 ${systemName} 自动发送，请勿直接回复。</div></div></div>'
    WHEN 'API_KEY_DISABLED' THEN '<div style="margin:0;padding:32px;background:#f4f7fb;font-family:Arial,sans-serif;color:#1f2937;"><div style="max-width:640px;margin:0 auto;background:#ffffff;border:1px solid #e5e7eb;border-radius:10px;overflow:hidden;"><div style="padding:22px 28px;background:#991b1b;color:#ffffff;"><div style="font-size:13px;opacity:.78;">${systemName}</div><div style="margin-top:4px;font-size:20px;font-weight:700;">API 密钥停用通知</div></div><div style="padding:28px;line-height:1.8;font-size:14px;"><p style="margin:0 0 16px;">您好，${merchantName}：</p><p style="margin:0 0 16px;">您的商户 API 密钥已停用。</p><div style="margin:20px 0;padding:18px 20px;background:#fef2f2;border:1px solid #fecaca;border-radius:8px;"><p style="margin:0 0 8px;">商户号：<strong>${merchantNo}</strong></p><p style="margin:0 0 8px;">密钥名称：${keyName}</p><p style="margin:0 0 8px;">密钥尾号：<strong>${keyLast4}</strong></p><p style="margin:0 0 8px;">操作人：${operatorName}</p><p style="margin:0;">操作时间：${operationTime}</p></div><p style="margin:0;color:#b45309;">如果该操作不是您或授权人员发起，请及时联系平台客服。</p></div><div style="padding:16px 28px;background:#f8fafc;color:#64748b;font-size:12px;">此邮件由 ${systemName} 自动发送，请勿直接回复。</div></div></div>'
    ELSE content_template
END,
update_by = 'system',
update_time = CURRENT_TIMESTAMP(3)
WHERE system_builtin = 1
  AND locale = 'zh-CN'
  AND template_code IN (
      'ADMIN_LOGIN_OTP',
      'MERCHANT_LOGIN_OTP',
      'ADMIN_PASSWORD_RESET',
      'MERCHANT_PASSWORD_RESET',
      'ADMIN_ACCOUNT_CREATED',
      'MERCHANT_ACCOUNT_CREATED',
      'MERCHANT_ONBOARDING_APPROVED',
      'MERCHANT_ONBOARDING_REJECTED',
      'API_KEY_CREATED',
      'API_KEY_RESET',
      'API_KEY_ENABLED',
      'API_KEY_DISABLED'
  )
  AND deleted = 0;

INSERT IGNORE INTO sys_menu (id, app_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path, permission_code, icon, visible, sort_no, status, deleted)
VALUES
(660, 1, 0, 'admin_email_catalog_v1', '邮件管理', 'CATALOG', '/email', NULL, 'email', 'Message', 1, 50, 1, 0),
(661, 1, 660, 'admin_email_account_v1', '发件账户配置', 'MENU', '/email/account', 'email/account', 'email:account:list', 'Message', 1, 51, 1, 0),
(662, 1, 660, 'admin_email_template_v1', '邮件模板管理', 'MENU', '/email/template', 'email/template', 'email:template:list', 'Tickets', 1, 52, 1, 0),
(663, 1, 660, 'admin_email_record_v1', '邮件发送记录', 'MENU', '/email/record', 'email/record', 'email:record:list', 'DocumentChecked', 1, 53, 1, 0);

INSERT IGNORE INTO sys_permission (id, app_id, menu_id, permission_code, permission_name, permission_type, resource_method, resource_path, status, deleted)
VALUES
(1010, 1, 660, 'email', '邮件管理目录', 'MENU', 'GET', '/email/**', 1, 0),
(1011, 1, 661, 'email:account:list', '发件账户查询', 'MENU', 'POST', '/admin/email/accounts/search', 1, 0),
(1012, 1, 661, 'email:account:detail', '发件账户详情', 'BUTTON', 'GET', '/admin/email/accounts/*', 1, 0),
(1013, 1, 661, 'email:account:add', '发件账户新增', 'BUTTON', 'POST', '/admin/email/accounts', 1, 0),
(1014, 1, 661, 'email:account:edit', '发件账户修改', 'BUTTON', 'PUT', '/admin/email/accounts/*', 1, 0),
(1015, 1, 661, 'email:account:remove', '发件账户删除', 'BUTTON', 'DELETE', '/admin/email/accounts/*', 1, 0),
(1016, 1, 661, 'email:account:status', '发件账户状态', 'BUTTON', 'PUT', '/admin/email/accounts/*/status', 1, 0),
(1017, 1, 661, 'email:account:default', '发件账户默认', 'BUTTON', 'PUT', '/admin/email/accounts/*/default', 1, 0),
(1018, 1, 661, 'email:account:test', '发件账户测试发送', 'BUTTON', 'POST', '/admin/email/accounts/*/test', 1, 0),
(1020, 1, 662, 'email:template:list', '邮件模板查询', 'MENU', 'POST', '/admin/email/templates/search', 1, 0),
(1021, 1, 662, 'email:template:detail', '邮件模板详情', 'BUTTON', 'GET', '/admin/email/templates/*', 1, 0),
(1022, 1, 662, 'email:template:add', '邮件模板新增', 'BUTTON', 'POST', '/admin/email/templates', 1, 0),
(1023, 1, 662, 'email:template:edit', '邮件模板修改', 'BUTTON', 'PUT', '/admin/email/templates/*', 1, 0),
(1024, 1, 662, 'email:template:remove', '邮件模板删除', 'BUTTON', 'DELETE', '/admin/email/templates/*', 1, 0),
(1025, 1, 662, 'email:template:status', '邮件模板状态', 'BUTTON', 'PUT', '/admin/email/templates/*/status', 1, 0),
(1026, 1, 662, 'email:template:copy', '邮件模板复制', 'BUTTON', 'POST', '/admin/email/templates/*/copy', 1, 0),
(1027, 1, 662, 'email:template:preview', '邮件模板预览', 'BUTTON', 'POST', '/admin/email/templates/preview', 1, 0),
(1030, 1, 663, 'email:record:list', '邮件发送记录查询', 'MENU', 'POST', '/admin/email/records/search', 1, 0),
(1031, 1, 663, 'email:record:detail', '邮件发送记录详情', 'BUTTON', 'GET', '/admin/email/records/*', 1, 0),
(1032, 1, 663, 'email:record:resend', '邮件重新发送', 'BUTTON', 'POST', '/admin/email/records/*/resend', 1, 0);

INSERT INTO sys_menu (app_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path, permission_code, icon, visible, sort_no, status, deleted)
SELECT 1, parent.id, button.menu_code, button.menu_name, 'BUTTON', NULL, NULL, button.permission_code, NULL, 0, button.sort_no, 1, 0
FROM sys_menu parent
JOIN (
    SELECT 'admin_email_account_v1' parent_code, 'admin_email_account_detail_v1' menu_code, '发件账户详情' menu_name, 'email:account:detail' permission_code, 1 sort_no
    UNION ALL SELECT 'admin_email_account_v1', 'admin_email_account_add_v1', '发件账户新增', 'email:account:add', 2
    UNION ALL SELECT 'admin_email_account_v1', 'admin_email_account_edit_v1', '发件账户修改', 'email:account:edit', 3
    UNION ALL SELECT 'admin_email_account_v1', 'admin_email_account_remove_v1', '发件账户删除', 'email:account:remove', 4
    UNION ALL SELECT 'admin_email_account_v1', 'admin_email_account_status_v1', '发件账户状态', 'email:account:status', 5
    UNION ALL SELECT 'admin_email_account_v1', 'admin_email_account_default_v1', '发件账户默认', 'email:account:default', 6
    UNION ALL SELECT 'admin_email_account_v1', 'admin_email_account_test_v1', '发件账户测试发送', 'email:account:test', 7
    UNION ALL SELECT 'admin_email_template_v1', 'admin_email_template_detail_v1', '邮件模板详情', 'email:template:detail', 1
    UNION ALL SELECT 'admin_email_template_v1', 'admin_email_template_add_v1', '邮件模板新增', 'email:template:add', 2
    UNION ALL SELECT 'admin_email_template_v1', 'admin_email_template_edit_v1', '邮件模板修改', 'email:template:edit', 3
    UNION ALL SELECT 'admin_email_template_v1', 'admin_email_template_remove_v1', '邮件模板删除', 'email:template:remove', 4
    UNION ALL SELECT 'admin_email_template_v1', 'admin_email_template_status_v1', '邮件模板状态', 'email:template:status', 5
    UNION ALL SELECT 'admin_email_template_v1', 'admin_email_template_copy_v1', '邮件模板复制', 'email:template:copy', 6
    UNION ALL SELECT 'admin_email_template_v1', 'admin_email_template_preview_v1', '邮件模板预览', 'email:template:preview', 7
    UNION ALL SELECT 'admin_email_record_v1', 'admin_email_record_detail_v1', '邮件发送记录详情', 'email:record:detail', 1
    UNION ALL SELECT 'admin_email_record_v1', 'admin_email_record_resend_v1', '邮件重新发送', 'email:record:resend', 2
) button ON button.parent_code = parent.menu_code
WHERE parent.app_id = 1
  AND parent.deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM sys_menu exists_menu
      WHERE exists_menu.app_id = 1
        AND exists_menu.menu_code = button.menu_code
        AND exists_menu.deleted = 0
  );

INSERT IGNORE INTO sys_role_menu (app_id, role_id, menu_id, deleted)
SELECT 1, 1, id, 0
FROM sys_menu
WHERE app_id = 1
  AND deleted = 0
  AND (id BETWEEN 660 AND 663 OR menu_code LIKE 'admin_email_%_v1');

INSERT IGNORE INTO sys_role_permission (app_id, role_id, permission_id, deleted)
SELECT 1, 1, id, 0
FROM sys_permission
WHERE app_id = 1
  AND deleted = 0
  AND (permission_code = 'email' OR permission_code LIKE 'email:%');

-- =============================================================================
-- 管理端菜单最终校准
-- 保留备份库中的正式菜单树，同时保留后续新增的渠道、汇率、邮件、分表功能。
-- =============================================================================
UPDATE sys_menu
SET visible = 0,
    status = 0,
    updated_at = CURRENT_TIMESTAMP(3)
WHERE app_id = 1
  AND deleted = 0
  AND menu_code IN (
      'home', 'system', 'permission', 'monitor',
      'admin_dashboard',
      'admin_system', 'admin_config', 'admin_dict', 'admin_oper_log', 'admin_menu', 'admin_dept', 'admin_post',
      'admin_login_log', 'admin_merchant_catalog', 'admin_merchant_user', 'admin_merchant_audit',
      'admin_merchant_key', 'admin_payment_catalog', 'admin_payment_order', 'admin_refund_order',
      'admin_payout_order', 'admin_settlement', 'admin_channel', 'admin_risk_catalog',
      'admin_risk_rule', 'admin_risk_blacklist',
      'admin_system_center', 'admin_system_user', 'admin_system_role', 'admin_system_menu',
      'admin_system_department', 'admin_system_post', 'admin_system_dict', 'admin_system_config',
      'admin_system_login_log', 'admin_system_oper_log', 'admin_merchant_center', 'admin_merchant_info_v2',
      'admin_merchant_user_v2', 'admin_merchant_role_v2', 'admin_merchant_jwt_key',
      'admin_merchant_response_key', 'admin_platform_payload_key', 'admin_base_center',
      'admin_base_country', 'admin_base_currency', 'admin_base_region_currency',
      'admin_permission_center', 'admin_permission_app', 'admin_permission_resource',
      'admin_permission_data_scope', 'admin_permission_role_grant',
      'admin_system_user_v3', 'admin_system_role_v3', 'admin_system_menu_v3', 'admin_system_org_v3',
      'admin_system_config_center_v3', 'admin_system_log_v3',
      'admin_merchant_catalog_v3', 'admin_merchant_info_v3',
      'admin_base_catalog_v3', 'admin_base_country_v3', 'admin_base_currency_v3',
      'admin_base_region_currency_v3'
  );

INSERT INTO sys_menu (app_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path, permission_code, icon, visible, sort_no, status, deleted)
SELECT 1, 0, item.menu_code, item.menu_name, item.menu_type, item.route_path, item.component_path, item.permission_code, item.icon, item.visible, item.sort_no, item.status, 0
FROM (
    SELECT 'admin_home_catalog_v3' menu_code, '首页' menu_name, 'CATALOG' menu_type, '/' route_path, NULL component_path, NULL permission_code, 'House' icon, 1 visible, 1 sort_no, 1 status
    UNION ALL SELECT 'system_manage', '系统管理', 'CATALOG', '/system', NULL, NULL, 'Setting', 1, 10, 1
    UNION ALL SELECT 'merchant_manage', '商户管理', 'CATALOG', '/merchant', NULL, NULL, 'Shop', 1, 20, 1
    UNION ALL SELECT 'base', '基础数据', 'CATALOG', '/base', NULL, NULL, 'DataLine', 1, 30, 1
    UNION ALL SELECT 'admin_channel_catalog_v1', '渠道管理', 'CATALOG', '/channel', NULL, 'channel', 'OfficeBuilding', 1, 40, 1
    UNION ALL SELECT 'exchange', '汇率管理', 'CATALOG', '/exchange', NULL, NULL, 'Money', 1, 50, 1
    UNION ALL SELECT 'admin_email_catalog_v1', '邮件管理', 'CATALOG', '/email', NULL, 'email', 'Message', 1, 60, 1
    UNION ALL SELECT 'system_monitor', '系统监控', 'CATALOG', '/monitor', NULL, NULL, 'Monitor', 1, 80, 1
    UNION ALL SELECT 'monitor_sharding', '分表管理', 'CATALOG', '/monitor/sharding', 'monitor/sharding/index', NULL, 'Coin', 1, 85, 1
) item
WHERE NOT EXISTS (
    SELECT 1
    FROM sys_menu exists_menu
    WHERE exists_menu.app_id = 1
      AND exists_menu.menu_code = item.menu_code
      AND exists_menu.deleted = 0
);

UPDATE sys_menu menu
JOIN (
    SELECT 'admin_home_catalog_v3' menu_code, 0 parent_id, '首页' menu_name, 'CATALOG' menu_type, '/' route_path, NULL component_path, NULL permission_code, 'House' icon, 1 visible, 1 sort_no, 1 status
    UNION ALL SELECT 'system_manage', 0, '系统管理', 'CATALOG', '/system', NULL, NULL, 'Setting', 1, 10, 1
    UNION ALL SELECT 'merchant_manage', 0, '商户管理', 'CATALOG', '/merchant', NULL, NULL, 'Shop', 1, 20, 1
    UNION ALL SELECT 'base', 0, '基础数据', 'CATALOG', '/base', NULL, NULL, 'DataLine', 1, 30, 1
    UNION ALL SELECT 'admin_channel_catalog_v1', 0, '渠道管理', 'CATALOG', '/channel', NULL, 'channel', 'OfficeBuilding', 1, 40, 1
    UNION ALL SELECT 'exchange', 0, '汇率管理', 'CATALOG', '/exchange', NULL, NULL, 'Money', 1, 50, 1
    UNION ALL SELECT 'admin_email_catalog_v1', 0, '邮件管理', 'CATALOG', '/email', NULL, 'email', 'Message', 1, 60, 1
    UNION ALL SELECT 'system_monitor', 0, '系统监控', 'CATALOG', '/monitor', NULL, NULL, 'Monitor', 1, 80, 1
    UNION ALL SELECT 'monitor_sharding', 0, '分表管理', 'CATALOG', '/monitor/sharding', 'monitor/sharding/index', NULL, 'Coin', 1, 85, 1
) item ON item.menu_code = menu.menu_code
SET menu.parent_id = item.parent_id,
    menu.menu_name = item.menu_name,
    menu.menu_type = item.menu_type,
    menu.route_path = item.route_path,
    menu.component_path = item.component_path,
    menu.permission_code = item.permission_code,
    menu.icon = item.icon,
    menu.visible = item.visible,
    menu.sort_no = item.sort_no,
    menu.status = item.status,
    menu.updated_at = CURRENT_TIMESTAMP(3)
WHERE menu.app_id = 1
  AND menu.deleted = 0;

INSERT INTO sys_menu (app_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path, permission_code, icon, visible, sort_no, status, deleted)
SELECT 1, parent.id, item.menu_code, item.menu_name, item.menu_type, item.route_path, item.component_path, item.permission_code, item.icon, item.visible, item.sort_no, item.status, 0
FROM (
    SELECT 'system_manage' parent_code, 'system_user' menu_code, '用户管理' menu_name, 'MENU' menu_type, '/system/user' route_path, 'system/user/index' component_path, 'system:user:list' permission_code, 'User' icon, 1 visible, 11 sort_no, 1 status
    UNION ALL SELECT 'system_manage', 'system_role', '角色管理', 'MENU', '/system/role', 'system/role/index', 'system:role:list', 'UserFilled', 1, 12, 1
    UNION ALL SELECT 'system_manage', 'system_menu', '菜单管理', 'MENU', '/system/menu', 'system/menu/index', 'system:menu:list', 'Menu', 1, 13, 1
    UNION ALL SELECT 'system_manage', 'system_dept', '部门管理', 'MENU', '/system/dept', 'system/dept/index', 'system:dept:list', 'OfficeBuilding', 1, 14, 1
    UNION ALL SELECT 'system_manage', 'system_post', '岗位管理', 'MENU', '/system/post', 'system/post/index', 'system:post:list', 'Postcard', 1, 15, 1
    UNION ALL SELECT 'system_manage', 'system_dict', '字典管理', 'MENU', '/system/dict', 'system/dict/index', 'system:dict:list', 'Tickets', 1, 16, 1
    UNION ALL SELECT 'system_manage', 'system_config', '参数设置', 'MENU', '/system/config', 'system/config/index', 'system:config:list', 'Setting', 1, 17, 1
    UNION ALL SELECT 'system_manage', 'system_notice', '通知公告', 'MENU', '/system/notice', 'system/notice/index', 'system:notice:list', 'Bell', 1, 18, 1
    UNION ALL SELECT 'system_manage', 'system_log', '日志管理', 'MENU', '/system/log', 'system/log/index', 'system:login-log:list', 'DocumentChecked', 1, 19, 1
    UNION ALL SELECT 'merchant_manage', 'admin_merchant_menu_manage_v1', '商户系统菜单管理', 'MENU', '/merchant/menu-manage', 'merchant/menu-manage', 'merchant:menu-manage:list', 'Menu', 1, 21, 1
    UNION ALL SELECT 'merchant_manage', 'admin_merchant_menu_grant_v3', '商户菜单授权', 'MENU', '/merchant/menu-grant', 'merchant/menu-grant', 'merchant:menu-grant:list', 'MagicStick', 1, 22, 1
    UNION ALL SELECT 'merchant_manage', 'admin_merchant_user_query_v1', '商户用户查询', 'MENU', '/merchant/user-query', 'merchant/user-query', 'admin:merchant:user:list', 'User', 1, 23, 1
    UNION ALL SELECT 'merchant_manage', 'merchant_info_manage', '商户信息管理', 'MENU', '/merchant/info', 'merchant/info/index', 'merchant:info:list', 'Shop', 1, 41, 1
    UNION ALL SELECT 'base', 'base_country', '国家/地区', 'MENU', '/base/country', 'base/country', 'base:country:list', 'Location', 1, 31, 1
    UNION ALL SELECT 'base', 'base_currency', '币种管理', 'MENU', '/base/currency', 'base/currency', 'base:currency:list', 'Coin', 1, 32, 1
    UNION ALL SELECT 'base', 'base_region_currency', '地区币种配置', 'MENU', '/base/region-currency', 'base/region-currency', 'base:countryCurrency:list', 'Connection', 1, 33, 1
    UNION ALL SELECT 'base', 'base_mcc', 'MCC 管理', 'MENU', '/base/mcc', 'base/mcc', 'base:mcc:view', 'DataLine', 1, 34, 1
    UNION ALL SELECT 'system_monitor', 'monitor_online', '在线用户', 'MENU', '/monitor/online', 'monitor/online/index', 'system:online:list', 'User', 1, 21, 1
    UNION ALL SELECT 'system_monitor', 'monitor_server', '服务监控', 'MENU', '/monitor/server', 'monitor/server/index', 'system:server:list', 'Cpu', 1, 22, 1
    UNION ALL SELECT 'system_monitor', 'monitor_cache', '缓存监控', 'MENU', '/monitor/cache', 'monitor/cache/index', 'system:cache:list', 'Coin', 1, 23, 1
    UNION ALL SELECT 'system_monitor', 'monitor_job', '任务调度', 'MENU', '/monitor/job', 'monitor/job/index', 'monitor:job:list', 'Clock', 1, 84, 1
    UNION ALL SELECT 'system_monitor', 'monitor_job_log', '任务日志', 'MENU', '/monitor/job-log', 'monitor/job-log/index', 'monitor:jobLog:list', 'Document', 1, 85, 1
    UNION ALL SELECT 'system_monitor', 'monitor_job_node', '执行节点', 'MENU', '/monitor/job-node', 'monitor/job-node/index', 'monitor:jobNode:list', 'Connection', 1, 86, 1
    UNION ALL SELECT 'system_monitor', 'monitor_datasource', '数据源监控', 'MENU', '/monitor/datasource', 'monitor/datasource/index', 'monitor:datasource:view', 'DataLine', 1, 87, 1
    UNION ALL SELECT 'system_monitor', 'monitor_rocketmq', 'RocketMQ 控制台', 'LINK', 'http://localhost:8088', NULL, 'monitor:rocketmq:view', 'Connection', 1, 88, 1
    UNION ALL SELECT 'system_monitor', 'monitor_nacos', 'Nacos 控制台', 'LINK', 'http://localhost:8848/nacos', NULL, 'monitor:nacos:view', 'Monitor', 1, 89, 1
    UNION ALL SELECT 'monitor_sharding', 'monitor_sharding_rule', '分表规则', 'MENU', '/monitor/sharding/rules', 'monitor/sharding/rules/index', 'monitor:sharding:rule:list', 'List', 1, 91, 1
    UNION ALL SELECT 'monitor_sharding', 'monitor_sharding_physical', '物理表清单', 'MENU', '/monitor/sharding/physical-tables', 'monitor/sharding/physical-tables/index', 'monitor:sharding:physical:list', 'Grid', 1, 92, 1
    UNION ALL SELECT 'monitor_sharding', 'monitor_sharding_task_log', '建表任务日志', 'MENU', '/monitor/sharding/table-create-logs', 'monitor/sharding/table-create-logs/index', 'monitor:sharding:task:list', 'Document', 1, 93, 1
    UNION ALL SELECT 'monitor_sharding', 'monitor_sharding_id_rule', 'ID规则说明', 'MENU', '/monitor/sharding/id-rule', 'monitor/sharding/id-rule/index', 'monitor:sharding:idRule:query', 'Key', 1, 94, 1
    UNION ALL SELECT 'admin_channel_catalog_v1', 'admin_channel_info_v1', '渠道信息管理', 'MENU', '/channel/info', 'channel/info', 'channel:info:list', 'Connection', 1, 41, 1
    UNION ALL SELECT 'admin_channel_catalog_v1', 'admin_channel_capability_v1', '渠道支付能力管理', 'MENU', '/channel/capability', 'channel/capability', 'channel:capability:list', 'CreditCard', 1, 42, 1
    UNION ALL SELECT 'admin_channel_catalog_v1', 'admin_channel_limit_v1', '渠道限额管理', 'MENU', '/channel/limit', 'channel/limit', 'channel:limit:list', 'Money', 1, 43, 1
    UNION ALL SELECT 'admin_email_catalog_v1', 'admin_email_account_v1', '发件账户配置', 'MENU', '/email/account', 'email/account', 'email:account:list', 'Message', 1, 51, 1
    UNION ALL SELECT 'admin_email_catalog_v1', 'admin_email_template_v1', '邮件模板管理', 'MENU', '/email/template', 'email/template', 'email:template:list', 'Tickets', 1, 52, 1
    UNION ALL SELECT 'admin_email_catalog_v1', 'admin_email_record_v1', '邮件发送记录', 'MENU', '/email/record', 'email/record', 'email:record:list', 'DocumentChecked', 1, 53, 1
) item
JOIN sys_menu parent ON parent.app_id = 1 AND parent.menu_code = item.parent_code AND parent.deleted = 0
WHERE NOT EXISTS (
    SELECT 1
    FROM sys_menu exists_menu
    WHERE exists_menu.app_id = 1
      AND exists_menu.menu_code = item.menu_code
      AND exists_menu.deleted = 0
);

UPDATE sys_menu menu
JOIN (
    SELECT 'system_manage' parent_code, 'system_user' menu_code, '用户管理' menu_name, 'MENU' menu_type, '/system/user' route_path, 'system/user/index' component_path, 'system:user:list' permission_code, 'User' icon, 1 visible, 11 sort_no, 1 status
    UNION ALL SELECT 'system_manage', 'system_role', '角色管理', 'MENU', '/system/role', 'system/role/index', 'system:role:list', 'UserFilled', 1, 12, 1
    UNION ALL SELECT 'system_manage', 'system_menu', '菜单管理', 'MENU', '/system/menu', 'system/menu/index', 'system:menu:list', 'Menu', 1, 13, 1
    UNION ALL SELECT 'system_manage', 'system_dept', '部门管理', 'MENU', '/system/dept', 'system/dept/index', 'system:dept:list', 'OfficeBuilding', 1, 14, 1
    UNION ALL SELECT 'system_manage', 'system_post', '岗位管理', 'MENU', '/system/post', 'system/post/index', 'system:post:list', 'Postcard', 1, 15, 1
    UNION ALL SELECT 'system_manage', 'system_dict', '字典管理', 'MENU', '/system/dict', 'system/dict/index', 'system:dict:list', 'Tickets', 1, 16, 1
    UNION ALL SELECT 'system_manage', 'system_config', '参数设置', 'MENU', '/system/config', 'system/config/index', 'system:config:list', 'Setting', 1, 17, 1
    UNION ALL SELECT 'system_manage', 'system_notice', '通知公告', 'MENU', '/system/notice', 'system/notice/index', 'system:notice:list', 'Bell', 1, 18, 1
    UNION ALL SELECT 'system_manage', 'system_log', '日志管理', 'MENU', '/system/log', 'system/log/index', 'system:login-log:list', 'DocumentChecked', 1, 19, 1
    UNION ALL SELECT 'merchant_manage', 'admin_merchant_menu_manage_v1', '商户系统菜单管理', 'MENU', '/merchant/menu-manage', 'merchant/menu-manage', 'merchant:menu-manage:list', 'Menu', 1, 21, 1
    UNION ALL SELECT 'merchant_manage', 'admin_merchant_menu_grant_v3', '商户菜单授权', 'MENU', '/merchant/menu-grant', 'merchant/menu-grant', 'merchant:menu-grant:list', 'MagicStick', 1, 22, 1
    UNION ALL SELECT 'merchant_manage', 'admin_merchant_user_query_v1', '商户用户查询', 'MENU', '/merchant/user-query', 'merchant/user-query', 'admin:merchant:user:list', 'User', 1, 23, 1
    UNION ALL SELECT 'merchant_manage', 'merchant_info_manage', '商户信息管理', 'MENU', '/merchant/info', 'merchant/info/index', 'merchant:info:list', 'Shop', 1, 41, 1
    UNION ALL SELECT 'base', 'base_country', '国家/地区', 'MENU', '/base/country', 'base/country', 'base:country:list', 'Location', 1, 31, 1
    UNION ALL SELECT 'base', 'base_currency', '币种管理', 'MENU', '/base/currency', 'base/currency', 'base:currency:list', 'Coin', 1, 32, 1
    UNION ALL SELECT 'base', 'base_region_currency', '地区币种配置', 'MENU', '/base/region-currency', 'base/region-currency', 'base:countryCurrency:list', 'Connection', 1, 33, 1
    UNION ALL SELECT 'base', 'base_mcc', 'MCC 管理', 'MENU', '/base/mcc', 'base/mcc', 'base:mcc:view', 'DataLine', 1, 34, 1
    UNION ALL SELECT 'system_monitor', 'monitor_online', '在线用户', 'MENU', '/monitor/online', 'monitor/online/index', 'system:online:list', 'User', 1, 21, 1
    UNION ALL SELECT 'system_monitor', 'monitor_server', '服务监控', 'MENU', '/monitor/server', 'monitor/server/index', 'system:server:list', 'Cpu', 1, 22, 1
    UNION ALL SELECT 'system_monitor', 'monitor_cache', '缓存监控', 'MENU', '/monitor/cache', 'monitor/cache/index', 'system:cache:list', 'Coin', 1, 23, 1
    UNION ALL SELECT 'system_monitor', 'monitor_job', '任务调度', 'MENU', '/monitor/job', 'monitor/job/index', 'monitor:job:list', 'Clock', 1, 84, 1
    UNION ALL SELECT 'system_monitor', 'monitor_job_log', '任务日志', 'MENU', '/monitor/job-log', 'monitor/job-log/index', 'monitor:jobLog:list', 'Document', 1, 85, 1
    UNION ALL SELECT 'system_monitor', 'monitor_job_node', '执行节点', 'MENU', '/monitor/job-node', 'monitor/job-node/index', 'monitor:jobNode:list', 'Connection', 1, 86, 1
    UNION ALL SELECT 'system_monitor', 'monitor_datasource', '数据源监控', 'MENU', '/monitor/datasource', 'monitor/datasource/index', 'monitor:datasource:view', 'DataLine', 1, 87, 1
    UNION ALL SELECT 'system_monitor', 'monitor_rocketmq', 'RocketMQ 控制台', 'LINK', 'http://localhost:8088', NULL, 'monitor:rocketmq:view', 'Connection', 1, 88, 1
    UNION ALL SELECT 'system_monitor', 'monitor_nacos', 'Nacos 控制台', 'LINK', 'http://localhost:8848/nacos', NULL, 'monitor:nacos:view', 'Monitor', 1, 89, 1
    UNION ALL SELECT 'monitor_sharding', 'monitor_sharding_rule', '分表规则', 'MENU', '/monitor/sharding/rules', 'monitor/sharding/rules/index', 'monitor:sharding:rule:list', 'List', 1, 91, 1
    UNION ALL SELECT 'monitor_sharding', 'monitor_sharding_physical', '物理表清单', 'MENU', '/monitor/sharding/physical-tables', 'monitor/sharding/physical-tables/index', 'monitor:sharding:physical:list', 'Grid', 1, 92, 1
    UNION ALL SELECT 'monitor_sharding', 'monitor_sharding_task_log', '建表任务日志', 'MENU', '/monitor/sharding/table-create-logs', 'monitor/sharding/table-create-logs/index', 'monitor:sharding:task:list', 'Document', 1, 93, 1
    UNION ALL SELECT 'monitor_sharding', 'monitor_sharding_id_rule', 'ID规则说明', 'MENU', '/monitor/sharding/id-rule', 'monitor/sharding/id-rule/index', 'monitor:sharding:idRule:query', 'Key', 1, 94, 1
    UNION ALL SELECT 'admin_channel_catalog_v1', 'admin_channel_info_v1', '渠道信息管理', 'MENU', '/channel/info', 'channel/info', 'channel:info:list', 'Connection', 1, 41, 1
    UNION ALL SELECT 'admin_channel_catalog_v1', 'admin_channel_capability_v1', '渠道支付能力管理', 'MENU', '/channel/capability', 'channel/capability', 'channel:capability:list', 'CreditCard', 1, 42, 1
    UNION ALL SELECT 'admin_channel_catalog_v1', 'admin_channel_limit_v1', '渠道限额管理', 'MENU', '/channel/limit', 'channel/limit', 'channel:limit:list', 'Money', 1, 43, 1
    UNION ALL SELECT 'admin_email_catalog_v1', 'admin_email_account_v1', '发件账户配置', 'MENU', '/email/account', 'email/account', 'email:account:list', 'Message', 1, 51, 1
    UNION ALL SELECT 'admin_email_catalog_v1', 'admin_email_template_v1', '邮件模板管理', 'MENU', '/email/template', 'email/template', 'email:template:list', 'Tickets', 1, 52, 1
    UNION ALL SELECT 'admin_email_catalog_v1', 'admin_email_record_v1', '邮件发送记录', 'MENU', '/email/record', 'email/record', 'email:record:list', 'DocumentChecked', 1, 53, 1
) item ON item.menu_code = menu.menu_code
JOIN sys_menu parent ON parent.app_id = menu.app_id AND parent.menu_code = item.parent_code AND parent.deleted = 0
SET menu.parent_id = parent.id,
    menu.menu_name = item.menu_name,
    menu.menu_type = item.menu_type,
    menu.route_path = item.route_path,
    menu.component_path = item.component_path,
    menu.permission_code = item.permission_code,
    menu.icon = item.icon,
    menu.visible = item.visible,
    menu.sort_no = item.sort_no,
    menu.status = item.status,
    menu.updated_at = CURRENT_TIMESTAMP(3)
WHERE menu.app_id = 1
  AND menu.deleted = 0;

UPDATE sys_permission permission
JOIN (
    SELECT 'system:user' prefix, 'system_user' menu_code
    UNION ALL SELECT 'system:role', 'system_role'
    UNION ALL SELECT 'system:menu', 'system_menu'
    UNION ALL SELECT 'system:dept', 'system_dept'
    UNION ALL SELECT 'system:department', 'system_dept'
    UNION ALL SELECT 'system:org', 'system_dept'
    UNION ALL SELECT 'system:post', 'system_post'
    UNION ALL SELECT 'system:dict', 'system_dict'
    UNION ALL SELECT 'system:dictData', 'system_dict'
    UNION ALL SELECT 'system:config', 'system_config'
    UNION ALL SELECT 'system:notice', 'system_notice'
    UNION ALL SELECT 'system:login-log', 'system_log'
    UNION ALL SELECT 'system:oper-log', 'system_log'
    UNION ALL SELECT 'system:log', 'system_log'
    UNION ALL SELECT 'system:online', 'monitor_online'
    UNION ALL SELECT 'system:server', 'monitor_server'
    UNION ALL SELECT 'system:cache', 'monitor_cache'
    UNION ALL SELECT 'monitor:jobLog', 'monitor_job_log'
    UNION ALL SELECT 'monitor:jobNode', 'monitor_job_node'
    UNION ALL SELECT 'monitor:job', 'monitor_job'
    UNION ALL SELECT 'monitor:datasource', 'monitor_datasource'
    UNION ALL SELECT 'monitor:rocketmq', 'monitor_rocketmq'
    UNION ALL SELECT 'monitor:nacos', 'monitor_nacos'
    UNION ALL SELECT 'monitor:sharding:rule', 'monitor_sharding_rule'
    UNION ALL SELECT 'monitor:sharding:physical', 'monitor_sharding_physical'
    UNION ALL SELECT 'monitor:sharding:task', 'monitor_sharding_task_log'
    UNION ALL SELECT 'monitor:sharding:idRule', 'monitor_sharding_id_rule'
    UNION ALL SELECT 'merchant:info', 'merchant_info_manage'
    UNION ALL SELECT 'merchant:account', 'merchant_info_manage'
    UNION ALL SELECT 'merchant:role', 'merchant_info_manage'
    UNION ALL SELECT 'merchant:key', 'merchant_info_manage'
    UNION ALL SELECT 'merchant:response-key', 'merchant_info_manage'
    UNION ALL SELECT 'merchant:platform-payload-key', 'merchant_info_manage'
    UNION ALL SELECT 'merchant:operation-log', 'merchant_info_manage'
    UNION ALL SELECT 'merchant:menu-manage', 'admin_merchant_menu_manage_v1'
    UNION ALL SELECT 'merchant:menu-grant', 'admin_merchant_menu_grant_v3'
    UNION ALL SELECT 'admin:merchant:user', 'admin_merchant_user_query_v1'
    UNION ALL SELECT 'base:countryCurrency', 'base_region_currency'
    UNION ALL SELECT 'base:country', 'base_country'
    UNION ALL SELECT 'base:currency', 'base_currency'
    UNION ALL SELECT 'base:mcc', 'base_mcc'
    UNION ALL SELECT 'channel:info', 'admin_channel_info_v1'
    UNION ALL SELECT 'channel:capability', 'admin_channel_capability_v1'
    UNION ALL SELECT 'channel:limit', 'admin_channel_limit_v1'
    UNION ALL SELECT 'email:account', 'admin_email_account_v1'
    UNION ALL SELECT 'email:template', 'admin_email_template_v1'
    UNION ALL SELECT 'email:record', 'admin_email_record_v1'
) target ON permission.permission_code = target.prefix OR permission.permission_code LIKE CONCAT(target.prefix, ':%')
JOIN sys_menu menu ON menu.app_id = permission.app_id AND menu.menu_code = target.menu_code AND menu.deleted = 0
SET permission.menu_id = menu.id
WHERE permission.app_id = 1
  AND permission.deleted = 0;

UPDATE sys_permission permission
JOIN sys_menu menu ON menu.app_id = permission.app_id AND menu.deleted = 0
SET permission.menu_id = menu.id
WHERE permission.app_id = 1
  AND permission.deleted = 0
  AND (
      (permission.permission_code = 'channel' AND menu.menu_code = 'admin_channel_catalog_v1')
      OR (permission.permission_code = 'email' AND menu.menu_code = 'admin_email_catalog_v1')
      OR (permission.permission_code = 'dashboard:view' AND menu.menu_code = 'admin_dashboard_v3')
  );

DELETE role_permission
FROM sys_role_permission role_permission
JOIN sys_permission permission
  ON permission.app_id = role_permission.app_id
 AND permission.id = role_permission.permission_id
WHERE permission.app_id = 1
  AND permission.permission_code IN (
      'admin:permission-center:view', 'admin:app:view', 'admin:permission:view',
      'admin:data-scope:view', 'admin:role-grant:view',
      'permission:app:list', 'permission:app:add', 'permission:app:edit', 'permission:app:delete',
      'permission:resource:list', 'permission:resource:add', 'permission:resource:edit', 'permission:resource:delete',
      'permission:role-auth:list', 'permission:role-auth:edit',
      'permission:data-scope:list', 'permission:data-scope:add', 'permission:data-scope:edit', 'permission:data-scope:delete'
  );

DELETE role_menu
FROM sys_role_menu role_menu
JOIN sys_menu menu
  ON menu.app_id = role_menu.app_id
 AND menu.id = role_menu.menu_id
WHERE menu.app_id = 1
  AND menu.menu_code IN (
      'admin_permission_center', 'admin_permission_app', 'admin_permission_resource',
      'admin_permission_data_scope', 'admin_permission_role_grant',
      'admin_permission_catalog_v3', 'admin_permission_app_v3', 'admin_permission_data_scope_v3'
  );

DELETE FROM sys_permission
WHERE app_id = 1
  AND permission_code IN (
      'admin:permission-center:view', 'admin:app:view', 'admin:permission:view',
      'admin:data-scope:view', 'admin:role-grant:view',
      'permission:app:list', 'permission:app:add', 'permission:app:edit', 'permission:app:delete',
      'permission:resource:list', 'permission:resource:add', 'permission:resource:edit', 'permission:resource:delete',
      'permission:role-auth:list', 'permission:role-auth:edit',
      'permission:data-scope:list', 'permission:data-scope:add', 'permission:data-scope:edit', 'permission:data-scope:delete'
  );

DELETE FROM sys_menu
WHERE app_id = 1
  AND menu_code IN (
      'admin_permission_center', 'admin_permission_app', 'admin_permission_resource',
      'admin_permission_data_scope', 'admin_permission_role_grant',
      'admin_permission_catalog_v3', 'admin_permission_app_v3', 'admin_permission_data_scope_v3'
  );

INSERT IGNORE INTO sys_role_menu (app_id, role_id, menu_id, deleted)
SELECT menu.app_id, 1, menu.id, 0
FROM sys_menu menu
WHERE menu.app_id = 1
  AND menu.deleted = 0
  AND menu.status = 1;

INSERT IGNORE INTO sys_role_permission (app_id, role_id, permission_id, deleted)
SELECT permission.app_id, 1, permission.id, 0
FROM sys_permission permission
WHERE permission.app_id = 1
  AND permission.deleted = 0
  AND permission.status = 1;

-- ===================== 全球 IP 库管理（查询） =====================
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS ip_library_split_model (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    ip_type VARCHAR(8) NOT NULL COMMENT 'IP 类型：IPV4、IPV6',
    shard_no TINYINT NOT NULL COMMENT '分片编号：1-8',
    table_name VARCHAR(64) NOT NULL COMMENT 'IP 库物理分表名称',
    range_start DECIMAL(39,0) NOT NULL COMMENT '分片起始 IP 数值',
    range_end DECIMAL(39,0) NOT NULL COMMENT '分片截止 IP 数值',
    data_version VARCHAR(32) NOT NULL COMMENT '当前生效数据版本',
    active_flag TINYINT NOT NULL DEFAULT 1 COMMENT '是否生效：1是，0否',
    row_count BIGINT NOT NULL DEFAULT 0 COMMENT '当前分片数据量',
    load_status VARCHAR(32) NOT NULL DEFAULT 'READY' COMMENT '分片数据状态：READY、LOADING、FAILED',
    start_time DATETIME(3) NULL COMMENT '开始处理时间',
    end_time DATETIME(3) NULL COMMENT '处理完毕时间',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    create_by VARCHAR(64) NULL COMMENT '创建人',
    update_by VARCHAR(64) NULL COMMENT '更新人',
    PRIMARY KEY (id),
    UNIQUE KEY uk_ip_shard_version (ip_type, shard_no, data_version),
    KEY idx_ip_route (ip_type, active_flag, range_start, range_end),
    KEY idx_ip_table_active (table_name, active_flag)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='IP库分片路由配置表';

CREATE TABLE IF NOT EXISTS ip_library_v4_data_01 (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    ip_type VARCHAR(8) NOT NULL DEFAULT 'IPV4' COMMENT 'IP 类型：IPV4、IPV6',
    ip_number_start BIGINT UNSIGNED NOT NULL COMMENT 'IP Number 开始值',
    ip_number_end BIGINT UNSIGNED NOT NULL COMMENT 'IP Number 截止值',
    country_alpha2 VARCHAR(2) NOT NULL COMMENT '国家简称 ISO Alpha-2',
    country_alpha3 VARCHAR(3) NOT NULL COMMENT '国家三位字母码 ISO Alpha-3',
    country_numeric VARCHAR(3) NOT NULL COMMENT '国家数字码 ISO Numeric',
    country_name VARCHAR(128) NOT NULL COMMENT '国家英文全称',
    state_province VARCHAR(128) NULL COMMENT '归属州/省',
    city VARCHAR(128) NULL COMMENT '归属城市',
    data_version VARCHAR(32) NOT NULL COMMENT '数据版本',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '录入时间',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    create_by VARCHAR(64) NULL COMMENT '操作人',
    update_by VARCHAR(64) NULL COMMENT '更新人',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除，1已删除',
    PRIMARY KEY (id),
    KEY idx_ip_start (ip_number_start),
    KEY idx_ip_range (ip_number_start, ip_number_end),
    KEY idx_ip_lookup (data_version, deleted, ip_number_start, ip_number_end),
    KEY idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='全球 IPV4 库分表 01';

CREATE TABLE IF NOT EXISTS ip_library_v4_data_02 LIKE ip_library_v4_data_01;
CREATE TABLE IF NOT EXISTS ip_library_v4_data_03 LIKE ip_library_v4_data_01;
CREATE TABLE IF NOT EXISTS ip_library_v4_data_04 LIKE ip_library_v4_data_01;
CREATE TABLE IF NOT EXISTS ip_library_v4_data_05 LIKE ip_library_v4_data_01;
CREATE TABLE IF NOT EXISTS ip_library_v4_data_06 LIKE ip_library_v4_data_01;
CREATE TABLE IF NOT EXISTS ip_library_v4_data_07 LIKE ip_library_v4_data_01;
CREATE TABLE IF NOT EXISTS ip_library_v4_data_08 LIKE ip_library_v4_data_01;

CREATE TABLE IF NOT EXISTS ip_library_v6_data_01 (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    ip_type VARCHAR(8) NOT NULL DEFAULT 'IPV6' COMMENT 'IP 类型：IPV4、IPV6',
    ip_number_start DECIMAL(39,0) NOT NULL COMMENT 'IP Number 开始值',
    ip_number_end DECIMAL(39,0) NOT NULL COMMENT 'IP Number 截止值',
    country_alpha2 VARCHAR(2) NOT NULL COMMENT '国家简称 ISO Alpha-2',
    country_alpha3 VARCHAR(3) NOT NULL COMMENT '国家三位字母码 ISO Alpha-3',
    country_numeric VARCHAR(3) NOT NULL COMMENT '国家数字码 ISO Numeric',
    country_name VARCHAR(128) NOT NULL COMMENT '国家英文全称',
    state_province VARCHAR(128) NULL COMMENT '归属州/省',
    city VARCHAR(128) NULL COMMENT '归属城市',
    data_version VARCHAR(32) NOT NULL COMMENT '数据版本',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '录入时间',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    create_by VARCHAR(64) NULL COMMENT '操作人',
    update_by VARCHAR(64) NULL COMMENT '更新人',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除，1已删除',
    PRIMARY KEY (id),
    KEY idx_ip_start (ip_number_start),
    KEY idx_ip_range (ip_number_start, ip_number_end),
    KEY idx_ip_lookup (data_version, deleted, ip_number_start, ip_number_end),
    KEY idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='全球 IPV6 库分表 01';

CREATE TABLE IF NOT EXISTS ip_library_v6_data_02 LIKE ip_library_v6_data_01;
CREATE TABLE IF NOT EXISTS ip_library_v6_data_03 LIKE ip_library_v6_data_01;
CREATE TABLE IF NOT EXISTS ip_library_v6_data_04 LIKE ip_library_v6_data_01;
CREATE TABLE IF NOT EXISTS ip_library_v6_data_05 LIKE ip_library_v6_data_01;
CREATE TABLE IF NOT EXISTS ip_library_v6_data_06 LIKE ip_library_v6_data_01;
CREATE TABLE IF NOT EXISTS ip_library_v6_data_07 LIKE ip_library_v6_data_01;
CREATE TABLE IF NOT EXISTS ip_library_v6_data_08 LIKE ip_library_v6_data_01;

INSERT INTO ip_library_split_model (ip_type, shard_no, table_name, range_start, range_end, data_version, active_flag, row_count, load_status)
VALUES
('IPV4', 1, 'ip_library_v4_data_01', 0, 536870911, 'DEFAULT', 1, 0, 'READY'),
('IPV4', 2, 'ip_library_v4_data_02', 536870912, 1073741823, 'DEFAULT', 1, 0, 'READY'),
('IPV4', 3, 'ip_library_v4_data_03', 1073741824, 1610612735, 'DEFAULT', 1, 0, 'READY'),
('IPV4', 4, 'ip_library_v4_data_04', 1610612736, 2147483647, 'DEFAULT', 1, 0, 'READY'),
('IPV4', 5, 'ip_library_v4_data_05', 2147483648, 2684354559, 'DEFAULT', 1, 0, 'READY'),
('IPV4', 6, 'ip_library_v4_data_06', 2684354560, 3221225471, 'DEFAULT', 1, 0, 'READY'),
('IPV4', 7, 'ip_library_v4_data_07', 3221225472, 3758096383, 'DEFAULT', 1, 0, 'READY'),
('IPV4', 8, 'ip_library_v4_data_08', 3758096384, 4294967295, 'DEFAULT', 1, 0, 'READY'),
('IPV6', 1, 'ip_library_v6_data_01', 0, 42535295865117307932921825928971026431, 'DEFAULT', 1, 0, 'READY'),
('IPV6', 2, 'ip_library_v6_data_02', 42535295865117307932921825928971026432, 85070591730234615865843651857942052863, 'DEFAULT', 1, 0, 'READY'),
('IPV6', 3, 'ip_library_v6_data_03', 85070591730234615865843651857942052864, 127605887595351923798765477786913079295, 'DEFAULT', 1, 0, 'READY'),
('IPV6', 4, 'ip_library_v6_data_04', 127605887595351923798765477786913079296, 170141183460469231731687303715884105727, 'DEFAULT', 1, 0, 'READY'),
('IPV6', 5, 'ip_library_v6_data_05', 170141183460469231731687303715884105728, 212676479325586539664609129644855132159, 'DEFAULT', 1, 0, 'READY'),
('IPV6', 6, 'ip_library_v6_data_06', 212676479325586539664609129644855132160, 255211775190703847597530955573826158591, 'DEFAULT', 1, 0, 'READY'),
('IPV6', 7, 'ip_library_v6_data_07', 255211775190703847597530955573826158592, 297747071055821155530452781502797185023, 'DEFAULT', 1, 0, 'READY'),
('IPV6', 8, 'ip_library_v6_data_08', 297747071055821155530452781502797185024, 340282366920938463463374607431768211455, 'DEFAULT', 1, 0, 'READY')
ON DUPLICATE KEY UPDATE table_name = VALUES(table_name), range_start = VALUES(range_start), range_end = VALUES(range_end), active_flag = VALUES(active_flag), update_time = CURRENT_TIMESTAMP(3);

INSERT INTO sys_menu (app_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path, permission_code, icon, visible, sort_no, status, deleted)
SELECT 1, parent.id, 'base_ip_library', '全球IP库管理', 'MENU', '/base/ip-library', 'base/ip-library', 'base:ip-library:list', 'Connection', 1, 35, 1, 0
FROM sys_menu parent
WHERE parent.app_id = 1 AND parent.menu_code = 'base' AND parent.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_menu exists_menu WHERE exists_menu.app_id = 1 AND exists_menu.menu_code = 'base_ip_library' AND exists_menu.deleted = 0);

UPDATE sys_menu menu
JOIN sys_menu parent ON parent.app_id = menu.app_id AND parent.menu_code = 'base' AND parent.deleted = 0
SET menu.parent_id = parent.id,
    menu.menu_name = '全球IP库管理',
    menu.menu_type = 'MENU',
    menu.route_path = '/base/ip-library',
    menu.component_path = 'base/ip-library',
    menu.permission_code = 'base:ip-library:list',
    menu.icon = 'Connection',
    menu.visible = 1,
    menu.sort_no = 35,
    menu.status = 1,
    menu.updated_at = CURRENT_TIMESTAMP(3)
WHERE menu.app_id = 1 AND menu.menu_code = 'base_ip_library' AND menu.deleted = 0;

INSERT INTO sys_permission (app_id, menu_id, permission_code, permission_name, permission_type, resource_method, resource_path, status, deleted)
SELECT 1, menu.id, 'base:ip-library:list', '全球IP库查询', 'MENU', 'POST', '/admin/base/ip-library/**', 1, 0
FROM sys_menu menu
WHERE menu.app_id = 1 AND menu.menu_code = 'base_ip_library' AND menu.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_permission p WHERE p.app_id = 1 AND p.permission_code = 'base:ip-library:list' AND p.deleted = 0);

UPDATE sys_permission permission
JOIN sys_menu menu ON menu.app_id = permission.app_id AND menu.menu_code = 'base_ip_library' AND menu.deleted = 0
SET permission.menu_id = menu.id,
    permission.permission_name = '全球IP库查询',
    permission.permission_type = 'MENU',
    permission.resource_method = 'POST',
    permission.resource_path = '/admin/base/ip-library/**',
    permission.status = 1,
    permission.updated_at = CURRENT_TIMESTAMP(3)
WHERE permission.app_id = 1 AND permission.permission_code = 'base:ip-library:list' AND permission.deleted = 0;

INSERT IGNORE INTO sys_role_menu (app_id, role_id, menu_id, deleted)
SELECT menu.app_id, 1, menu.id, 0
FROM sys_menu menu
WHERE menu.app_id = 1 AND menu.menu_code = 'base_ip_library' AND menu.deleted = 0 AND menu.status = 1;

INSERT IGNORE INTO sys_role_permission (app_id, role_id, permission_id, deleted)
SELECT permission.app_id, 1, permission.id, 0
FROM sys_permission permission
WHERE permission.app_id = 1 AND permission.permission_code = 'base:ip-library:list' AND permission.deleted = 0 AND permission.status = 1;

CREATE TABLE IF NOT EXISTS base_card_bin_range (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    legacy_pk_id BIGINT NULL COMMENT '旧表card_bin_type_info.pk_id，用于初始化数据追溯',
    card_bin_start BIGINT UNSIGNED NOT NULL COMMENT '卡BIN开始值，统一按11位数字存储，不足位右侧补0',
    card_bin_end BIGINT UNSIGNED NOT NULL COMMENT '卡BIN结束值，统一按11位数字存储，不足位右侧补9',
    bin_length TINYINT UNSIGNED NOT NULL DEFAULT 11 COMMENT 'BIN精度长度：6、7、8、9、10、11',
    card_brand VARCHAR(64) NOT NULL DEFAULT 'UNKNOWN' COMMENT '卡品牌：复用系统已有卡品牌字典',
    card_sub_brand VARCHAR(128) NULL COMMENT '卡子品牌/产品名称',
    card_type VARCHAR(32) NOT NULL DEFAULT 'UNKNOWN' COMMENT '卡类型：CREDIT、DEBIT、PREPAID、CHARGE、COMMERCIAL、UNKNOWN',
    card_level VARCHAR(64) NULL COMMENT '卡等级',
    issuer_country_name VARCHAR(128) NULL COMMENT '发卡行国家全称',
    issuer_country_alpha2 CHAR(2) NULL COMMENT '发卡行国家ISO Alpha-2',
    issuer_country_alpha3 CHAR(3) NULL COMMENT '发卡行国家ISO Alpha-3',
    issuer_country_numeric CHAR(3) NULL COMMENT '发卡行国家ISO Numeric',
    issuer_bank VARCHAR(256) NULL COMMENT '隶属发卡行',
    issuer_web_url VARCHAR(512) NULL COMMENT '发卡行网页访问URL',
    issuer_telephone VARCHAR(64) NULL COMMENT '发卡行联系电话',
    data_source VARCHAR(64) NOT NULL DEFAULT 'MANUAL' COMMENT '数据来源',
    source_batch_no VARCHAR(64) NULL COMMENT '来源批次号',
    source_priority TINYINT NOT NULL DEFAULT 50 COMMENT '来源优先级，数值越大优先级越高',
    effective_time DATETIME(3) NULL COMMENT '生效时间',
    expire_time DATETIME(3) NULL COMMENT '失效时间',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0禁用，1启用，2待确认，3已过期',
    remark VARCHAR(512) NULL COMMENT '备注',
    create_by VARCHAR(64) NULL COMMENT '创建人',
    update_by VARCHAR(64) NULL COMMENT '修改人',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '修改时间',
    deleted BIGINT NOT NULL DEFAULT 0 COMMENT '删除标识：0未删除，大于0为删除记录ID',
    PRIMARY KEY (id),
    UNIQUE KEY uk_card_bin_legacy_deleted (data_source, legacy_pk_id, deleted),
    KEY idx_card_bin_range_status (deleted, status, card_bin_start, card_bin_end),
    KEY idx_card_bin_start (card_bin_start),
    KEY idx_card_bin_end (card_bin_end),
    KEY idx_card_bin_brand_country (card_brand, issuer_country_alpha2, deleted),
    KEY idx_card_bin_type (card_type, deleted),
    KEY idx_card_bin_country (issuer_country_alpha2, deleted),
    KEY idx_card_bin_bank (issuer_bank),
    KEY idx_card_bin_source_batch (source_batch_no, deleted),
    KEY idx_card_bin_update_time (update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='基础卡BIN区间表';

CREATE TABLE IF NOT EXISTS base_card_bin_import_batch (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    batch_no VARCHAR(64) NOT NULL COMMENT '批次号',
    import_type VARCHAR(32) NOT NULL DEFAULT 'DB_INIT' COMMENT '导入类型：DB_INIT、EXCEL、CSV、API',
    data_source VARCHAR(64) NOT NULL DEFAULT 'LEGACY_DB' COMMENT '数据来源',
    file_name VARCHAR(255) NULL COMMENT '文件名称，数据库初始化导入可为空',
    total_count INT NOT NULL DEFAULT 0 COMMENT '总条数',
    success_count INT NOT NULL DEFAULT 0 COMMENT '成功条数',
    failed_count INT NOT NULL DEFAULT 0 COMMENT '失败条数',
    conflict_count INT NOT NULL DEFAULT 0 COMMENT '冲突条数',
    duplicate_count INT NOT NULL DEFAULT 0 COMMENT '重复条数',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0处理中，1成功，2部分成功，3失败',
    error_message VARCHAR(1024) NULL COMMENT '错误信息',
    remark VARCHAR(512) NULL COMMENT '备注',
    create_by VARCHAR(64) NULL COMMENT '创建人',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_card_bin_batch_no (batch_no),
    KEY idx_card_bin_batch_source (data_source),
    KEY idx_card_bin_batch_status (status),
    KEY idx_card_bin_batch_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='基础卡BIN导入批次表';

INSERT IGNORE INTO sys_dict_type (id, dict_name, dict_type, biz_domain, system_builtin, editable, status, deleted) VALUES
(39, '卡类型', 'base_card_type', 'base', 1, 1, 1, 0),
(40, '卡BIN状态', 'base_card_bin_status', 'base', 1, 1, 1, 0),
(41, '卡BIN数据来源', 'base_card_bin_data_source', 'base', 1, 1, 1, 0);

INSERT IGNORE INTO sys_dict_data (id, dict_type, dict_label, dict_value, locale, dict_sort, list_class, extra_json, is_default, status, deleted) VALUES
(3900, 'base_card_type', '信用卡', 'CREDIT', 'zh-CN', 1, 'primary', NULL, 0, 1, 0),
(3901, 'base_card_type', '借记卡', 'DEBIT', 'zh-CN', 2, 'success', NULL, 0, 1, 0),
(3902, 'base_card_type', '预付卡', 'PREPAID', 'zh-CN', 3, 'warning', NULL, 0, 1, 0),
(3903, 'base_card_type', '签账卡', 'CHARGE', 'zh-CN', 4, 'primary', NULL, 0, 1, 0),
(3904, 'base_card_type', '商务卡', 'COMMERCIAL', 'zh-CN', 5, 'primary', NULL, 0, 1, 0),
(3905, 'base_card_type', '未知', 'UNKNOWN', 'zh-CN', 99, 'info', NULL, 1, 1, 0),
(4000, 'base_card_bin_status', '禁用', '0', 'zh-CN', 1, 'info', NULL, 0, 1, 0),
(4001, 'base_card_bin_status', '启用', '1', 'zh-CN', 2, 'success', NULL, 1, 1, 0),
(4002, 'base_card_bin_status', '待确认', '2', 'zh-CN', 3, 'warning', NULL, 0, 1, 0),
(4003, 'base_card_bin_status', '已过期', '3', 'zh-CN', 4, 'danger', NULL, 0, 1, 0),
(4100, 'base_card_bin_data_source', '手工维护', 'MANUAL', 'zh-CN', 1, 'primary', NULL, 1, 1, 0),
(4101, 'base_card_bin_data_source', '旧库导入', 'LEGACY_DB', 'zh-CN', 2, 'success', NULL, 0, 1, 0),
(4102, 'base_card_bin_data_source', 'Visa', 'VISA', 'zh-CN', 3, 'primary', NULL, 0, 1, 0),
(4103, 'base_card_bin_data_source', 'Mastercard', 'MASTERCARD', 'zh-CN', 4, 'primary', NULL, 0, 1, 0),
(4104, 'base_card_bin_data_source', '渠道返回', 'CHANNEL', 'zh-CN', 5, 'warning', NULL, 0, 1, 0),
(4105, 'base_card_bin_data_source', '第三方数据', 'THIRD_PARTY', 'zh-CN', 6, 'info', NULL, 0, 1, 0),
(13900, 'base_card_type', 'Credit Card', 'CREDIT', 'en-US', 1, 'primary', NULL, 0, 1, 0),
(13901, 'base_card_type', 'Debit Card', 'DEBIT', 'en-US', 2, 'success', NULL, 0, 1, 0),
(13902, 'base_card_type', 'Prepaid Card', 'PREPAID', 'en-US', 3, 'warning', NULL, 0, 1, 0),
(13903, 'base_card_type', 'Charge Card', 'CHARGE', 'en-US', 4, 'primary', NULL, 0, 1, 0),
(13904, 'base_card_type', 'Commercial Card', 'COMMERCIAL', 'en-US', 5, 'primary', NULL, 0, 1, 0),
(13905, 'base_card_type', 'Unknown', 'UNKNOWN', 'en-US', 99, 'info', NULL, 1, 1, 0),
(14000, 'base_card_bin_status', 'Disabled', '0', 'en-US', 1, 'info', NULL, 0, 1, 0),
(14001, 'base_card_bin_status', 'Enabled', '1', 'en-US', 2, 'success', NULL, 1, 1, 0),
(14002, 'base_card_bin_status', 'Pending Review', '2', 'en-US', 3, 'warning', NULL, 0, 1, 0),
(14003, 'base_card_bin_status', 'Expired', '3', 'en-US', 4, 'danger', NULL, 0, 1, 0),
(14100, 'base_card_bin_data_source', 'Manual', 'MANUAL', 'en-US', 1, 'primary', NULL, 1, 1, 0),
(14101, 'base_card_bin_data_source', 'Legacy DB', 'LEGACY_DB', 'en-US', 2, 'success', NULL, 0, 1, 0),
(14102, 'base_card_bin_data_source', 'Visa', 'VISA', 'en-US', 3, 'primary', NULL, 0, 1, 0),
(14103, 'base_card_bin_data_source', 'Mastercard', 'MASTERCARD', 'en-US', 4, 'primary', NULL, 0, 1, 0),
(14104, 'base_card_bin_data_source', 'Channel', 'CHANNEL', 'en-US', 5, 'warning', NULL, 0, 1, 0),
(14105, 'base_card_bin_data_source', 'Third Party', 'THIRD_PARTY', 'en-US', 6, 'info', NULL, 0, 1, 0);

INSERT INTO sys_menu (app_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path, permission_code, icon, visible, sort_no, status, deleted)
SELECT 1, parent.id, 'base_card_bin', '卡BIN库管理', 'MENU', '/base/card-bin', 'base/cardBin/index', 'base:cardBin:list', 'CreditCard', 1, 36, 1, 0
FROM sys_menu parent
WHERE parent.app_id = 1 AND parent.menu_code = 'base' AND parent.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_menu exists_menu WHERE exists_menu.app_id = 1 AND exists_menu.menu_code = 'base_card_bin' AND exists_menu.deleted = 0);

UPDATE sys_menu menu
JOIN sys_menu parent ON parent.app_id = menu.app_id AND parent.menu_code = 'base' AND parent.deleted = 0
SET menu.parent_id = parent.id,
    menu.menu_name = '卡BIN库管理',
    menu.menu_type = 'MENU',
    menu.route_path = '/base/card-bin',
    menu.component_path = 'base/cardBin/index',
    menu.permission_code = 'base:cardBin:list',
    menu.icon = 'CreditCard',
    menu.visible = 1,
    menu.sort_no = 36,
    menu.status = 1,
    menu.updated_at = CURRENT_TIMESTAMP(3)
WHERE menu.app_id = 1 AND menu.menu_code = 'base_card_bin' AND menu.deleted = 0;

INSERT INTO sys_permission (app_id, menu_id, permission_code, permission_name, permission_type, resource_method, resource_path, status, deleted)
SELECT 1, menu.id, perm.permission_code, perm.permission_name, perm.permission_type, perm.resource_method, perm.resource_path, 1, 0
FROM sys_menu menu
JOIN (
    SELECT 'base:cardBin:list' AS permission_code, '卡BIN库查询' AS permission_name, 'MENU' AS permission_type, 'POST' AS resource_method, '/admin/base/card-bin/page' AS resource_path
    UNION ALL SELECT 'base:cardBin:query', '卡BIN库详情', 'BUTTON', 'GET', '/admin/base/card-bin/*'
    UNION ALL SELECT 'base:cardBin:add', '卡BIN库新增', 'BUTTON', 'POST', '/admin/base/card-bin'
    UNION ALL SELECT 'base:cardBin:edit', '卡BIN库修改', 'BUTTON', 'PUT', '/admin/base/card-bin/*'
    UNION ALL SELECT 'base:cardBin:remove', '卡BIN库删除', 'BUTTON', 'DELETE', '/admin/base/card-bin/*'
    UNION ALL SELECT 'base:cardBin:status', '卡BIN库状态', 'BUTTON', 'PUT', '/admin/base/card-bin/*/status'
    UNION ALL SELECT 'base:cardBin:match', '卡BIN匹配测试', 'BUTTON', 'POST', '/admin/base/card-bin/match'
    UNION ALL SELECT 'base:cardBin:export', '卡BIN库导出', 'BUTTON', 'POST', '/admin/base/card-bin/export'
    UNION ALL SELECT 'base:cardBin:init', '卡BIN旧库初始化', 'BUTTON', 'POST', '/admin/base/card-bin/init-from-legacy-db'
) perm
WHERE menu.app_id = 1 AND menu.menu_code = 'base_card_bin' AND menu.deleted = 0
  AND NOT EXISTS (
      SELECT 1
      FROM sys_permission exists_permission
      WHERE exists_permission.app_id = 1
        AND exists_permission.permission_code = perm.permission_code
        AND exists_permission.deleted = 0
  );

UPDATE sys_permission permission
JOIN sys_menu menu ON menu.app_id = permission.app_id AND menu.menu_code = 'base_card_bin' AND menu.deleted = 0
SET permission.menu_id = menu.id,
    permission.status = 1,
    permission.updated_at = CURRENT_TIMESTAMP(3)
WHERE permission.app_id = 1
  AND permission.permission_code LIKE 'base:cardBin:%'
  AND permission.deleted = 0;

INSERT IGNORE INTO sys_role_menu (app_id, role_id, menu_id, deleted)
SELECT menu.app_id, 1, menu.id, 0
FROM sys_menu menu
WHERE menu.app_id = 1 AND menu.menu_code = 'base_card_bin' AND menu.deleted = 0 AND menu.status = 1;

INSERT IGNORE INTO sys_role_permission (app_id, role_id, permission_id, deleted)
SELECT permission.app_id, 1, permission.id, 0
FROM sys_permission permission
WHERE permission.app_id = 1 AND permission.permission_code LIKE 'base:cardBin:%' AND permission.deleted = 0 AND permission.status = 1;

SET @card_bin_batch_no = CONCAT('INIT_DB_IMPORT_', DATE_FORMAT(NOW(3), '%Y%m%d%H%i%s%f'));
SET @card_bin_legacy_exists = (
    SELECT COUNT(1)
    FROM information_schema.tables
    WHERE table_schema = DATABASE()
      AND table_name = 'card_bin_type_info'
);

SET @card_bin_insert_batch_sql = IF(@card_bin_legacy_exists > 0,
    'INSERT INTO base_card_bin_import_batch (
        batch_no, import_type, data_source, file_name, total_count, success_count, failed_count,
        conflict_count, duplicate_count, status, remark, create_by, create_time, update_time
     )
     VALUES (@card_bin_batch_no, ''DB_INIT'', ''LEGACY_DB'', NULL, 0, 0, 0, 0, 0, 0, ''从旧表 card_bin_type_info 初始化导入'', ''system'', NOW(3), NOW(3))',
    'SELECT 1'
);
PREPARE stmt FROM @card_bin_insert_batch_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @card_bin_import_sql = IF(@card_bin_legacy_exists > 0,
    'INSERT INTO base_card_bin_range (
        legacy_pk_id, card_bin_start, card_bin_end, bin_length, card_brand, card_sub_brand, card_type, card_level,
        issuer_country_name, issuer_country_alpha2, issuer_country_alpha3, issuer_country_numeric,
        issuer_bank, issuer_web_url, issuer_telephone, data_source, source_batch_no, source_priority,
        effective_time, expire_time, status, remark, create_by, update_by, create_time, update_time, deleted
     )
     SELECT
        t.pk_id,
        t.card_bin_start,
        t.card_bin_end,
        CASE
            WHEN MOD(t.card_bin_start, 100000) = 0 AND MOD(t.card_bin_end, 100000) = 99999 AND FLOOR(t.card_bin_start / 100000) = FLOOR(t.card_bin_end / 100000) THEN 6
            WHEN MOD(t.card_bin_start, 10000) = 0 AND MOD(t.card_bin_end, 10000) = 9999 AND FLOOR(t.card_bin_start / 10000) = FLOOR(t.card_bin_end / 10000) THEN 7
            WHEN MOD(t.card_bin_start, 1000) = 0 AND MOD(t.card_bin_end, 1000) = 999 AND FLOOR(t.card_bin_start / 1000) = FLOOR(t.card_bin_end / 1000) THEN 8
            WHEN MOD(t.card_bin_start, 100) = 0 AND MOD(t.card_bin_end, 100) = 99 AND FLOOR(t.card_bin_start / 100) = FLOOR(t.card_bin_end / 100) THEN 9
            WHEN MOD(t.card_bin_start, 10) = 0 AND MOD(t.card_bin_end, 10) = 9 AND FLOOR(t.card_bin_start / 10) = FLOOR(t.card_bin_end / 10) THEN 10
            ELSE 11
        END,
        COALESCE(NULLIF(UPPER(TRIM(t.card_brand)), ''''), ''UNKNOWN''),
        NULLIF(TRIM(t.card_sub_brand), ''''),
        CASE UPPER(TRIM(t.credit_debit))
            WHEN ''CREDIT'' THEN ''CREDIT''
            WHEN ''DEBIT'' THEN ''DEBIT''
            WHEN ''PREPAID'' THEN ''PREPAID''
            WHEN ''CHARGE'' THEN ''CHARGE''
            WHEN ''COMMERCIAL'' THEN ''COMMERCIAL''
            ELSE ''UNKNOWN''
        END,
        NULL,
        NULLIF(TRIM(t.issuer_country_name), ''''),
        NULLIF(UPPER(TRIM(t.issuer_country_code_ii)), ''''),
        NULLIF(UPPER(TRIM(t.issuer_country_code)), ''''),
        NULLIF(TRIM(t.issuer_country_number), ''''),
        NULLIF(TRIM(t.issuer_bank), ''''),
        NULLIF(TRIM(t.issuer_web_url), ''''),
        NULLIF(TRIM(t.issuer_telephone), ''''),
        ''LEGACY_DB'',
        @card_bin_batch_no,
        50,
        NULL,
        NULL,
        1,
        ''旧表card_bin_type_info初始化导入'',
        ''system'',
        ''system'',
        COALESCE(t.gmt_create, NOW(3)),
        COALESCE(t.gmt_modified, NOW(3)),
        0
      FROM card_bin_type_info t
      WHERE t.card_bin_start IS NOT NULL
        AND t.card_bin_end IS NOT NULL
        AND t.card_bin_start <= t.card_bin_end
        AND NOT EXISTS (
            SELECT 1
            FROM base_card_bin_range r
            WHERE r.data_source = ''LEGACY_DB''
              AND r.legacy_pk_id = t.pk_id
              AND r.deleted = 0
        )',
    'SELECT 1'
);
PREPARE stmt FROM @card_bin_import_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @card_bin_update_batch_sql = IF(@card_bin_legacy_exists > 0,
    'UPDATE base_card_bin_import_batch batch
     SET
        total_count = (SELECT COUNT(1) FROM card_bin_type_info),
        success_count = (SELECT COUNT(1) FROM base_card_bin_range WHERE source_batch_no = @card_bin_batch_no),
        failed_count = (
            SELECT COUNT(1)
            FROM card_bin_type_info
            WHERE card_bin_start IS NULL OR card_bin_end IS NULL OR card_bin_start > card_bin_end
        ),
        duplicate_count = (
            SELECT COUNT(1)
            FROM card_bin_type_info t
            WHERE EXISTS (
                SELECT 1
                FROM base_card_bin_range r
                WHERE r.data_source = ''LEGACY_DB''
                  AND r.legacy_pk_id = t.pk_id
                  AND r.source_batch_no <> @card_bin_batch_no
                  AND r.deleted = 0
            )
        ),
        update_time = NOW(3)
     WHERE batch.batch_no = @card_bin_batch_no',
    'SELECT 1'
);
PREPARE stmt FROM @card_bin_update_batch_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE base_card_bin_import_batch
SET status = CASE
    WHEN success_count = 0 AND total_count > 0 THEN 3
    WHEN failed_count > 0 OR duplicate_count > 0 THEN 2
    ELSE 1
END
WHERE batch_no = @card_bin_batch_no
  AND @card_bin_legacy_exists > 0;
