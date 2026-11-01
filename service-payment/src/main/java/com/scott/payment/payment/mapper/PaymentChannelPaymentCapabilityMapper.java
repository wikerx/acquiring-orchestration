package com.scott.payment.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.payment.entity.ChannelPaymentCapabilityDO;

/**
 * 交易路由渠道支付能力 Mapper，位于 service-payment 数据访问层，仅用于读取渠道支付能力配置。
 */
public interface PaymentChannelPaymentCapabilityMapper extends BaseMapper<ChannelPaymentCapabilityDO> {
}
