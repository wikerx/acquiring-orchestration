package com.scott.payment.merchant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.merchant.entity.SysDictDataDO;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysDictDataMapper
 * @date : 2026-07-20 00:00
 * @email : scott_x@163.com
 * @description : 商户系统只读字典 Mapper，位于 service-merchant 数据访问层，仅查询管理系统维护的启用字典项。
 * @status : create
 */
public interface SysDictDataMapper extends BaseMapper<SysDictDataDO> {
}
