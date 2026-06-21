package com.scott.payment.job.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.job.entity.SysShardingPhysicalTableDO;

/**
 * 分表物理表登记 Mapper。
 *
 * <p>只负责分表治理登记表的数据访问，不承载建表业务判断。</p>
 */
public interface SysShardingPhysicalTableMapper extends BaseMapper<SysShardingPhysicalTableDO> {
}
