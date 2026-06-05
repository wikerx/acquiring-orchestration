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

SET FOREIGN_KEY_CHECKS = 1;
