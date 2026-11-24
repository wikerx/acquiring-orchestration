-- 保证金结算明细按动作时间和主键稳定分页。仅新增索引，无需回填历史数据。
ALTER TABLE merchant_reserve_action
    ADD INDEX idx_reserve_action_time (action_time, id) COMMENT '按动作时间及主键续读保证金结算明细',
    ALGORITHM=INPLACE, LOCK=NONE;
