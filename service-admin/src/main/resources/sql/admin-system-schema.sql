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
    merchant_id VARCHAR(32) NULL COMMENT '商户号，后台操作涉及商户时记录',
    module_name VARCHAR(100) NULL COMMENT '模块名称，如商户管理、费率管理、系统配置',
    business_type TINYINT NULL COMMENT '业务类型：1新增，2修改，3删除，4查询，5导出，6审核，7冻结，8解冻',
    method_name VARCHAR(255) NULL COMMENT '后端方法名称',
    request_method VARCHAR(20) NULL COMMENT '请求方式：GET、POST、PUT、DELETE',
    operator_type TINYINT NOT NULL DEFAULT 1 COMMENT '操作人类别：1后台用户，2商户用户，3系统任务',
    operator_id VARCHAR(64) NULL COMMENT '操作人ID',
    operator_name VARCHAR(100) NULL COMMENT '操作人名称',
    oper_url VARCHAR(500) NULL COMMENT '请求URL',
    oper_ip VARCHAR(45) NULL COMMENT '操作IP，支持IPv4/IPv6',
    oper_location VARCHAR(255) NULL COMMENT '操作地点',
    request_param TEXT NULL COMMENT '脱敏后的请求参数，禁止记录密钥、卡号、CVV、JWT明文',
    response_result TEXT NULL COMMENT '脱敏后的响应结果',
    cost_time BIGINT NULL COMMENT '执行时长，单位毫秒',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '操作状态：0失败，1成功',
    error_code VARCHAR(32) NULL COMMENT '错误码',
    error_msg VARCHAR(1000) NULL COMMENT '错误信息，禁止写入堆栈明文',
    operated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '操作时间',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_sys_oper_trace_id (trace_id),
    KEY idx_sys_oper_request_id (request_id),
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

INSERT IGNORE INTO sys_menu (id, app_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path, permission_code, icon, visible, sort_no, status, deleted)
VALUES
    (1, 1, 0, 'admin_dashboard', '控制台', 'MENU', '/dashboard', 'admin/dashboard/index', 'admin:dashboard:view', 'dashboard', 1, 1, 1, 0),
    (2, 1, 0, 'admin_system', '系统管理', 'CATALOG', '/system', NULL, 'admin:system:view', 'setting', 1, 10, 1, 0),
    (3, 1, 2, 'admin_user', '用户管理', 'MENU', '/system/users', 'admin/system/user/index', 'admin:user:view', 'user', 1, 11, 1, 0),
    (4, 1, 2, 'admin_role', '角色管理', 'MENU', '/system/roles', 'admin/system/role/index', 'admin:role:view', 'lock', 1, 12, 1, 0),
    (5, 2, 0, 'merchant_dashboard', '商户首页', 'MENU', '/dashboard', 'merchant/dashboard/index', 'merchant:dashboard:view', 'dashboard', 1, 1, 1, 0),
    (6, 2, 0, 'merchant_transaction', '交易管理', 'MENU', '/transactions', 'merchant/transaction/index', 'merchant:transaction:view', 'transaction', 1, 10, 1, 0),
    (7, 2, 0, 'merchant_settlement', '结算管理', 'MENU', '/settlements', 'merchant/settlement/index', 'merchant:settlement:view', 'settlement', 1, 20, 1, 0),
    (8, 2, 0, 'merchant_account', '账号管理', 'MENU', '/account/users', 'merchant/account/user/index', 'merchant:account:view', 'user', 1, 30, 1, 0);

INSERT IGNORE INTO sys_permission (id, app_id, menu_id, permission_code, permission_name, permission_type, resource_method, resource_path, status, deleted)
VALUES
    (1, 1, 1, 'admin:dashboard:view', '后台控制台查看', 'MENU', 'GET', '/admin/dashboard/**', 1, 0),
    (2, 1, 3, 'admin:user:view', '后台用户查看', 'API', 'POST', '/admin/users/search', 1, 0),
    (3, 1, 3, 'admin:user:create', '后台用户创建', 'API', 'POST', '/admin/auth/register', 1, 0),
    (4, 1, 4, 'admin:role:view', '后台角色查看', 'API', 'POST', '/admin/roles/search', 1, 0),
    (5, 2, 5, 'merchant:dashboard:view', '商户首页查看', 'MENU', 'GET', '/merchant/dashboard/**', 1, 0),
    (6, 2, 6, 'merchant:transaction:view', '商户交易查看', 'API', 'POST', '/merchant/transactions/search', 1, 0),
    (7, 2, 7, 'merchant:settlement:view', '商户结算查看', 'API', 'POST', '/merchant/settlements/search', 1, 0),
    (8, 2, 8, 'merchant:account:view', '商户账号查看', 'API', 'POST', '/merchant/account/users/search', 1, 0),
    (9, 2, 8, 'merchant:account:create', '商户账号创建', 'API', 'POST', '/merchant/auth/register', 1, 0);

INSERT IGNORE INTO sys_role_menu (app_id, role_id, menu_id, deleted)
SELECT app_id, 1, id, 0 FROM sys_menu WHERE app_id = 1 AND deleted = 0;

INSERT IGNORE INTO sys_role_menu (app_id, role_id, menu_id, deleted)
SELECT app_id, 2, id, 0 FROM sys_menu WHERE app_id = 2 AND deleted = 0;

INSERT IGNORE INTO sys_role_permission (app_id, role_id, permission_id, deleted)
SELECT app_id, 1, id, 0 FROM sys_permission WHERE app_id = 1 AND deleted = 0;

INSERT IGNORE INTO sys_role_permission (app_id, role_id, permission_id, deleted)
SELECT app_id, 2, id, 0 FROM sys_permission WHERE app_id = 2 AND deleted = 0;

SET FOREIGN_KEY_CHECKS = 1;
