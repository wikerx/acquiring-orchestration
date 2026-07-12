package com.scott.payment.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.payment.entity.MerchantChannelMidBindingDO;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentMerchantChannelMidBindingMapper
 * @date : 2026-07-12 00:00
 * @email : scott_x@163.com
 * @description : 交易路由商户 MID 绑定 Mapper，位于 service-payment 数据访问层，仅用于读取商户启用中的 MID 绑定关系。
 * @status : create
 */
public interface PaymentMerchantChannelMidBindingMapper extends BaseMapper<MerchantChannelMidBindingDO> {
}
