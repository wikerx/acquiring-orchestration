package com.scott.payment.finance.settlement.core;

import com.scott.payment.finance.settlement.model.SettlementRateModels.CurrencyPair;
import com.scott.payment.finance.settlement.model.SettlementRateModels.LockedRate;
import com.scott.payment.finance.settlement.model.SettlementRateModels.QuoteDirection;
import com.scott.payment.finance.settlement.model.SettlementRateModels.RateQuote;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SettlementRateNormalizer
 * @date : 2026-08-26 20:00
 * @email : scott_x@163.com
 * @description : 将直接、反向和同币种报价固化为 12 位小数的批次直接汇率，确保计算值与数据库锁定值一致。
 * @status : create
 */
public final class SettlementRateNormalizer {

    /** 结算批次汇率固定持久化精度。 */
    public static final int RATE_SCALE = 12;

    /**
     * 财务计算统一 MathContext，约束中间计算精度并避免过早舍入。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；不允许为空；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final MathContext CALCULATION_CONTEXT = MathContext.DECIMAL128;
    /**
     * 汇率归一时的统一舍入模式，仅在锁定精度边界使用。
     * <p>
     * 单位：比例值；格式：decimal，按费率或汇率精度保存；不允许为空；非敏感字段。
     * 取值范围：取值范围由费率、汇率或预警配置定义；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final RoundingMode RATE_ROUNDING_MODE = RoundingMode.HALF_EVEN;

    /**
     * 把原始报价归一成一单位源币种对应目标币种的直接汇率。
     *
     * @param quote 原始结算报价
     * @param sourceCurrencyExponent 原币种 ISO 小数位
     * @param targetCurrencyExponent 目标币种 ISO 小数位
     * @return 可直接持久化到批次汇率矩阵的锁定值
     */
    public LockedRate normalize(RateQuote quote, int sourceCurrencyExponent, int targetCurrencyExponent) {
        Objects.requireNonNull(quote, "rate quote is required");
        if (quote.pair().sourceCurrency().equals(quote.pair().targetCurrency())) {
            if (quote.quotedRate().compareTo(BigDecimal.ONE) != 0
                    || quote.quoteDirection() != QuoteDirection.DIRECT
                    || !"SYSTEM_IDENTITY".equals(quote.rateSource())) {
                throw new IllegalArgumentException("same-currency quote must be a direct system identity rate");
            }
            return identity(quote.pair().sourceCurrency(), sourceCurrencyExponent, quote.effectiveTime());
        }
        BigDecimal directRate = quote.quoteDirection() == QuoteDirection.DIRECT
                ? quote.quotedRate()
                : BigDecimal.ONE.divide(quote.quotedRate(), CALCULATION_CONTEXT);
        return new LockedRate(
                quote.pair(),
                directRate.setScale(RATE_SCALE, RATE_ROUNDING_MODE),
                sourceCurrencyExponent,
                targetCurrencyExponent,
                quote.rateSource(),
                quote.quoteId(),
                quote.quoteDirection(),
                quote.effectiveTime());
    }

    /**
     * 创建必须写入矩阵的同币种恒等汇率，避免同币种结果绕过批次汇率审计。
     *
     * @param currency 源和目标使用的 ISO 币种
     * @param exponent 币种 ISO 小数位
     * @param effectiveTime 恒等汇率生效时间
     * @return SYSTEM_IDENTITY 锁定汇率
     */
    public LockedRate identity(String currency, int exponent, LocalDateTime effectiveTime) {
        CurrencyPair pair = new CurrencyPair(currency, currency);
        return new LockedRate(
                pair,
                BigDecimal.ONE.setScale(RATE_SCALE),
                exponent,
                exponent,
                "SYSTEM_IDENTITY",
                null,
                QuoteDirection.DIRECT,
                Objects.requireNonNull(effectiveTime, "rate effective time is required"));
    }
}
