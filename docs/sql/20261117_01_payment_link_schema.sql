-- 收单支付链接，首批固定金额、单次成功。先在 dev/test 预检执行，不自动执行生产迁移。
-- 前置：当前 schema 与 payment_checkout_* 同库，CHARSET/COLLATION 与既有订单身份保持一致。
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS payment_link (
    id BIGINT NOT NULL AUTO_INCREMENT,
    -- 新链接编号为 L 加系统 22 位数字，保留 32 位宽度兼容旧编号；关联表保持同类型。
    link_id VARCHAR(32) NOT NULL,
    merchant_id VARCHAR(64) NOT NULL,
    merchant_order_no VARCHAR(64) NOT NULL,
    -- 名称选填，未填写保存空串，管理界面按商户订单号展示，不新增冗余展示名。
    link_name VARCHAR(120) NOT NULL,
    description VARCHAR(500) NULL,
    currency CHAR(3) NOT NULL,
    amount DECIMAL(24,8) NOT NULL,
    currency_exponent INT NOT NULL,
    locale VARCHAR(12) NOT NULL,
    allowed_brands_json TEXT NOT NULL,
    merchant_display_name VARCHAR(256) NOT NULL,
    notify_url VARCHAR(512) NULL,
    redirect_url VARCHAR(512) NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    expire_time DATETIME(3) NOT NULL,
    create_request_id VARCHAR(80) NOT NULL,
    request_fingerprint CHAR(64) NOT NULL,
    created_app_code VARCHAR(16) NOT NULL,
    created_by VARCHAR(128) NOT NULL,
    disabled_by VARCHAR(128) NULL,
    disable_request_id VARCHAR(80) NULL,
    disable_reason VARCHAR(500) NULL,
    disabled_time DATETIME(3) NULL,
    version INT NOT NULL DEFAULT 0,
    create_time DATETIME(3) NOT NULL,
    update_time DATETIME(3) NOT NULL,
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
    id BIGINT NOT NULL AUTO_INCREMENT,
    -- 新链接编号为 L 加系统 22 位数字，保留 32 位宽度兼容旧编号；关联表保持同类型。
    link_id VARCHAR(32) NOT NULL,
    key_version VARCHAR(32) NOT NULL,
    token_hash CHAR(64) NOT NULL,
    create_time DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_payment_link_access_link (link_id),
    UNIQUE KEY uk_payment_link_access_hash (token_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='链接访问摘要，不保存令牌原文';

CREATE TABLE IF NOT EXISTS payment_link_collection (
    id BIGINT NOT NULL AUTO_INCREMENT,
    collection_id VARCHAR(64) NOT NULL,
    -- 新链接编号为 L 加系统 22 位数字，保留 32 位宽度兼容旧编号；关联表保持同类型。
    link_id VARCHAR(32) NOT NULL,
    merchant_id VARCHAR(64) NOT NULL,
    merchant_order_no VARCHAR(64) NOT NULL,
    submission_deadline DATETIME(3) NOT NULL,
    create_time DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_payment_link_collection_id (collection_id),
    UNIQUE KEY uk_payment_link_collection_link (link_id),
    UNIQUE KEY uk_payment_link_collection_order (merchant_id, merchant_order_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='链接到现有核心订单的唯一收款关联';

CREATE TABLE IF NOT EXISTS payment_link_checkout_binding (
    id BIGINT NOT NULL AUTO_INCREMENT,
    -- 新链接编号为 L 加系统 22 位数字，保留 32 位宽度兼容旧编号；关联表保持同类型。
    link_id VARCHAR(32) NOT NULL,
    collection_id VARCHAR(64) NOT NULL,
    checkout_session_id VARCHAR(64) NOT NULL,
    create_time DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_payment_link_binding_link (link_id),
    UNIQUE KEY uk_payment_link_binding_collection (collection_id),
    UNIQUE KEY uk_payment_link_binding_session (checkout_session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='链接会话唯一绑定，失败重试不新建独立会话';

CREATE TABLE IF NOT EXISTS payment_link_event (
    id BIGINT NOT NULL AUTO_INCREMENT,
    event_id VARCHAR(64) NOT NULL,
    -- 新链接编号为 L 加系统 22 位数字，保留 32 位宽度兼容旧编号；关联表保持同类型。
    link_id VARCHAR(32) NOT NULL,
    event_type VARCHAR(32) NOT NULL,
    actor_app_code VARCHAR(16) NOT NULL,
    actor_id VARCHAR(128) NOT NULL,
    reason VARCHAR(500) NULL,
    create_time DATETIME(3) NOT NULL,
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
