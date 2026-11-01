package com.scott.payment.openapi.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.openapi.entity.MerchantInfoDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * OpenAPI 商户基础信息 MyBatisPlus Mapper
 */

@Mapper
public interface MerchantInfoMapper extends BaseMapper<MerchantInfoDO> {
}
