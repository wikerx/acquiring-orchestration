package com.scott.payment.component.db.systemconfig.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.component.db.systemconfig.entity.SystemConfigDO;

/**
 * 跨服务系统参数只读 Mapper，只提供公共配置缓存的数据库回源能力
 */
public interface SystemConfigMapper extends BaseMapper<SystemConfigDO> {
}
