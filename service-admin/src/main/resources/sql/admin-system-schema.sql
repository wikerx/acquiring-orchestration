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
    (4, 1, 2, 'admin_role', '角色管理', 'MENU', '/system/roles', 'admin/system/role/index', 'admin:role:view', 'lock', 1, 12, 1, 0),
    (5, 2, 0, 'merchant_dashboard', '商户首页', 'MENU', '/dashboard', 'merchant/dashboard/index', 'merchant:dashboard:view', 'dashboard', 1, 1, 1, 0),
    (6, 2, 0, 'merchant_transaction', '交易管理', 'MENU', '/transactions', 'merchant/transaction/index', 'merchant:transaction:view', 'transaction', 1, 10, 1, 0),
    (7, 2, 0, 'merchant_settlement', '结算管理', 'MENU', '/settlements', 'merchant/settlement/index', 'merchant:settlement:view', 'settlement', 1, 20, 1, 0),
    (8, 2, 0, 'merchant_account', '账号管理', 'MENU', '/account/users', 'merchant/account/user/index', 'merchant:account:view', 'user', 1, 30, 1, 0);

INSERT IGNORE INTO sys_menu (id, app_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path, permission_code, icon, visible, sort_no, status, deleted)
VALUES
    (9, 1, 2, 'admin_config', '参数设置', 'MENU', '/system/configs', 'admin/system/config/index', 'admin:config:view', 'setting', 1, 17, 1, 0),
    (10, 1, 2, 'admin_dict', '字典管理', 'MENU', '/system/dicts', 'admin/system/dict/index', 'admin:dict:view', 'dict', 1, 16, 1, 0),
    (11, 1, 2, 'admin_oper_log', '操作日志', 'MENU', '/system/oper-logs', 'admin/system/oper-log/index', 'admin:oper-log:view', 'log', 1, 18, 1, 0),
    (12, 2, 0, 'merchant_info', '商户信息', 'MENU', '/merchant/info', 'merchant/info/index', 'merchant:info:view', 'shop', 1, 2, 1, 0),
    (13, 2, 0, 'merchant_store', '店铺管理', 'MENU', '/stores', 'merchant/store/index', 'merchant:store:view', 'store', 1, 5, 1, 0),
    (14, 2, 0, 'merchant_order', '订单查询', 'MENU', '/orders', 'merchant/order/index', 'merchant:order:view', 'order', 1, 11, 1, 0),
    (15, 2, 0, 'merchant_refund', '退款管理', 'MENU', '/refunds', 'merchant/refund/index', 'merchant:refund:view', 'refund', 1, 12, 1, 0),
    (16, 2, 0, 'merchant_api_key', 'API密钥', 'MENU', '/api-keys', 'merchant/api-key/index', 'merchant:api-key:view', 'key', 1, 31, 1, 0),
    (17, 2, 0, 'merchant_oper_log', '操作日志', 'MENU', '/oper-logs', 'merchant/oper-log/index', 'merchant:oper-log:view', 'log', 1, 32, 1, 0);

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
    (4, 1, 4, 'admin:role:view', '后台角色查看', 'API', 'POST', '/admin/roles/search', 1, 0),
    (5, 2, 5, 'merchant:dashboard:view', '商户首页查看', 'MENU', 'GET', '/merchant/dashboard/**', 1, 0),
    (6, 2, 6, 'merchant:transaction:view', '商户交易查看', 'API', 'POST', '/merchant/transactions/search', 1, 0),
    (7, 2, 7, 'merchant:settlement:view', '商户结算查看', 'API', 'POST', '/merchant/settlements/search', 1, 0),
    (8, 2, 8, 'merchant:account:view', '商户账号查看', 'API', 'POST', '/merchant/account/users/search', 1, 0),
    (9, 2, 8, 'merchant:account:create', '商户账号创建', 'API', 'POST', '/merchant/auth/register', 1, 0);

INSERT IGNORE INTO sys_permission (id, app_id, menu_id, permission_code, permission_name, permission_type, resource_method, resource_path, status, deleted)
VALUES
    (10, 1, 9, 'admin:config:view', '系统配置查看', 'API', 'GET', '/admin/system/configs/**', 1, 0),
    (11, 1, 9, 'admin:config:save', '系统配置保存', 'API', 'POST', '/admin/system/configs', 1, 0),
    (12, 1, 9, 'admin:config:delete', '系统配置删除', 'API', 'DELETE', '/admin/system/configs/**', 1, 0),
    (13, 1, 10, 'admin:dict:view', '数据字典查看', 'API', 'POST', '/admin/system/dicts/**/search', 1, 0),
    (14, 1, 10, 'admin:dict:save', '数据字典保存', 'API', 'POST', '/admin/system/dicts/**', 1, 0),
    (15, 1, 10, 'admin:dict:delete', '数据字典删除', 'API', 'DELETE', '/admin/system/dicts/**', 1, 0),
    (16, 1, 11, 'admin:oper-log:view', '后台操作日志查看', 'API', 'POST', '/admin/system/oper-logs/search', 1, 0),
    (17, 1, 11, 'admin:oper-log:create', '后台操作日志写入', 'API', 'POST', '/admin/system/oper-logs', 1, 0),
    (18, 2, 12, 'merchant:info:view', '商户信息查看', 'API', 'GET', '/merchant/info/**', 1, 0),
    (19, 2, 13, 'merchant:store:view', '商户店铺查看', 'API', 'GET', '/merchant/stores/**', 1, 0),
    (20, 2, 13, 'merchant:store:manage', '商户店铺管理', 'API', '*', '/merchant/stores/**', 1, 0),
    (21, 2, 14, 'merchant:order:view', '商户订单查询', 'API', '*', '/merchant/orders/**', 1, 0),
    (22, 2, 15, 'merchant:refund:apply', '商户退款申请', 'API', 'POST', '/merchant/refunds/**', 1, 0),
    (23, 2, 7, 'merchant:settlement:view', '商户结算查询', 'API', '*', '/merchant/settlements/**', 1, 0),
    (24, 2, 8, 'merchant:account:view', '商户账户查询', 'API', '*', '/merchant/account/**', 1, 0),
    (25, 2, 16, 'merchant:api-key:view', '商户API密钥查看', 'API', 'GET', '/merchant/api-keys/**', 1, 0),
    (26, 2, 16, 'merchant:api-key:manage', '商户API密钥管理', 'API', '*', '/merchant/api-keys/**', 1, 0),
    (27, 2, 17, 'merchant:oper-log:view', '商户操作日志查询', 'API', '*', '/merchant/oper-logs/**', 1, 0);

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
    (143, 1, 140, 'admin_base_region_currency', '地区币种配置', 'MENU', '/base/region-currency', 'base/region-currency/index', 'admin:region-currency:view', 'Connection', 1, 33, 1, 0),
    (160, 1, 0, 'admin_permission_center', '权限中心', 'CATALOG', '/permission', NULL, 'admin:permission-center:view', 'Key', 1, 40, 1, 0),
    (161, 1, 160, 'admin_permission_app', '应用管理', 'MENU', '/permission/app', 'permission/app/index', 'admin:app:view', 'Grid', 1, 41, 1, 0),
    (162, 1, 160, 'admin_permission_resource', '资源权限', 'MENU', '/permission/resource', 'permission/resource/index', 'admin:permission:view', 'Key', 1, 42, 1, 0),
    (163, 1, 160, 'admin_permission_data_scope', '数据权限', 'MENU', '/permission/data-scope', 'permission/data-scope/index', 'admin:data-scope:view', 'Connection', 1, 43, 1, 0),
    (164, 1, 160, 'admin_permission_role_grant', '角色授权', 'MENU', '/permission/role-grant', 'permission/role-grant/index', 'admin:role-grant:view', 'Unlock', 1, 44, 1, 0),
    (180, 1, 0, 'admin_security_center', '安全中心', 'CATALOG', '/security', NULL, 'admin:security:view', 'Lock', 1, 50, 1, 0),
    (181, 1, 180, 'admin_security_login_session', '登录会话', 'MENU', '/security/login-session', 'security/login-session/index', 'admin:login-session:view', 'Monitor', 1, 51, 1, 0),
    (182, 1, 180, 'admin_security_jwt_key', 'JWT密钥管理', 'MENU', '/security/jwt-key', 'security/jwt-key/index', 'admin:jwt-key:view', 'Key', 1, 52, 1, 0),
    (183, 1, 180, 'admin_security_api_access', 'API访问控制', 'MENU', '/security/api-access', 'security/api-access/index', 'admin:api-access:view', 'Monitor', 1, 53, 1, 0),
    (184, 1, 180, 'admin_security_operation_audit', '操作审计', 'MENU', '/security/operation-audit', 'security/operation-audit/index', 'admin:operation-audit:view', 'DocumentChecked', 1, 54, 1, 0);

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
    (143, 1, 143, 'admin:region-currency:view', '地区币种配置查看', 'MENU', 'GET', '/base/region-currency', 1, 0),
    (160, 1, 160, 'admin:permission-center:view', '权限中心目录查看', 'MENU', 'GET', '/permission/**', 1, 0),
    (161, 1, 161, 'admin:app:view', '应用管理查看', 'MENU', 'GET', '/permission/app', 1, 0),
    (162, 1, 162, 'admin:permission:view', '资源权限查看', 'MENU', 'GET', '/permission/resource', 1, 0),
    (163, 1, 163, 'admin:data-scope:view', '数据权限查看', 'MENU', 'GET', '/permission/data-scope', 1, 0),
    (164, 1, 164, 'admin:role-grant:view', '角色授权查看', 'MENU', 'GET', '/permission/role-grant', 1, 0),
    (180, 1, 180, 'admin:security:view', '安全中心目录查看', 'MENU', 'GET', '/security/**', 1, 0),
    (181, 1, 181, 'admin:login-session:view', '登录会话查看', 'MENU', 'GET', '/security/login-session', 1, 0),
    (182, 1, 182, 'admin:jwt-key:view', 'JWT密钥管理查看', 'MENU', 'GET', '/security/jwt-key', 1, 0),
    (183, 1, 183, 'admin:api-access:view', 'API访问控制查看', 'MENU', 'GET', '/security/api-access', 1, 0),
    (184, 1, 184, 'admin:operation-audit:view', '操作审计查看', 'MENU', 'GET', '/security/operation-audit', 1, 0);

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
    (240, 1, 0, 'admin_base_catalog_v3', '基础数据', 'CATALOG', '/base', NULL, NULL, 'DataLine', 1, 30, 1, 0),
    (241, 1, 240, 'admin_base_country_v3', '国家/地区', 'MENU', '/base/country', 'base/country', 'base:country:list', 'Location', 1, 31, 1, 0),
    (242, 1, 240, 'admin_base_currency_v3', '币种管理', 'MENU', '/base/currency', 'base/currency', 'base:currency:list', 'Coin', 1, 32, 1, 0),
    (243, 1, 240, 'admin_base_region_currency_v3', '地区币种配置', 'MENU', '/base/region-currency', 'base/region-currency', 'base:region-currency:list', 'Connection', 1, 33, 1, 0),
    (250, 1, 0, 'admin_permission_catalog_v3', '权限中心', 'CATALOG', '/permission', NULL, NULL, 'Key', 1, 40, 1, 0),
    (251, 1, 250, 'admin_permission_app_v3', '应用权限', 'MENU', '/permission/app', 'permission/app', 'permission:app:list', 'Key', 1, 41, 1, 0),
    (252, 1, 250, 'admin_permission_data_scope_v3', '数据权限', 'MENU', '/permission/data-scope', 'permission/data-scope', 'permission:data-scope:list', 'Connection', 1, 42, 1, 0),
    (260, 1, 0, 'admin_security_catalog_v3', '安全中心', 'CATALOG', '/security', NULL, NULL, 'Lock', 1, 50, 1, 0),
    (261, 1, 260, 'admin_security_session_v3', '会话管理', 'MENU', '/security/session', 'security/session', 'security:session:list', 'Monitor', 1, 51, 1, 0),
    (262, 1, 260, 'admin_security_api_security_v3', '密钥与 API 安全', 'MENU', '/security/api-security', 'security/api-security', 'security:jwt-key:list', 'Lock', 1, 52, 1, 0);

INSERT IGNORE INTO sys_permission (id, app_id, menu_id, permission_code, permission_name, permission_type, resource_method, resource_path, status, deleted)
VALUES
    (200, 1, 201, 'dashboard:view', '工作台查看', 'MENU', 'GET', '/dashboard', 1, 0),
    (211, 1, 211, 'system:user:list', '用户管理查询', 'MENU', 'GET', '/system/user', 1, 0),
    (212, 1, 211, 'system:user:add', '用户新增', 'BUTTON', 'POST', '/admin/system/users/create', 1, 0),
    (213, 1, 211, 'system:user:edit', '用户编辑', 'BUTTON', '*', '/admin/system/users/**', 1, 0),
    (214, 1, 211, 'system:user:delete', '用户删除', 'BUTTON', 'DELETE', '/admin/system/users/**', 1, 0),
    (215, 1, 211, 'system:user:reset-password', '用户重置密码', 'BUTTON', '*', '/admin/system/users/**/password', 1, 0),
    (216, 1, 211, 'system:user:assign-role', '用户分配角色', 'BUTTON', '*', '/admin/system/users/**/roles', 1, 0),
    (221, 1, 212, 'system:role:list', '角色管理查询', 'MENU', 'GET', '/system/role', 1, 0),
    (222, 1, 212, 'system:role:add', '角色新增', 'BUTTON', '*', '/admin/system/roles/**', 1, 0),
    (223, 1, 212, 'system:role:edit', '角色编辑', 'BUTTON', '*', '/admin/system/roles/**', 1, 0),
    (224, 1, 212, 'system:role:delete', '角色删除', 'BUTTON', '*', '/admin/system/roles/**', 1, 0),
    (225, 1, 212, 'system:role:assign-menu', '角色分配菜单', 'BUTTON', '*', '/admin/system/roles/**/menus', 1, 0),
    (226, 1, 212, 'system:role:assign-permission', '角色分配权限', 'BUTTON', '*', '/admin/system/roles/**/permissions', 1, 0),
    (227, 1, 212, 'system:role:data-scope', '角色数据范围', 'BUTTON', '*', '/admin/system/roles/**/data-scope', 1, 0),
    (231, 1, 213, 'system:menu:list', '菜单管理查询', 'MENU', 'GET', '/system/menu', 1, 0),
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
    (254, 1, 215, 'system:dict:delete', '字典删除', 'BUTTON', 'DELETE', '/admin/system/dicts/**', 1, 0),
    (255, 1, 215, 'system:config:list', '参数查询', 'BUTTON', '*', '/admin/system/configs/**', 1, 0),
    (256, 1, 215, 'system:config:add', '参数新增', 'BUTTON', 'POST', '/admin/system/configs', 1, 0),
    (257, 1, 215, 'system:config:edit', '参数编辑', 'BUTTON', 'POST', '/admin/system/configs', 1, 0),
    (258, 1, 215, 'system:config:delete', '参数删除', 'BUTTON', 'DELETE', '/admin/system/configs/**', 1, 0),
    (261, 1, 216, 'system:login-log:list', '登录日志查询', 'MENU', '*', '/admin/auth/**', 1, 0),
    (262, 1, 216, 'system:oper-log:list', '操作日志查询', 'BUTTON', '*', '/admin/system/oper-logs/**', 1, 0),
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
    (404, 1, 241, 'base:country:delete', '国家地区删除', 'BUTTON', '*', '/admin/base/countries/**', 1, 0),
    (405, 1, 241, 'base:country:import', '国家地区导入', 'BUTTON', '*', '/admin/base/countries/import', 1, 0),
    (406, 1, 241, 'base:country:export', '国家地区导出', 'BUTTON', '*', '/admin/base/countries/export', 1, 0),
    (411, 1, 242, 'base:currency:list', '币种查询', 'MENU', 'GET', '/base/currency', 1, 0),
    (412, 1, 242, 'base:currency:add', '币种新增', 'BUTTON', '*', '/admin/base/currencies/**', 1, 0),
    (413, 1, 242, 'base:currency:edit', '币种编辑', 'BUTTON', '*', '/admin/base/currencies/**', 1, 0),
    (414, 1, 242, 'base:currency:delete', '币种删除', 'BUTTON', '*', '/admin/base/currencies/**', 1, 0),
    (415, 1, 242, 'base:currency:import', '币种导入', 'BUTTON', '*', '/admin/base/currencies/import', 1, 0),
    (416, 1, 242, 'base:currency:export', '币种导出', 'BUTTON', '*', '/admin/base/currencies/export', 1, 0),
    (421, 1, 243, 'base:region-currency:list', '地区币种配置查询', 'MENU', 'GET', '/base/region-currency', 1, 0),
    (422, 1, 243, 'base:region-currency:add', '地区币种配置新增', 'BUTTON', '*', '/admin/base/region-currencies/**', 1, 0),
    (423, 1, 243, 'base:region-currency:edit', '地区币种配置编辑', 'BUTTON', '*', '/admin/base/region-currencies/**', 1, 0),
    (424, 1, 243, 'base:region-currency:delete', '地区币种配置删除', 'BUTTON', '*', '/admin/base/region-currencies/**', 1, 0),
    (501, 1, 251, 'permission:app:list', '应用权限查询', 'MENU', 'GET', '/permission/app', 1, 0),
    (502, 1, 251, 'permission:app:add', '应用权限新增', 'BUTTON', '*', '/admin/permissions/apps/**', 1, 0),
    (503, 1, 251, 'permission:app:edit', '应用权限编辑', 'BUTTON', '*', '/admin/permissions/apps/**', 1, 0),
    (504, 1, 251, 'permission:app:delete', '应用权限删除', 'BUTTON', '*', '/admin/permissions/apps/**', 1, 0),
    (511, 1, 251, 'permission:resource:list', '资源权限查询', 'BUTTON', '*', '/admin/permissions/resources/**', 1, 0),
    (512, 1, 251, 'permission:resource:add', '资源权限新增', 'BUTTON', '*', '/admin/permissions/resources/**', 1, 0),
    (513, 1, 251, 'permission:resource:edit', '资源权限编辑', 'BUTTON', '*', '/admin/permissions/resources/**', 1, 0),
    (514, 1, 251, 'permission:resource:delete', '资源权限删除', 'BUTTON', '*', '/admin/permissions/resources/**', 1, 0),
    (521, 1, 251, 'permission:role-auth:list', '角色授权查询', 'BUTTON', '*', '/admin/permissions/role-auth/**', 1, 0),
    (522, 1, 251, 'permission:role-auth:edit', '角色授权编辑', 'BUTTON', '*', '/admin/permissions/role-auth/**', 1, 0),
    (531, 1, 252, 'permission:data-scope:list', '数据权限查询', 'MENU', 'GET', '/permission/data-scope', 1, 0),
    (532, 1, 252, 'permission:data-scope:add', '数据权限新增', 'BUTTON', '*', '/admin/permissions/data-scopes/**', 1, 0),
    (533, 1, 252, 'permission:data-scope:edit', '数据权限编辑', 'BUTTON', '*', '/admin/permissions/data-scopes/**', 1, 0),
    (534, 1, 252, 'permission:data-scope:delete', '数据权限删除', 'BUTTON', '*', '/admin/permissions/data-scopes/**', 1, 0),
    (601, 1, 261, 'security:session:list', '会话查询', 'MENU', 'GET', '/security/session', 1, 0),
    (602, 1, 261, 'security:session:kickout', '会话踢出', 'BUTTON', '*', '/admin/security/sessions/**/kickout', 1, 0),
    (611, 1, 262, 'security:jwt-key:list', 'JWT 密钥查询', 'MENU', 'GET', '/security/api-security', 1, 0),
    (612, 1, 262, 'security:jwt-key:rotate', 'JWT 密钥轮换', 'BUTTON', '*', '/admin/security/jwt-keys/**/rotate', 1, 0),
    (621, 1, 262, 'security:api-access:list', 'API 访问控制查询', 'BUTTON', '*', '/admin/security/api-access/**', 1, 0),
    (622, 1, 262, 'security:api-access:add', 'API 访问控制新增', 'BUTTON', '*', '/admin/security/api-access/**', 1, 0),
    (623, 1, 262, 'security:api-access:edit', 'API 访问控制编辑', 'BUTTON', '*', '/admin/security/api-access/**', 1, 0),
    (624, 1, 262, 'security:api-access:delete', 'API 访问控制删除', 'BUTTON', '*', '/admin/security/api-access/**', 1, 0),
    (631, 1, 262, 'security:audit:list', '安全审计查询', 'BUTTON', '*', '/admin/security/audit/**', 1, 0);

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
SET resource_method = 'POST',
    resource_path = '/admin/system/users/create'
WHERE app_id = 1
  AND permission_code = 'system:user:add'
  AND deleted = 0;

UPDATE sys_permission
SET resource_method = 'POST',
    resource_path = '/admin/system/users/roles%'
WHERE app_id = 1
  AND permission_code = 'system:user:assign-role'
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
  AND m.menu_code IN ('merchant_dashboard', 'merchant_info', 'merchant_store', 'merchant_transaction',
                      'merchant_order', 'merchant_refund', 'merchant_settlement', 'merchant_account',
                      'merchant_oper_log');

INSERT IGNORE INTO sys_role_menu (app_id, role_id, menu_id, deleted)
SELECT r.app_id, r.id, m.id, 0
FROM sys_role r
JOIN sys_menu m ON m.app_id = r.app_id AND m.deleted = 0
WHERE r.app_id = 2
  AND r.deleted = 0
  AND r.role_code LIKE 'MERCHANT_VIEWER\_%'
  AND m.permission_code IN ('merchant:dashboard:view', 'merchant:info:view', 'merchant:transaction:view',
                            'merchant:order:view', 'merchant:settlement:view', 'merchant:account:view',
                            'merchant:oper-log:view');

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
  AND p.permission_code IN ('merchant:dashboard:view', 'merchant:info:view', 'merchant:store:view',
                            'merchant:store:manage', 'merchant:transaction:view', 'merchant:order:view',
                            'merchant:refund:apply', 'merchant:settlement:view', 'merchant:account:view',
                            'merchant:oper-log:view');

INSERT IGNORE INTO sys_role_permission (app_id, role_id, permission_id, deleted)
SELECT r.app_id, r.id, p.id, 0
FROM sys_role r
JOIN sys_permission p ON p.app_id = r.app_id AND p.deleted = 0
WHERE r.app_id = 2
  AND r.deleted = 0
  AND r.role_code LIKE 'MERCHANT_VIEWER\_%'
  AND p.permission_code IN ('merchant:dashboard:view', 'merchant:info:view', 'merchant:transaction:view',
                            'merchant:order:view', 'merchant:settlement:view', 'merchant:account:view',
                            'merchant:oper-log:view');

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
