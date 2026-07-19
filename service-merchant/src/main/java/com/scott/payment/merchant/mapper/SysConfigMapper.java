package com.scott.payment.merchant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.merchant.entity.SysConfigDO;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysConfigMapper
 * @date : 2026-07-19 00:00
 * @email : scott_x@163.com
 * @description : 商户系统只读系统参数 Mapper，位于 service-merchant 数据访问层，仅查询 Admin 参数管理维护的公共配置。
 * @status : create
 */
public interface SysConfigMapper extends BaseMapper<SysConfigDO> {
}
