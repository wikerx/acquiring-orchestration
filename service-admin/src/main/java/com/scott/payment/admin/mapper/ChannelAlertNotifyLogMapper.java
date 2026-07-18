package com.scott.payment.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.admin.entity.channel.ChannelAlertEntities.ChannelAlertNotifyLogDO;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ChannelAlertNotifyLogMapper
 * @date : 2026-07-17 00:00
 * @email : scott_x@163.com
 * @description : 渠道预警通知日志 Mapper，位于 service-admin 数据访问层，仅负责邮件通知执行日志查询。
 * @status : create
 */
public interface ChannelAlertNotifyLogMapper extends BaseMapper<ChannelAlertNotifyLogDO> {
}
