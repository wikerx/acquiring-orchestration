package com.scott.payment.merchant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.merchant.entity.MerchantFinanceEntities.FeeRuleTierDO;

/**
 * 商户端当前费用规则月累计阶梯只读数据访问，不执行跨币种累计换算。
 */
public interface MerchantFeeRuleTierMapper extends BaseMapper<FeeRuleTierDO> { }
