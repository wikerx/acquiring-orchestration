package com.scott.payment.openapi.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.openapi.entity.OpenApiMerchantInfoDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiMerchantInfoMapper
 * @date : 2026-05-30 00:00
 * @email : scott_x@163.com
 * @description : OpenAPI 商户基础信息 MyBatisPlus Mapper
 * @status : create
 */
@Mapper
public interface OpenApiMerchantInfoMapper extends BaseMapper<OpenApiMerchantInfoDO> {
}
