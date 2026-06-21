package com.scott.payment.job.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.job.entity.SysShardingTableCreateLogDO;

/**
 * 分表建表任务日志 Mapper。
 *
 * <p>只负责建表批次日志的数据访问。</p>
 */
public interface SysShardingTableCreateLogMapper extends BaseMapper<SysShardingTableCreateLogDO> {
}
