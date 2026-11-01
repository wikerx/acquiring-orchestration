package com.scott.payment.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.admin.entity.channel.ChannelEntities.ChannelMetadataSchemaDO;

/**
 * 渠道 MID 参数模板 Mapper，位于 service-admin 数据访问层，仅负责 channel_metadata_schema 表的增删改查。
 */
public interface ChannelMetadataSchemaMapper extends BaseMapper<ChannelMetadataSchemaDO> {
}
