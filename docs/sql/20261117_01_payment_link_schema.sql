-- 收单支付链接，首批固定金额、单次成功。先在 dev/test 预检执行，不自动执行生产迁移。
-- 前置：当前 schema 与 payment_checkout_* 同库，CHARSET/COLLATION 与既有订单身份保持一致。
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS payment_link (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '物理主键' ,
    -- 新链接编号为 L 加系统 22 位数字，保留 32 位宽度兼容旧编号；关联表保持同类型。
    link_id VARCHAR(32) NOT NULL COMMENT '支付链接编号，兼容历史编号格式' ,
    merchant_id VARCHAR(64) NOT NULL COMMENT '平台商户号' ,
    merchant_order_no VARCHAR(64) NOT NULL COMMENT '商户侧订单号，在商户范围内唯一' ,
    -- 名称选填，未填写保存空串，管理界面按商户订单号展示，不新增冗余展示名。
    link_name VARCHAR(120) NOT NULL COMMENT '支付链接名称，未填写时保存空串并按商户订单号展示' ,
    description VARCHAR(500) NULL COMMENT '业务说明' ,
    currency CHAR(3) NOT NULL COMMENT '金额对应的三位字母币种代码' ,
    amount DECIMAL(24,8) NOT NULL COMMENT '支付链接固定金额，以币种主单位计价' ,
    currency_exponent INT NOT NULL COMMENT '币种小数位，用于金额精度校验与展示' ,
    locale VARCHAR(12) NOT NULL COMMENT '消息或页面语言标识' ,
    allowed_brands_json TEXT NOT NULL COMMENT '允许的支付卡品牌列表，使用序列化数组保存' ,
    merchant_display_name VARCHAR(256) NOT NULL COMMENT '支付页展示的商户名称快照' ,
    notify_url VARCHAR(512) NULL COMMENT '商户交易结果通知地址' ,
    redirect_url VARCHAR(512) NULL COMMENT '支付完成后的页面跳转地址' ,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '支付链接状态：ACTIVE有效，DISABLED停用，EXPIRED过期，COMPLETED已完成' ,
    expire_time DATETIME(3) NOT NULL COMMENT '支付链接有效期截止时间' ,
    create_request_id VARCHAR(80) NOT NULL COMMENT '创建支付链接的请求幂等编号' ,
    request_fingerprint CHAR(64) NOT NULL COMMENT '请求参数指纹，用于防止同一幂等编号对应不同请求' ,
    created_app_code VARCHAR(16) NOT NULL COMMENT '创建来源应用编码' ,
    created_by VARCHAR(128) NOT NULL COMMENT '创建操作人标识' ,
    disabled_by VARCHAR(128) NULL COMMENT '停用操作人标识' ,
    disable_request_id VARCHAR(80) NULL COMMENT '停用请求幂等编号' ,
    disable_reason VARCHAR(500) NULL COMMENT '停用原因' ,
    disabled_time DATETIME(3) NULL COMMENT '停用成功时间' ,
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号，防止并发覆盖' ,
    create_time DATETIME(3) NOT NULL COMMENT '创建时间' ,
    update_time DATETIME(3) NOT NULL COMMENT '更新时间' ,
    PRIMARY KEY (id),
    UNIQUE KEY uk_payment_link_id (link_id),
    UNIQUE KEY uk_payment_link_merchant_order (merchant_id, merchant_order_no),
    UNIQUE KEY uk_payment_link_create_request (merchant_id, create_request_id),
    KEY idx_payment_link_merchant_created (merchant_id, create_time, id),
    KEY idx_payment_link_created (create_time, id),
    KEY idx_payment_link_order_created (merchant_order_no, create_time, id),
    KEY idx_payment_link_status_expire (status, expire_time, id),
    CONSTRAINT ck_payment_link_positive_amount CHECK (amount > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='固定金额、单次成功的收单支付链接';

CREATE TABLE IF NOT EXISTS payment_link_access (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '物理主键' ,
    -- 新链接编号为 L 加系统 22 位数字，保留 32 位宽度兼容旧编号；关联表保持同类型。
    link_id VARCHAR(32) NOT NULL COMMENT '支付链接编号，兼容历史编号格式' ,
    key_version VARCHAR(32) NOT NULL COMMENT '访问令牌签名密钥版本号，不保存密钥原文' ,
    token_hash CHAR(64) NOT NULL COMMENT '访问令牌摘要，不保存令牌原文' ,
    create_time DATETIME(3) NOT NULL COMMENT '创建时间' ,
    PRIMARY KEY (id),
    UNIQUE KEY uk_payment_link_access_link (link_id),
    UNIQUE KEY uk_payment_link_access_hash (token_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='链接访问摘要，不保存令牌原文';

CREATE TABLE IF NOT EXISTS payment_link_collection (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '物理主键' ,
    collection_id VARCHAR(64) NOT NULL COMMENT '支付链接唯一收款关联编号' ,
    -- 新链接编号为 L 加系统 22 位数字，保留 32 位宽度兼容旧编号；关联表保持同类型。
    link_id VARCHAR(32) NOT NULL COMMENT '支付链接编号，兼容历史编号格式' ,
    merchant_id VARCHAR(64) NOT NULL COMMENT '平台商户号' ,
    merchant_order_no VARCHAR(64) NOT NULL COMMENT '商户侧订单号，在商户范围内唯一' ,
    submission_deadline DATETIME(3) NOT NULL COMMENT '本次收款允许提交支付的截止时间' ,
    create_time DATETIME(3) NOT NULL COMMENT '创建时间' ,
    PRIMARY KEY (id),
    UNIQUE KEY uk_payment_link_collection_id (collection_id),
    UNIQUE KEY uk_payment_link_collection_link (link_id),
    UNIQUE KEY uk_payment_link_collection_order (merchant_id, merchant_order_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='链接到现有核心订单的唯一收款关联';

CREATE TABLE IF NOT EXISTS payment_link_checkout_binding (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '物理主键' ,
    -- 新链接编号为 L 加系统 22 位数字，保留 32 位宽度兼容旧编号；关联表保持同类型。
    link_id VARCHAR(32) NOT NULL COMMENT '支付链接编号，兼容历史编号格式' ,
    collection_id VARCHAR(64) NOT NULL COMMENT '支付链接唯一收款关联编号' ,
    checkout_session_id VARCHAR(64) NOT NULL COMMENT '关联收银台会话编号' ,
    create_time DATETIME(3) NOT NULL COMMENT '创建时间' ,
    PRIMARY KEY (id),
    UNIQUE KEY uk_payment_link_binding_link (link_id),
    UNIQUE KEY uk_payment_link_binding_collection (collection_id),
    UNIQUE KEY uk_payment_link_binding_session (checkout_session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='链接会话唯一绑定，失败重试不新建独立会话';

CREATE TABLE IF NOT EXISTS payment_link_event (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '物理主键' ,
    event_id VARCHAR(64) NOT NULL COMMENT '支付链接审计事件编号' ,
    -- 新链接编号为 L 加系统 22 位数字，保留 32 位宽度兼容旧编号；关联表保持同类型。
    link_id VARCHAR(32) NOT NULL COMMENT '支付链接编号，兼容历史编号格式' ,
    event_type VARCHAR(32) NOT NULL COMMENT '支付链接审计事件类型' ,
    actor_app_code VARCHAR(16) NOT NULL COMMENT '操作来源应用编码' ,
    actor_id VARCHAR(128) NOT NULL COMMENT '操作人标识' ,
    reason VARCHAR(500) NULL COMMENT '操作原因或业务说明' ,
    create_time DATETIME(3) NOT NULL COMMENT '创建时间' ,
    PRIMARY KEY (id),
    UNIQUE KEY uk_payment_link_event_id (event_id),
    KEY idx_payment_link_event_link (link_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='链接状态事务内脱敏审计';

-- 已有验收版本可能已建表；补齐平台跨商户按创建时间查询的索引，不重复添加。
SET @payment_link_created_index_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.statistics
           WHERE table_schema = DATABASE() AND table_name = 'payment_link'
             AND index_name = 'idx_payment_link_created'),
    'SELECT 1',
    'ALTER TABLE payment_link ADD INDEX idx_payment_link_created (create_time, id)'
);
PREPARE payment_link_created_index_stmt FROM @payment_link_created_index_sql;
EXECUTE payment_link_created_index_stmt;
DEALLOCATE PREPARE payment_link_created_index_stmt;

-- 管理端未指定商户时，商户订单号精确查找不能依赖 merchant_id 为首列的唯一键。
SET @payment_link_order_index_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.statistics
           WHERE table_schema = DATABASE() AND table_name = 'payment_link'
             AND index_name = 'idx_payment_link_order_created'),
    'SELECT 1',
    'ALTER TABLE payment_link ADD INDEX idx_payment_link_order_created (merchant_order_no, create_time, id)'
);
PREPARE payment_link_order_index_stmt FROM @payment_link_order_index_sql;
EXECUTE payment_link_order_index_stmt;
DEALLOCATE PREPARE payment_link_order_index_stmt;

-- 后置核对：五表引擎、唯一索引及 payment_checkout_* 路由必须验证后才设置 storage-ready=true。
SELECT table_name, engine FROM information_schema.tables
WHERE table_schema = DATABASE() AND table_name IN
    ('payment_link','payment_link_access','payment_link_collection','payment_link_checkout_binding','payment_link_event');
-- 回退采用开关隔离，不 DROP 有业务事实的表；storage-ready 保持 true，enabled=false 阻止旧会话新提交。
