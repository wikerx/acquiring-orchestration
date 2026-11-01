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
 * 支付核心使用的交易汇率只读服务。
 *
 * <p>汇率方向固定为 baseCurrency 到 quoteCurrency，并按交易发生时点选择最近生效且未过期的
 * TRANSACTION_RATE。缺失汇率返回空结果，由调用方 Fail Closed；本服务不提供默认汇率、反向推导
 * 或结算汇率替代。</p>
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
        // 同一币对可能存在多条历史版本，按生效时间和主键倒序锁定交易时点唯一版本。
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
        // 原样返回数据库 BigDecimal 汇率，不在查询层提前换算金额或舍入。
        dto.setRateId(rateDO.getId());
        dto.setRawRateId(rateDO.getRawRateId());
        dto.setRuleId(rateDO.getRuleId());
        dto.setSourceCode(rateDO.getSourceCode());
        dto.setBaseCurrency(rateDO.getBaseCurrency());
        dto.setQuoteCurrency(rateDO.getQuoteCurrency());
        dto.setFinalRate(rateDO.getFinalRate());
        dto.setAdjustDescription(rateDO.getAdjustDescription());
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
