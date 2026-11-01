package com.scott.payment.merchant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.merchant.entity.SysDictDataDO;

/**
 * 商户系统只读字典 Mapper，位于 service-merchant 数据访问层，仅查询管理系统维护的启用字典项。
 */
public interface SysDictDataMapper extends BaseMapper<SysDictDataDO> {
}
