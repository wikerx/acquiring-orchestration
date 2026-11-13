-- 支付路由阶段1A：管理系统配置中心迁移。
-- 仅创建配置、菜单和权限，不修改 service-payment 的真实选路行为。

CREATE TABLE IF NOT EXISTS payment_route_rule (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    rule_code VARCHAR(64) NOT NULL COMMENT '稳定路由规则编码',
    rule_name VARCHAR(128) NOT NULL COMMENT '路由规则名称',
    rule_type VARCHAR(32) NOT NULL COMMENT '规则类型：FIXED、PRIORITY、WEIGHT、HISTORY_AFFINITY',
    scope_type VARCHAR(32) NOT NULL COMMENT '作用范围：GLOBAL全局、MERCHANT商户',
    merchant_id VARCHAR(32) NULL COMMENT '商户号，MERCHANT范围时必填',
    business_type VARCHAR(32) NOT NULL DEFAULT 'ACQUIRING' COMMENT '业务类型；阶段1A仅支持ACQUIRING',
    payment_method VARCHAR(64) NULL COMMENT '支付方式，空表示不限',
    transaction_type VARCHAR(64) NULL COMMENT '交易类型，空表示不限',
    card_brand VARCHAR(64) NULL COMMENT '卡品牌，空表示不限',
    label_currency CHAR(3) NULL COMMENT '商户标签币种，空表示不限',
    issuer_country_scope VARCHAR(1024) NOT NULL DEFAULT 'ALL' COMMENT '发卡国家范围，ALL或ISO Alpha-2逗号分隔',
    billing_country_scope VARCHAR(1024) NOT NULL DEFAULT 'ALL' COMMENT '账单国家范围，ALL或ISO Alpha-2逗号分隔',
    amount_min DECIMAL(24,8) NULL COMMENT '标签金额下限，包含边界',
    amount_max DECIMAL(24,8) NULL COMMENT '标签金额上限，包含边界',
    rule_priority INT NOT NULL COMMENT '第一层规则优先级，数值越小优先级越高',
    unavailable_policy VARCHAR(32) NOT NULL COMMENT '候选不可用策略：FALLBACK回退、REJECT拒绝',
    rule_status VARCHAR(32) NOT NULL DEFAULT 'DRAFT' COMMENT '生命周期：DRAFT、ENABLED、DISABLED',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    remark VARCHAR(500) NULL COMMENT '运营备注',
    published_by VARCHAR(64) NULL COMMENT '最近发布人',
    published_time DATETIME(3) NULL COMMENT '最近发布时间',
    disabled_by VARCHAR(64) NULL COMMENT '最近停用人',
    disabled_time DATETIME(3) NULL COMMENT '最近停用时间',
    create_by VARCHAR(64) NULL COMMENT '创建人',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_by VARCHAR(64) NULL COMMENT '更新人',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted BIGINT NOT NULL DEFAULT 0 COMMENT '删除标识：0未删除，大于0为删除记录ID',
    PRIMARY KEY (id),
    UNIQUE KEY uk_payment_route_rule_code_deleted (rule_code, deleted),
    KEY idx_payment_route_rule_status_priority (rule_status, business_type, rule_priority, deleted),
    KEY idx_payment_route_rule_merchant (scope_type, merchant_id, rule_status, deleted),
    KEY idx_payment_route_rule_dimension (payment_method, transaction_type, card_brand, label_currency, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='支付路由规则配置表；阶段1A不接入支付链路';

CREATE TABLE IF NOT EXISTS payment_route_method (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    rule_id BIGINT NOT NULL COMMENT '支付路由规则ID',
    payment_method VARCHAR(64) NOT NULL COMMENT '规则适用的收单支付方式',
    create_by VARCHAR(64) NULL COMMENT '创建人',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_by VARCHAR(64) NULL COMMENT '更新人',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted BIGINT NOT NULL DEFAULT 0 COMMENT '删除标识：0未删除，大于0为删除记录ID',
    PRIMARY KEY (id),
    UNIQUE KEY uk_payment_route_method_rule_method_deleted (rule_id, payment_method, deleted),
    KEY idx_payment_route_method_method_rule (payment_method, rule_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='支付路由规则适用支付方式表';

CREATE TABLE IF NOT EXISTS payment_route_transaction_type (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    rule_id BIGINT NOT NULL COMMENT '支付路由规则ID',
    transaction_type VARCHAR(64) NOT NULL COMMENT '规则适用的收单交易类型',
    create_by VARCHAR(64) NULL COMMENT '创建人',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_by VARCHAR(64) NULL COMMENT '更新人',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted BIGINT NOT NULL DEFAULT 0 COMMENT '删除标识：0未删除，大于0为删除记录ID',
    PRIMARY KEY (id),
    UNIQUE KEY uk_payment_route_transaction_rule_type_deleted (rule_id, transaction_type, deleted),
    KEY idx_payment_route_transaction_type_rule (transaction_type, rule_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='支付路由规则适用交易类型表；无有效记录表示全部交易类型';

CREATE TABLE IF NOT EXISTS payment_route_strategy (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    rule_id BIGINT NOT NULL COMMENT '支付路由规则ID',
    strategy_type VARCHAR(32) NOT NULL COMMENT '类型专属配置类型，与规则类型一致',
    strategy_priority INT NOT NULL DEFAULT 100 COMMENT '首版结构兼容字段，不再作为业务优先级',
    history_lookback_months INT NULL COMMENT '历史成功MID路由回看月数，其他类型为空',
    strategy_status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0停用，1启用',
    create_by VARCHAR(64) NULL COMMENT '创建人',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_by VARCHAR(64) NULL COMMENT '更新人',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted BIGINT NOT NULL DEFAULT 0 COMMENT '删除标识：0未删除，大于0为删除记录ID',
    PRIMARY KEY (id),
    UNIQUE KEY uk_payment_route_strategy_rule_type_deleted (rule_id, strategy_type, deleted),
    KEY idx_payment_route_strategy_rule_status (rule_id, strategy_status, strategy_priority, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='支付路由策略配置表';

CREATE TABLE IF NOT EXISTS payment_route_candidate (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    rule_id BIGINT NOT NULL COMMENT '支付路由规则ID',
    payment_method VARCHAR(64) NOT NULL COMMENT '候选所属收单支付方式',
    channel_id BIGINT NOT NULL COMMENT '渠道ID，关联channel_info.id',
    mid_config_id BIGINT NOT NULL COMMENT '渠道MID配置ID，关联channel_mid_config.id',
    candidate_priority INT NOT NULL COMMENT '候选稳定顺序；仅PRIORITY类型用于选路优先级',
    candidate_weight DECIMAL(5,2) NULL COMMENT 'WEIGHT类型候选权重，按支付方式合计100.00',
    candidate_status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0停用，1启用',
    create_by VARCHAR(64) NULL COMMENT '创建人',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_by VARCHAR(64) NULL COMMENT '更新人',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted BIGINT NOT NULL DEFAULT 0 COMMENT '删除标识：0未删除，大于0为删除记录ID',
    PRIMARY KEY (id),
    UNIQUE KEY uk_payment_route_candidate_mid_deleted (rule_id, payment_method, mid_config_id, deleted),
    UNIQUE KEY uk_payment_route_candidate_priority_deleted (rule_id, payment_method, candidate_priority, deleted),
    KEY idx_payment_route_candidate_method (payment_method, candidate_status, deleted),
    KEY idx_payment_route_candidate_channel (channel_id, candidate_status, deleted),
    KEY idx_payment_route_candidate_mid (mid_config_id, candidate_status, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='支付路由候选MID配置表';

-- 兼容已经执行过阶段1A首版迁移的环境：历史候选继承规则支付方式，无法识别时标记为ALL并阻止直接发布。
DROP PROCEDURE IF EXISTS migrate_payment_route_candidate_method;
DELIMITER $$
CREATE PROCEDURE migrate_payment_route_candidate_method()
BEGIN
    DECLARE mid_index_columns VARCHAR(512) DEFAULT NULL;
    DECLARE priority_index_columns VARCHAR(512) DEFAULT NULL;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'payment_route_candidate'
          AND column_name = 'payment_method'
    ) THEN
        ALTER TABLE payment_route_candidate
            ADD COLUMN payment_method VARCHAR(64) NULL COMMENT '候选所属收单支付方式' AFTER rule_id;

    END IF;

    -- 兼容列已创建但回填或非空约束尚未完成的中断迁移环境。
    UPDATE payment_route_candidate candidate
    JOIN payment_route_rule rule_record ON rule_record.id = candidate.rule_id
    SET candidate.payment_method = COALESCE(NULLIF(rule_record.payment_method, ''), 'ALL')
    WHERE candidate.payment_method IS NULL OR candidate.payment_method = '';

    ALTER TABLE payment_route_candidate
        MODIFY COLUMN payment_method VARCHAR(64) NOT NULL COMMENT '候选所属收单支付方式';

    SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index)
    INTO mid_index_columns
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'payment_route_candidate'
      AND index_name = 'uk_payment_route_candidate_mid_deleted';

    IF mid_index_columns IS NOT NULL
       AND mid_index_columns <> 'rule_id,payment_method,mid_config_id,deleted' THEN
        ALTER TABLE payment_route_candidate DROP INDEX uk_payment_route_candidate_mid_deleted;
        SET mid_index_columns = NULL;
    END IF;
    IF mid_index_columns IS NULL THEN
        ALTER TABLE payment_route_candidate
            ADD UNIQUE KEY uk_payment_route_candidate_mid_deleted
                (rule_id, payment_method, mid_config_id, deleted);
    END IF;

    SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index)
    INTO priority_index_columns
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'payment_route_candidate'
      AND index_name = 'uk_payment_route_candidate_priority_deleted';

    IF priority_index_columns IS NOT NULL
       AND priority_index_columns <> 'rule_id,payment_method,candidate_priority,deleted' THEN
        ALTER TABLE payment_route_candidate DROP INDEX uk_payment_route_candidate_priority_deleted;
        SET priority_index_columns = NULL;
    END IF;
    IF priority_index_columns IS NULL THEN
        ALTER TABLE payment_route_candidate
            ADD UNIQUE KEY uk_payment_route_candidate_priority_deleted
                (rule_id, payment_method, candidate_priority, deleted);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'payment_route_candidate'
          AND index_name = 'idx_payment_route_candidate_method'
    ) THEN
        ALTER TABLE payment_route_candidate
            ADD KEY idx_payment_route_candidate_method (payment_method, candidate_status, deleted);
    END IF;
END$$
DELIMITER ;

CALL migrate_payment_route_candidate_method();
DROP PROCEDURE migrate_payment_route_candidate_method;

CREATE TABLE IF NOT EXISTS payment_route_change_log (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    rule_id BIGINT NOT NULL COMMENT '支付路由规则ID',
    rule_code VARCHAR(64) NOT NULL COMMENT '规则编码审计冗余',
    action_type VARCHAR(32) NOT NULL COMMENT '操作类型：CREATE、UPDATE、COPY、PUBLISH、DISABLE、DELETE',
    before_version INT NULL COMMENT '变更前版本',
    after_version INT NULL COMMENT '变更后版本',
    before_snapshot_json JSON NULL COMMENT '变更前非敏感配置快照',
    after_snapshot_json JSON NULL COMMENT '变更后非敏感配置快照',
    operator_account_id BIGINT NULL COMMENT '可信登录账号ID',
    operator_name VARCHAR(64) NOT NULL COMMENT '可信操作人名称',
    operation_reason VARCHAR(500) NOT NULL COMMENT '操作原因',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '操作时间',
    PRIMARY KEY (id),
    KEY idx_payment_route_change_rule_time (rule_id, create_time, id),
    KEY idx_payment_route_change_operator_time (operator_account_id, create_time, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='支付路由配置不可变变更日志表';

INSERT INTO sys_menu (
    app_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path,
    permission_code, icon, visible, keep_alive, external_link, sort_no, status, deleted
)
SELECT 1, parent.id, 'admin_payment_route_v1', '支付路由', 'MENU', '/channel/route', 'channel/route',
       'channel:route:list', 'Guide', 1, 0, 0, 46, 1, 0
FROM sys_menu parent
WHERE parent.app_id = 1 AND parent.menu_code = 'admin_channel_catalog_v1' AND parent.deleted = 0
ON DUPLICATE KEY UPDATE parent_id=VALUES(parent_id), menu_name=VALUES(menu_name), route_path=VALUES(route_path),
    component_path=VALUES(component_path), permission_code=VALUES(permission_code), icon=VALUES(icon),
    visible=VALUES(visible), sort_no=VALUES(sort_no), status=VALUES(status), deleted=0;

UPDATE sys_menu
SET sort_no = CASE menu_code
    WHEN 'admin_channel_alert_rule_v1' THEN 47
    WHEN 'admin_channel_alert_event_v1' THEN 48
END
WHERE app_id = 1 AND menu_code IN ('admin_channel_alert_rule_v1', 'admin_channel_alert_event_v1') AND deleted = 0;

INSERT INTO sys_menu (
    app_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path,
    permission_code, icon, visible, keep_alive, external_link, sort_no, status, deleted
)
SELECT 1, parent.id, item.menu_code, item.menu_name, 'BUTTON', NULL, NULL,
       item.permission_code, NULL, 0, 0, 0, item.sort_no, 1, 0
FROM sys_menu parent
JOIN (
    SELECT 'admin_payment_route_detail_v1' menu_code, '支付路由详情' menu_name, 'channel:route:detail' permission_code, 1 sort_no
    UNION ALL SELECT 'admin_payment_route_add_v1', '支付路由新增', 'channel:route:add', 2
    UNION ALL SELECT 'admin_payment_route_edit_v1', '支付路由修改', 'channel:route:edit', 3
    UNION ALL SELECT 'admin_payment_route_copy_v1', '支付路由复制', 'channel:route:copy', 4
    UNION ALL SELECT 'admin_payment_route_publish_v1', '支付路由发布', 'channel:route:publish', 5
    UNION ALL SELECT 'admin_payment_route_disable_v1', '支付路由停用', 'channel:route:disable', 6
    UNION ALL SELECT 'admin_payment_route_remove_v1', '支付路由删除', 'channel:route:remove', 7
    UNION ALL SELECT 'admin_payment_route_validate_v1', '支付路由校验', 'channel:route:validate', 8
    UNION ALL SELECT 'admin_payment_route_history_v1', '支付路由变更历史', 'channel:route:history', 9
) item ON 1 = 1
WHERE parent.app_id = 1 AND parent.menu_code = 'admin_payment_route_v1' AND parent.deleted = 0
ON DUPLICATE KEY UPDATE parent_id=VALUES(parent_id), menu_name=VALUES(menu_name),
    permission_code=VALUES(permission_code), visible=VALUES(visible), sort_no=VALUES(sort_no), status=VALUES(status), deleted=0;

INSERT INTO sys_permission (
    app_id, menu_id, permission_code, permission_name, permission_type,
    resource_method, resource_path, status, deleted
)
SELECT 1, menu.id, item.permission_code, item.permission_name, item.permission_type,
       item.resource_method, item.resource_path, 1, 0
FROM sys_menu menu
JOIN (
    SELECT 'admin_payment_route_v1' menu_code, 'channel:route:list' permission_code, '支付路由查询' permission_name, 'MENU' permission_type, 'POST' resource_method, '/admin/channel/routes/search' resource_path
    UNION ALL SELECT 'admin_payment_route_detail_v1', 'channel:route:detail', '支付路由详情', 'BUTTON', 'GET', '/admin/channel/routes/*'
    UNION ALL SELECT 'admin_payment_route_add_v1', 'channel:route:add', '支付路由新增', 'BUTTON', 'POST', '/admin/channel/routes'
    UNION ALL SELECT 'admin_payment_route_edit_v1', 'channel:route:edit', '支付路由修改', 'BUTTON', 'PUT', '/admin/channel/routes/*'
    UNION ALL SELECT 'admin_payment_route_copy_v1', 'channel:route:copy', '支付路由复制', 'BUTTON', 'POST', '/admin/channel/routes/*/copy'
    UNION ALL SELECT 'admin_payment_route_publish_v1', 'channel:route:publish', '支付路由发布', 'BUTTON', 'POST', '/admin/channel/routes/*/publish'
    UNION ALL SELECT 'admin_payment_route_disable_v1', 'channel:route:disable', '支付路由停用', 'BUTTON', 'POST', '/admin/channel/routes/*/disable'
    UNION ALL SELECT 'admin_payment_route_remove_v1', 'channel:route:remove', '支付路由删除', 'BUTTON', 'DELETE', '/admin/channel/routes/*'
    UNION ALL SELECT 'admin_payment_route_validate_v1', 'channel:route:validate', '支付路由校验', 'BUTTON', 'GET', '/admin/channel/routes/*/validation'
    UNION ALL SELECT 'admin_payment_route_history_v1', 'channel:route:history', '支付路由变更历史', 'BUTTON', 'POST', '/admin/channel/routes/*/change-logs/search'
) item ON item.menu_code = menu.menu_code
WHERE menu.app_id = 1 AND menu.deleted = 0
ON DUPLICATE KEY UPDATE menu_id=VALUES(menu_id), permission_name=VALUES(permission_name),
    permission_type=VALUES(permission_type), resource_method=VALUES(resource_method),
    resource_path=VALUES(resource_path), status=VALUES(status), deleted=0;

INSERT IGNORE INTO sys_role_menu (app_id, role_id, menu_id, deleted)
SELECT 1, role.id, menu.id, 0
FROM sys_role role
JOIN sys_menu menu ON menu.app_id = role.app_id
WHERE role.app_id = 1 AND (role.id = 1 OR role.role_code IN ('ADMIN', 'ADMIN_OPERATOR'))
  AND role.deleted = 0
  AND (menu.menu_code = 'admin_payment_route_v1' OR menu.menu_code LIKE 'admin_payment_route_%_v1')
  AND menu.deleted = 0;

INSERT IGNORE INTO sys_role_permission (app_id, role_id, permission_id, deleted)
SELECT 1, role.id, permission.id, 0
FROM sys_role role
JOIN sys_permission permission ON permission.app_id = role.app_id
WHERE role.app_id = 1 AND (role.id = 1 OR role.role_code IN ('ADMIN', 'ADMIN_OPERATOR'))
  AND role.deleted = 0 AND permission.permission_code LIKE 'channel:route:%' AND permission.deleted = 0;
