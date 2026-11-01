package com.scott.payment.openapi.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.openapi.entity.MerchantJwtKeyDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * OpenAPI 商户 JWT 签名密钥 MyBatisPlus Mapper
 */

@Mapper
public interface MerchantJwtKeyMapper extends BaseMapper<MerchantJwtKeyDO> {
}
