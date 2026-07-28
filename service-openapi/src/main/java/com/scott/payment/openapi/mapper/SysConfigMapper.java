package com.scott.payment.openapi.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.openapi.entity.SysConfigDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 系统参数配置只读 Mapper。
 */
@Mapper
public interface SysConfigMapper extends BaseMapper<SysConfigDO> {
}
