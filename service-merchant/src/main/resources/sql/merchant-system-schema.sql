SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

CREATE TABLE IF NOT EXISTS sys_oper_log (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    trace_id VARCHAR(64) NULL COMMENT '链路追踪ID',
    request_id VARCHAR(64) NULL COMMENT '请求ID',
    message_id VARCHAR(64) NULL COMMENT 'MQ消息唯一标识',
    idempotent_key VARCHAR(255) NULL COMMENT '消费幂等键',
    system_code VARCHAR(32) NULL COMMENT '系统编码，区分 ADMIN 和 MERCHANT',
    merchant_id VARCHAR(32) NULL COMMENT '商户号',
    module_name VARCHAR(100) NULL COMMENT '模块名称',
    operation_name VARCHAR(100) NULL COMMENT '操作名称',
    business_type TINYINT NULL COMMENT '业务类型：1新增，2修改，3删除，4查询，5导出，6审核，7冻结，8解冻',
    method_name VARCHAR(255) NULL COMMENT '后端方法名称',
    request_method VARCHAR(20) NULL COMMENT '请求方式：GET、POST、PUT、DELETE',
    operator_type TINYINT NOT NULL DEFAULT 2 COMMENT '操作人类别：1后台用户，2商户用户，3系统任务',
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商户管理系统操作日志表';

SET FOREIGN_KEY_CHECKS = 1;
