-- 商户系统基础功能与权限体系迁移草案
-- 执行前必须人工确认现有唯一键、历史商户账号、角色编码和线上数据兼容性。

ALTER TABLE `sys_account`
    MODIFY COLUMN `merchant_id` VARCHAR(64) DEFAULT NULL COMMENT '商户号，商户系统账号必填，管理系统账号为空';

ALTER TABLE `sys_role`
    ADD COLUMN `merchant_id` VARCHAR(64) DEFAULT NULL COMMENT '商户号，商户系统角色必填，平台角色为空' AFTER `role_name`,
    ADD KEY `idx_sys_role_merchant` (`merchant_id`, `status`, `deleted`);

-- 如果需要支持不同商户使用相同 login_account，先确认并处理现有唯一键冲突后再执行：
-- ALTER TABLE `sys_account` DROP INDEX `uk_sys_account_app_login_deleted`;
-- ALTER TABLE `sys_account`
--     ADD UNIQUE KEY `uk_sys_account_app_merchant_login_deleted` (`app_id`, `merchant_id`, `login_account`, `deleted`);

-- 如果需要支持不同商户使用相同 role_code，先确认并处理现有唯一键冲突后再执行：
-- ALTER TABLE `sys_role` DROP INDEX `uk_sys_role_app_code_deleted`;
-- ALTER TABLE `sys_role`
--     ADD UNIQUE KEY `uk_sys_role_app_merchant_code_deleted` (`app_id`, `merchant_id`, `role_code`, `deleted`);

CREATE TABLE IF NOT EXISTS `sys_merchant_dept` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `merchant_id` VARCHAR(64) NOT NULL COMMENT '商户号',
    `parent_id` BIGINT NOT NULL DEFAULT 0 COMMENT '父级部门ID，0=顶级部门',
    `dept_code` VARCHAR(80) NOT NULL COMMENT '部门编码',
    `dept_name` VARCHAR(100) NOT NULL COMMENT '部门名称',
    `leader_account_id` BIGINT DEFAULT NULL COMMENT '负责人账号ID',
    `phone` VARCHAR(30) DEFAULT NULL COMMENT '联系电话',
    `email` VARCHAR(150) DEFAULT NULL COMMENT '邮箱',
    `sort_no` INT NOT NULL DEFAULT 0 COMMENT '排序号',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1=启用，0=禁用',
    `remark` VARCHAR(500) DEFAULT NULL COMMENT '备注',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `created_by` BIGINT DEFAULT NULL COMMENT '创建人ID',
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `updated_by` BIGINT DEFAULT NULL COMMENT '更新人ID',
    `deleted` BIGINT NOT NULL DEFAULT 0 COMMENT '删除标识：0=未删除，非0=删除时间戳或删除批次ID',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_merchant_dept_code_deleted` (`merchant_id`, `dept_code`, `deleted`),
    KEY `idx_merchant_parent` (`merchant_id`, `parent_id`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商户部门表';

CREATE TABLE IF NOT EXISTS `sys_merchant_post` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `merchant_id` VARCHAR(64) NOT NULL COMMENT '商户号',
    `post_code` VARCHAR(80) NOT NULL COMMENT '岗位编码',
    `post_name` VARCHAR(100) NOT NULL COMMENT '岗位名称',
    `sort_no` INT NOT NULL DEFAULT 0 COMMENT '排序号',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1=启用，0=禁用',
    `remark` VARCHAR(500) DEFAULT NULL COMMENT '备注',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `created_by` BIGINT DEFAULT NULL COMMENT '创建人ID',
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `updated_by` BIGINT DEFAULT NULL COMMENT '更新人ID',
    `deleted` BIGINT NOT NULL DEFAULT 0 COMMENT '删除标识：0=未删除，非0=删除时间戳或删除批次ID',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_merchant_post_code_deleted` (`merchant_id`, `post_code`, `deleted`),
    KEY `idx_merchant_id` (`merchant_id`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商户岗位表';

CREATE TABLE IF NOT EXISTS `sys_merchant_account_dept` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `merchant_id` VARCHAR(64) NOT NULL COMMENT '商户号',
    `account_id` BIGINT NOT NULL COMMENT '商户账号ID',
    `dept_id` BIGINT NOT NULL COMMENT '部门ID',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `created_by` BIGINT DEFAULT NULL COMMENT '创建人ID',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_account_dept` (`account_id`, `dept_id`),
    KEY `idx_merchant_id` (`merchant_id`),
    KEY `idx_dept_id` (`dept_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商户账号部门关联表';

CREATE TABLE IF NOT EXISTS `sys_merchant_account_post` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `merchant_id` VARCHAR(64) NOT NULL COMMENT '商户号',
    `account_id` BIGINT NOT NULL COMMENT '商户账号ID',
    `post_id` BIGINT NOT NULL COMMENT '岗位ID',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `created_by` BIGINT DEFAULT NULL COMMENT '创建人ID',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_account_post` (`account_id`, `post_id`),
    KEY `idx_merchant_id` (`merchant_id`),
    KEY `idx_post_id` (`post_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商户账号岗位关联表';

CREATE TABLE IF NOT EXISTS `sys_merchant_menu_grant` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `merchant_id` VARCHAR(64) NOT NULL COMMENT '商户号',
    `app_id` BIGINT NOT NULL COMMENT '系统应用ID，固定为商户系统',
    `menu_id` BIGINT NOT NULL COMMENT '菜单ID',
    `grant_source` VARCHAR(30) NOT NULL DEFAULT 'ADMIN' COMMENT '授权来源：ADMIN=平台授权，SYSTEM=系统初始化',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1=启用，0=禁用',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `created_by` BIGINT DEFAULT NULL COMMENT '创建人ID',
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `updated_by` BIGINT DEFAULT NULL COMMENT '更新人ID',
    `deleted` BIGINT NOT NULL DEFAULT 0 COMMENT '删除标识：0=未删除，非0=删除时间戳或删除批次ID',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_merchant_menu_deleted` (`merchant_id`, `menu_id`, `deleted`),
    KEY `idx_merchant_id` (`merchant_id`),
    KEY `idx_app_id` (`app_id`),
    KEY `idx_menu_id` (`menu_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商户菜单授权表';

CREATE TABLE IF NOT EXISTS `sys_merchant_permission_grant` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `merchant_id` VARCHAR(64) NOT NULL COMMENT '商户号',
    `app_id` BIGINT NOT NULL COMMENT '系统应用ID，固定为商户系统',
    `permission_id` BIGINT NOT NULL COMMENT '权限ID',
    `grant_source` VARCHAR(30) NOT NULL DEFAULT 'ADMIN' COMMENT '授权来源：ADMIN=平台授权，SYSTEM=系统初始化',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1=启用，0=禁用',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `created_by` BIGINT DEFAULT NULL COMMENT '创建人ID',
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `updated_by` BIGINT DEFAULT NULL COMMENT '更新人ID',
    `deleted` BIGINT NOT NULL DEFAULT 0 COMMENT '删除标识：0=未删除，非0=删除时间戳或删除批次ID',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_merchant_permission_deleted` (`merchant_id`, `permission_id`, `deleted`),
    KEY `idx_merchant_id` (`merchant_id`),
    KEY `idx_app_id` (`app_id`),
    KEY `idx_permission_id` (`permission_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商户权限授权表';
