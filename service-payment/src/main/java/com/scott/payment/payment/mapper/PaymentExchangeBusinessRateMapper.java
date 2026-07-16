package com.scott.payment.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.payment.entity.ExchangeBusinessRateDO;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentExchangeBusinessRateMapper
 * @date : 2026-07-15 00:00
 * @email : scott_x@163.com
 * @description : 支付核心业务汇率 Mapper，位于 service-payment 数据访问层，仅用于 EDC 查询交易汇率。
 * @status : create
 */
public interface PaymentExchangeBusinessRateMapper extends BaseMapper<ExchangeBusinessRateDO> {
}
