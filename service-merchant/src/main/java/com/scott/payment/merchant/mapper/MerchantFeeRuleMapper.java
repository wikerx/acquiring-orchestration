package com.scott.payment.merchant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.merchant.entity.MerchantFinanceEntities.FeeRuleDO;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantFeeRuleMapper
 * @date : 2026-08-18 00:00
 * @email : scott_x@163.com
 * @description : 商户端当前生效费用规则只读数据访问，不承载费率匹配或计算逻辑。
 * @status : create
 */
public interface MerchantFeeRuleMapper extends BaseMapper<FeeRuleDO> { }
