-- 可靠 MQ Outbox 按生产服务隔离后的索引迁移。
-- 支持重复执行；仅在索引列定义不一致时重建二级索引，不修改消息数据或状态。

SET @schema_name = DATABASE();

SET @table_exists = (
    SELECT COUNT(1)
    FROM information_schema.tables
    WHERE table_schema = @schema_name
      AND table_name = 'sys_mq_outbox'
);

SET @due_index_columns = (
    SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',')
    FROM information_schema.statistics
    WHERE table_schema = @schema_name
      AND table_name = 'sys_mq_outbox'
      AND index_name = 'idx_sys_mq_outbox_due'
);

SET @due_index_ddl = CASE
    WHEN @table_exists = 0 THEN 'SELECT 1'
    WHEN @due_index_columns IS NULL THEN
        'ALTER TABLE sys_mq_outbox ADD INDEX idx_sys_mq_outbox_due (producer_service, event_status, next_retry_time, create_time, id), ALGORITHM=INPLACE, LOCK=NONE'
    WHEN @due_index_columns = 'producer_service,event_status,next_retry_time,create_time,id' THEN 'SELECT 1'
    ELSE
        'ALTER TABLE sys_mq_outbox DROP INDEX idx_sys_mq_outbox_due, ADD INDEX idx_sys_mq_outbox_due (producer_service, event_status, next_retry_time, create_time, id), ALGORITHM=INPLACE, LOCK=NONE'
END;
PREPARE stmt FROM @due_index_ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @processing_index_columns = (
    SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',')
    FROM information_schema.statistics
    WHERE table_schema = @schema_name
      AND table_name = 'sys_mq_outbox'
      AND index_name = 'idx_sys_mq_outbox_processing'
);

SET @processing_index_ddl = CASE
    WHEN @table_exists = 0 THEN 'SELECT 1'
    WHEN @processing_index_columns IS NULL THEN
        'ALTER TABLE sys_mq_outbox ADD INDEX idx_sys_mq_outbox_processing (producer_service, event_status, processing_started_time), ALGORITHM=INPLACE, LOCK=NONE'
    WHEN @processing_index_columns = 'producer_service,event_status,processing_started_time' THEN 'SELECT 1'
    ELSE
        'ALTER TABLE sys_mq_outbox DROP INDEX idx_sys_mq_outbox_processing, ADD INDEX idx_sys_mq_outbox_processing (producer_service, event_status, processing_started_time), ALGORITHM=INPLACE, LOCK=NONE'
END;
PREPARE stmt FROM @processing_index_ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
