-- 用户管理部门和岗位关联 SQL 草案。
-- 注意：本文件只作为人工确认后的执行草案，不由 AI 直接执行。

ALTER TABLE `sys_user`
  ADD COLUMN `dept_id` BIGINT DEFAULT NULL COMMENT '所属部门ID' AFTER `real_name`;

CREATE INDEX `idx_sys_user_dept_id`
  ON `sys_user` (`dept_id`);

CREATE TABLE IF NOT EXISTS `sys_user_post` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` BIGINT NOT NULL COMMENT '用户主体ID',
  `post_id` BIGINT NOT NULL COMMENT '岗位ID',
  `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_user_post` (`user_id`, `post_id`),
  KEY `idx_sys_user_post_post_id` (`post_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户与岗位关联表';
