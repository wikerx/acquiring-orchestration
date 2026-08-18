package com.scott.payment.component.db.systemconfig.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.component.db.systemconfig.entity.SystemConfigDO;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SystemConfigMapper
 * @date : 2026-08-11 00:00
 * @email : scott_x@163.com
 * @description : 跨服务系统参数只读 Mapper，只提供公共配置缓存的数据库回源能力
 * @status : create
 */
public interface SystemConfigMapper extends BaseMapper<SystemConfigDO> {
}
