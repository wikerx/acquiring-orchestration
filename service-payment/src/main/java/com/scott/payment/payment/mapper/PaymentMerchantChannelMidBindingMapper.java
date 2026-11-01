package com.scott.payment.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.payment.entity.MerchantChannelMidBindingDO;

/**
 * 交易路由商户 MID 绑定 Mapper，位于 service-payment 数据访问层，仅用于读取商户启用中的 MID 绑定关系。
 */
public interface PaymentMerchantChannelMidBindingMapper extends BaseMapper<MerchantChannelMidBindingDO> {
}
