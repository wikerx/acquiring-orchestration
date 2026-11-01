package com.scott.payment.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.admin.entity.channel.ChannelAlertEntities.ChannelAlertNotifyLogDO;

/**
 * 渠道预警通知日志 Mapper，位于 service-admin 数据访问层，仅负责邮件通知执行日志查询。
 */
public interface ChannelAlertNotifyLogMapper extends BaseMapper<ChannelAlertNotifyLogDO> {
}
