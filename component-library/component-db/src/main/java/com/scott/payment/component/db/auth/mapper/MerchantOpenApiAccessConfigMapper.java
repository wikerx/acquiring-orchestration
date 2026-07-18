package com.scott.payment.component.db.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.component.db.auth.entity.MerchantOpenApiAccessConfigDO;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantOpenApiAccessConfigMapper
 * @date : 2026-07-18 00:00
 * @email : scott_x@163.com
 * @description : 商户 OpenAPI 入站访问配置 Mapper，位于 component-db 数据访问层，供 admin 配置和 openapi 校验共同复用。
 * @status : create
 */
public interface MerchantOpenApiAccessConfigMapper extends BaseMapper<MerchantOpenApiAccessConfigDO> {
}
