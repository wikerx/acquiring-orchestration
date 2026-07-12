package com.scott.payment.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.payment.entity.ChannelMidConfigDO;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentChannelMidConfigMapper
 * @date : 2026-07-12 00:00
 * @email : scott_x@163.com
 * @description : 交易路由渠道 MID 配置 Mapper，位于 service-payment 数据访问层，仅用于读取真实渠道 MID 和元数据。
 * @status : create
 */
public interface PaymentChannelMidConfigMapper extends BaseMapper<ChannelMidConfigDO> {
}
