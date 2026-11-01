package com.scott.payment.openapi.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.openapi.entity.MerchantResponseKeyDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * OpenAPI 商户响应加密公钥 MyBatisPlus Mapper
 */

@Mapper
public interface MerchantResponseKeyMapper extends BaseMapper<MerchantResponseKeyDO> {
}
