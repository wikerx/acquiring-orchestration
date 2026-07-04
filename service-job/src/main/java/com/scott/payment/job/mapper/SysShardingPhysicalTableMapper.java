package com.scott.payment.job.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.job.entity.SysShardingPhysicalTableDO;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysShardingPhysicalTableMapper
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 分表物理表登记 Mapper。 <p>只负责分表治理登记表的数据访问，不承载建表业务判断。</p>
 * @status : create
 */
public interface SysShardingPhysicalTableMapper extends BaseMapper<SysShardingPhysicalTableDO> {
}
