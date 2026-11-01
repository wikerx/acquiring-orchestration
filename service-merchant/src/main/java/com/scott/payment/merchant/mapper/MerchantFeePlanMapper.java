package com.scott.payment.merchant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.merchant.entity.MerchantFinanceEntities.FeePlanDO;

/**
 * 商户端费用方案只读数据访问，业务查询必须同时限定认证商户号和商户方案类型。
 */
public interface MerchantFeePlanMapper extends BaseMapper<FeePlanDO> { }
