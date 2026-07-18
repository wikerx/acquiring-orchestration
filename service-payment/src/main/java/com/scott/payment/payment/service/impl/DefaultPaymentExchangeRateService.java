package com.scott.payment.payment.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.scott.payment.payment.entity.ExchangeBusinessRateDO;
import com.scott.payment.payment.mapper.PaymentExchangeBusinessRateMapper;
import com.scott.payment.payment.service.PaymentExchangeRateService;
import com.scott.payment.payment.service.dto.PaymentExchangeRateDTO;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DefaultPaymentExchangeRateService
 * @date : 2026-07-15 00:00
 * @email : scott_x@163.com
 * @description : 支付核心交易汇率默认实现，位于 service-payment 服务实现层，只读 exchange_business_rate 当前有效 TRANSACTION_RATE。
 * @status : create
 */
@Service
public class DefaultPaymentExchangeRateService implements PaymentExchangeRateService {

    /**
     * 未删除标识。
     */
    private static final long NOT_DELETED = 0L;

    /**
     * 交易汇率类型。
     */
    private static final String TRANSACTION_RATE = "TRANSACTION_RATE";

    /**
     * 有效汇率状态。
     */
    private static final String RATE_STATUS_ENABLED = "ENABLED";

    private final PaymentExchangeBusinessRateMapper exchangeBusinessRateMapper;

    /**
     * 创建支付核心交易汇率服务。
     *
     * @param exchangeBusinessRateMapper 业务汇率 Mapper
     */
    public DefaultPaymentExchangeRateService(PaymentExchangeBusinessRateMapper exchangeBusinessRateMapper) {
        this.exchangeBusinessRateMapper = exchangeBusinessRateMapper;
    }

    /**
     * 查询指定时点的交易汇率。
     * <p>
     * EDC 场景下，商户标签币种不被渠道能力支持时，支付核心按 baseCurrency 到 quoteCurrency
     * 查询 TRANSACTION_RATE；找不到有效汇率时必须拒绝交易，禁止按默认汇率误上送渠道。
     *
     * @param baseCurrency  商户标签币种
     * @param quoteCurrency 渠道路由后的交易币种
     * @param atTime        交易发生时间，空值时使用当前时间
     * @return 当前有效交易汇率
     */
    @Override
    public Optional<PaymentExchangeRateDTO> findTransactionRate(String baseCurrency, String quoteCurrency, LocalDateTime atTime) {
        if (!StringUtils.hasText(baseCurrency) || !StringUtils.hasText(quoteCurrency)) {
            return Optional.empty();
        }
        LocalDateTime queryTime = atTime == null ? LocalDateTime.now() : atTime;
        ExchangeBusinessRateDO rateDO = exchangeBusinessRateMapper.selectOne(Wrappers.<ExchangeBusinessRateDO>lambdaQuery()
                .eq(ExchangeBusinessRateDO::getDeleted, NOT_DELETED)
                .eq(ExchangeBusinessRateDO::getRateType, TRANSACTION_RATE)
                .eq(ExchangeBusinessRateDO::getRateStatus, RATE_STATUS_ENABLED)
                .eq(ExchangeBusinessRateDO::getBaseCurrency, normalizeCurrency(baseCurrency))
                .eq(ExchangeBusinessRateDO::getQuoteCurrency, normalizeCurrency(quoteCurrency))
                .le(ExchangeBusinessRateDO::getEffectiveTime, queryTime)
                .and(wrapper -> wrapper.isNull(ExchangeBusinessRateDO::getExpireTime)
                        .or()
                        .gt(ExchangeBusinessRateDO::getExpireTime, queryTime))
                .orderByDesc(ExchangeBusinessRateDO::getEffectiveTime)
                .orderByDesc(ExchangeBusinessRateDO::getId)
                .last("LIMIT 1"));
        if (rateDO == null || rateDO.getFinalRate() == null) {
            return Optional.empty();
        }
        PaymentExchangeRateDTO dto = new PaymentExchangeRateDTO();
        dto.setRateId(rateDO.getId());
        dto.setSourceCode(rateDO.getSourceCode());
        dto.setBaseCurrency(rateDO.getBaseCurrency());
        dto.setQuoteCurrency(rateDO.getQuoteCurrency());
        dto.setFinalRate(rateDO.getFinalRate());
        dto.setEffectiveTime(rateDO.getEffectiveTime());
        return Optional.of(dto);
    }

    /**
     * 归一化 ISO 4217 币种代码。
     *
     * @param value 原始币种代码
     * @return 大写币种代码
     */
    private String normalizeCurrency(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }
}
