package com.scott.payment.merchant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.merchant.entity.MerchantFinanceEntities.FeeRuleDO;

/**
 * 商户端当前生效费用规则只读数据访问，不承载费率匹配或计算逻辑。
 */
public interface MerchantFeeRuleMapper extends BaseMapper<FeeRuleDO> { }
