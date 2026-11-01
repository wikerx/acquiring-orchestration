package com.scott.payment.merchant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.merchant.entity.MerchantFinanceEntities.FeePlanVersionDO;

/**
 * 商户端生效费用版本只读数据访问，只允许读取当前商户方案指向的 ACTIVE 版本。
 */
public interface MerchantFeePlanVersionMapper extends BaseMapper<FeePlanVersionDO> { }
