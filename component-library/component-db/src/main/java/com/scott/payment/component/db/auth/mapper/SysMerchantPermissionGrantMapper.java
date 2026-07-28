package com.scott.payment.component.db.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.component.db.auth.entity.SysMerchantPermissionGrantDO;
import org.apache.ibatis.annotations.Mapper;


@Mapper
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysMerchantPermissionGrantMapper
 * @date : 2026-06-23 12:55
 * @email : scott_x@163.com
 * @description : Sys Merchant Permission Grant Mapper 映射组件，位于 公共组件库，在数据库记录、领域模型、接口 DTO 或渠道协议对象之间转换字段。
 * @status : create
 */
public interface SysMerchantPermissionGrantMapper extends BaseMapper<SysMerchantPermissionGrantDO> {
}
