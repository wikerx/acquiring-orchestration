package com.scott.payment.finance.money.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Objects;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : Money
 * @date : 2026-08-25 00:00
 * @email : scott_x@163.com
 * @description : 跨费用、保证金和结算领域复用的不可变金额值对象；保留原始精度且不隐式换汇或舍入。
 * @status : create
 * @param amount 有符号业务金额
 * @param currency ISO 4217 三位币种代码
 * @param exponent 当前业务事实固化的币种小数位
 */
public record Money(BigDecimal amount, String currency, int exponent) {

    public Money {
        Objects.requireNonNull(amount, "amount is required");
        currency = normalizeCurrency(currency);
        if (exponent < 0 || exponent > 8) {
            throw new IllegalArgumentException("currency exponent must be between 0 and 8");
        }
    }

    /**
     * 使用调用方明确指定的规则按当前币种精度舍入。
     *
     * @param roundingMode 业务版本固化的舍入规则
     * @return 舍入后的新金额
     */
    public Money rounded(RoundingMode roundingMode) {
        return new Money(amount.setScale(exponent, Objects.requireNonNull(roundingMode)), currency, exponent);
    }

    /**
     * 判断两个金额是否具有相同币种和业务精度。
     *
     * @param other 待比较金额
     * @return 币种和 exponent 均一致时返回 true
     */
    public boolean sameCurrency(Money other) {
        return other != null && currency.equals(other.currency) && exponent == other.exponent;
    }

    private static String normalizeCurrency(String currency) {
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("currency is required");
        }
        String normalized = currency.trim().toUpperCase(Locale.ROOT);
        if (normalized.length() != 3) {
            throw new IllegalArgumentException("currency must be an ISO 4217 alpha-3 code");
        }
        return normalized;
    }
}
