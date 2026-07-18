package com.scott.payment.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.admin.entity.channel.ChannelAlertEntities.ChannelAlertEventDO;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ChannelAlertEventMapper
 * @date : 2026-07-17 00:00
 * @email : scott_x@163.com
 * @description : 渠道预警事件 Mapper，位于 service-admin 数据访问层，仅负责预警事件查询和人工确认状态更新。
 * @status : create
 */
public interface ChannelAlertEventMapper extends BaseMapper<ChannelAlertEventDO> {
}
