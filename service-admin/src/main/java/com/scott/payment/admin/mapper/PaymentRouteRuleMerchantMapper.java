package com.scott.payment.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.admin.entity.channel.PaymentRouteEntities.PaymentRouteRuleMerchantDO;

/** 支付路由规则与指定商户的多对多关联数据访问。 */
public interface PaymentRouteRuleMerchantMapper extends BaseMapper<PaymentRouteRuleMerchantDO> {
}
