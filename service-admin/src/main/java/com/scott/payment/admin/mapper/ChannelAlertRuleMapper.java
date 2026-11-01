package com.scott.payment.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.admin.entity.channel.ChannelAlertEntities.ChannelAlertRuleDO;

/**
 * 渠道预警规则 Mapper，位于 service-admin 数据访问层，仅负责规则配置持久化。
 */
public interface ChannelAlertRuleMapper extends BaseMapper<ChannelAlertRuleDO> {
}
