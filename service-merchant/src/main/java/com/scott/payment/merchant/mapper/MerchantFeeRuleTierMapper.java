package com.scott.payment.merchant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.merchant.entity.MerchantFinanceEntities.FeeRuleTierDO;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantFeeRuleTierMapper
 * @date : 2026-08-18 00:00
 * @email : scott_x@163.com
 * @description : 商户端当前费用规则月累计阶梯只读数据访问，不执行跨币种累计换算。
 * @status : create
 */
public interface MerchantFeeRuleTierMapper extends BaseMapper<FeeRuleTierDO> { }
