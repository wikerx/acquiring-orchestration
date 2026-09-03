package com.scott.payment.finance.settlement.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SettlementRateModels
 * @date : 2026-08-26 20:00
 * @email : scott_x@163.com
 * @description : 定义真实结算批次使用的不可变汇率报价、标准直接汇率和单目标币种汇率矩阵契约。
 * @status : create
 */
public final class SettlementRateModels {

    /**
     * 结算锁定汇率的有效数字精度。
     * <p>
     * 单位：比例值；格式：decimal，按费率或汇率精度保存；不允许为空；非敏感字段。
     * 取值范围：取值范围由费率、汇率或预警配置定义；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final int LOCKED_RATE_PRECISION = 24;
    /**
     * 结算锁定汇率保留的小数位数，至少满足结算汇率精度要求。
     * <p>
     * 单位：比例值；格式：decimal，按费率或汇率精度保存；不允许为空；非敏感字段。
     * 取值范围：取值范围由费率、汇率或预警配置定义；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final int LOCKED_RATE_SCALE = 12;

    private SettlementRateModels() {
    }

    /** 外部报价相对于标准“源币种到目标币种”的方向。 */
    public enum QuoteDirection {
        /**
         * DIRECT 枚举值，表示当前枚举定义中的一个受控业务取值。
         * <p>
         * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
         * </p>
         */
        DIRECT,
        INVERSE
    }

    /**
     * 标准源币种和目标币种对。
     *
     * @param sourceCurrency 一单位待结算原币种
     * @param targetCurrency 商户批次目标结算币种
     */
    public record CurrencyPair(String sourceCurrency, String targetCurrency) {

        public CurrencyPair {
            sourceCurrency = normalizeCurrency(sourceCurrency, "source currency");
            targetCurrency = normalizeCurrency(targetCurrency, "target currency");
        }

        /**
         * 返回用于错误和审计信息的标准币种对。
         *
         * @return SOURCE/TARGET 格式币种对
         */
        public String displayName() {
            return sourceCurrency + "/" + targetCurrency;
        }
    }

    /**
     * 待归一的外部或内部结算报价。
     *
     * @param pair 标准源/目标币种身份
     * @param quotedRate 报价原值，必须为正数
     * @param quoteDirection 报价方向
     * @param rateSource 汇率来源，不允许为空
     * @param quoteId 外部报价或内部汇率记录号；系统恒等汇率可为空
     * @param effectiveTime 报价生效时间
     */
    public record RateQuote(CurrencyPair pair,
                            BigDecimal quotedRate,
                            QuoteDirection quoteDirection,
                            String rateSource,
                            String quoteId,
                            LocalDateTime effectiveTime) {

        public RateQuote {
            Objects.requireNonNull(pair, "currency pair is required");
            requirePositive(quotedRate, "quoted rate");
            Objects.requireNonNull(quoteDirection, "quote direction is required");
            rateSource = requireText(rateSource, "rate source");
            quoteId = normalizeOptionalText(quoteId);
            Objects.requireNonNull(effectiveTime, "rate effective time is required");
        }
    }

    /**
     * 已固化为“一单位源币种对应多少目标币种”的批次直接汇率。
     *
     * @param pair 标准源/目标币种身份
     * @param directRate 批次锁定直接汇率，持久化精度为 12 位小数
     * @param sourceCurrencyExponent 原币种 ISO 小数位
     * @param targetCurrencyExponent 目标币种 ISO 小数位
     * @param rateSource 汇率来源；同币种必须为 SYSTEM_IDENTITY
     * @param quoteId 原始报价号；同币种恒等汇率为空
     * @param sourceQuoteDirection 原报价方向，用于审计
     * @param effectiveTime 报价生效时间
     */
    public record LockedRate(CurrencyPair pair,
                             BigDecimal directRate,
                             int sourceCurrencyExponent,
                             int targetCurrencyExponent,
                             String rateSource,
                             String quoteId,
                             QuoteDirection sourceQuoteDirection,
                             LocalDateTime effectiveTime) {

        public LockedRate {
            Objects.requireNonNull(pair, "currency pair is required");
            requireLockedRateCapacity(directRate);
            requireExponent(sourceCurrencyExponent, "source currency exponent");
            requireExponent(targetCurrencyExponent, "target currency exponent");
            rateSource = requireText(rateSource, "rate source");
            quoteId = normalizeOptionalText(quoteId);
            Objects.requireNonNull(sourceQuoteDirection, "source quote direction is required");
            Objects.requireNonNull(effectiveTime, "rate effective time is required");
            if (pair.sourceCurrency().equals(pair.targetCurrency())
                    && (directRate.compareTo(BigDecimal.ONE) != 0
                    || !"SYSTEM_IDENTITY".equals(rateSource))) {
                throw new IllegalArgumentException("same-currency locked rate must be a system identity rate");
            }
        }
    }

    /**
     * 单个结算批次不可变的币种对矩阵；所有汇率必须指向同一目标币种且币种对唯一。
     *
     * @param rates 批次锁定汇率集合
     * @param targetCurrency 当前批次唯一目标币种
     */
    public record RateMatrix(List<LockedRate> rates, String targetCurrency) {

        public RateMatrix {
            rates = List.copyOf(Objects.requireNonNull(rates, "locked rates are required"));
            if (rates.isEmpty()) {
                throw new IllegalArgumentException("locked rate matrix must not be empty");
            }
            targetCurrency = normalizeCurrency(targetCurrency, "matrix target currency");
            Set<CurrencyPair> pairs = new HashSet<>();
            for (LockedRate rate : rates) {
                Objects.requireNonNull(rate, "locked rate must not be null");
                if (!targetCurrency.equals(rate.pair().targetCurrency())) {
                    throw new IllegalArgumentException("locked rates must use one target currency");
                }
                if (!pairs.add(rate.pair())) {
                    throw new IllegalArgumentException("duplicate locked currency pair: " + rate.pair().displayName());
                }
            }
        }

        /**
         * 从已锁定汇率列表创建单目标币种矩阵。
         *
         * @param rates 批次锁定汇率
         * @return 防御性复制后的不可变矩阵
         */
        public static RateMatrix of(List<LockedRate> rates) {
            List<LockedRate> copy = new ArrayList<>(Objects.requireNonNull(rates, "locked rates are required"));
            if (copy.isEmpty()) {
                throw new IllegalArgumentException("locked rate matrix must not be empty");
            }
            return new RateMatrix(copy, copy.get(0).pair().targetCurrency());
        }

        /**
         * 取得批次内指定币种对的唯一锁定汇率。
         *
         * @param sourceCurrency 原币种
         * @param targetCurrency 目标币种
         * @return 已锁定直接汇率
         * @throws IllegalArgumentException 币种对未锁定时抛出
         */
        public LockedRate require(String sourceCurrency, String targetCurrency) {
            CurrencyPair expected = new CurrencyPair(sourceCurrency, targetCurrency);
            return rates.stream()
                    .filter(rate -> rate.pair().equals(expected))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "locked settlement rate is missing for " + expected.displayName()));
        }
    }

    private static String normalizeCurrency(String value, String fieldName) {
        String normalized = requireText(value, fieldName).toUpperCase(Locale.ROOT);
        if (normalized.length() != 3) {
            throw new IllegalArgumentException(fieldName + " must be an ISO 4217 alpha-3 code");
        }
        return normalized;
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value.trim();
    }

    private static String normalizeOptionalText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static void requirePositive(BigDecimal value, String fieldName) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
    }

    /**
     * 校验锁定汇率能够无损写入数据库 DECIMAL(24,12)。
     * <p>
     * 禁止通过静默截断或四舍五入适配列容量，避免结算计算使用的汇率与审计快照不一致。
     */
    private static void requireLockedRateCapacity(BigDecimal value) {
        requirePositive(value, "direct rate");
        if (value.scale() > LOCKED_RATE_SCALE) {
            throw new IllegalArgumentException("direct rate must not exceed 12 decimal places");
        }
        int integerDigits = value.precision() - value.scale();
        if (integerDigits > LOCKED_RATE_PRECISION - LOCKED_RATE_SCALE) {
            throw new IllegalArgumentException("direct rate exceeds DECIMAL(24,12) capacity");
        }
    }

    private static void requireExponent(int value, String fieldName) {
        if (value < 0 || value > 8) {
            throw new IllegalArgumentException(fieldName + " must be between 0 and 8");
        }
    }
}
