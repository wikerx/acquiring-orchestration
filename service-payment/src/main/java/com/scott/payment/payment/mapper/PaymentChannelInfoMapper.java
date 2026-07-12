package com.scott.payment.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.payment.entity.ChannelInfoDO;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentChannelInfoMapper
 * @date : 2026-07-12 00:00
 * @email : scott_x@163.com
 * @description : 交易路由渠道基础信息 Mapper，位于 service-payment 数据访问层，仅用于读取渠道启停和请求配置。
 * @status : create
 */
public interface PaymentChannelInfoMapper extends BaseMapper<ChannelInfoDO> {
}
