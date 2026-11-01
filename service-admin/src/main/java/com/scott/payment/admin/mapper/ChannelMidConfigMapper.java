package com.scott.payment.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.admin.entity.channel.ChannelEntities.ChannelMidConfigDO;

/**
 * 渠道 MID 配置 Mapper，位于 service-admin 数据访问层，仅负责 channel_mid_config 表的增删改查。
 */
public interface ChannelMidConfigMapper extends BaseMapper<ChannelMidConfigDO> {
}
