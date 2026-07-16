package com.scott.payment.payment.service;

import com.scott.payment.payment.service.dto.PaymentExchangeRateDTO;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentExchangeRateService
 * @date : 2026-07-15 00:00
 * @email : scott_x@163.com
 * @description : 支付核心交易汇率服务，位于 service-payment 服务层，用于 EDC 查询标签币种到渠道交易币种的有效交易汇率。
 * @status : create
 */
public interface PaymentExchangeRateService {

    /**
     * 查询交易场景有效汇率。
     *
     * @param baseCurrency  源币种，商户标签币种
     * @param quoteCurrency 目标币种，渠道支持币种
     * @param atTime        交易业务时间
     * @return 有效交易汇率，不存在时返回空
     */
    Optional<PaymentExchangeRateDTO> findTransactionRate(String baseCurrency, String quoteCurrency, LocalDateTime atTime);
}
