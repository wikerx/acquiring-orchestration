-- 时间字段精度统一迁移草案。
-- 注意：本文件只作为人工确认后的执行草案，不得由 AI 直接执行。
-- 执行前请确认字段默认值、索引、业务影响、线上 MySQL 版本和回滚方案。

-- 任务调度中心：统一任务定义表时间点字段到毫秒精度。
ALTER TABLE `sys_job_task`
  MODIFY COLUMN `next_trigger_time` DATETIME(3) DEFAULT NULL COMMENT '下次触发时间',
  MODIFY COLUMN `last_trigger_time` DATETIME(3) DEFAULT NULL COMMENT '上次触发时间',
  MODIFY COLUMN `lock_until` DATETIME(3) DEFAULT NULL COMMENT '锁过期时间',
  MODIFY COLUMN `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  MODIFY COLUMN `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间';

-- 任务调度中心：统一执行日志表时间点字段到毫秒精度。
ALTER TABLE `sys_job_run_log`
  MODIFY COLUMN `start_time` DATETIME(3) DEFAULT NULL COMMENT '开始时间',
  MODIFY COLUMN `end_time` DATETIME(3) DEFAULT NULL COMMENT '结束时间',
  MODIFY COLUMN `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  MODIFY COLUMN `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间';

-- 任务调度中心：统一执行节点表心跳与审计时间到毫秒精度。
ALTER TABLE `sys_job_executor_node`
  MODIFY COLUMN `last_heartbeat_time` DATETIME(3) NOT NULL COMMENT '最后心跳时间',
  MODIFY COLUMN `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  MODIFY COLUMN `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间';

-- 分表治理：统一物理表登记表时间点字段到毫秒精度。
ALTER TABLE `sys_sharding_physical_table`
  MODIFY COLUMN `last_check_time` DATETIME(3) DEFAULT NULL COMMENT '最后检查时间',
  MODIFY COLUMN `created_time` DATETIME(3) DEFAULT NULL COMMENT '物理表创建时间',
  MODIFY COLUMN `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '记录创建时间',
  MODIFY COLUMN `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '记录更新时间';

-- 分表治理：统一建表任务日志表时间点字段到毫秒精度。
ALTER TABLE `sys_sharding_table_create_log`
  MODIFY COLUMN `start_time` DATETIME(3) NOT NULL COMMENT '开始时间',
  MODIFY COLUMN `end_time` DATETIME(3) DEFAULT NULL COMMENT '结束时间',
  MODIFY COLUMN `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间';
