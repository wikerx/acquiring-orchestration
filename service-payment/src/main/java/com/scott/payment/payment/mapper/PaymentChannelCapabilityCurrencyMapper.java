package com.scott.payment.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.payment.entity.ChannelCapabilityCurrencyDO;

/**
 * 交易路由渠道能力币种 Mapper，位于 service-payment 数据访问层，仅用于读取渠道可用交易币种。
 */
public interface PaymentChannelCapabilityCurrencyMapper extends BaseMapper<ChannelCapabilityCurrencyDO> {
}
