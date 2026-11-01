package com.scott.payment.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.admin.entity.channel.ChannelEntities.MerchantChannelMidBindingDO;

/**
 * 商户渠道 MID 绑定 Mapper，位于 service-admin 数据访问层，仅负责 merchant_channel_mid_binding 表的增删改查。
 */
public interface MerchantChannelMidBindingMapper extends BaseMapper<MerchantChannelMidBindingDO> {
}
