package com.scott.payment.settlement.dto;

import java.util.Locale;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SettlementCurrency
 * @date : 2026-08-26 22:40
 * @email : scott_x@163.com
 * @description : 批次事实中发现的 ISO 币种和 exponent 不可变组合，用于构造完整汇率矩阵并检测精度冲突。
 * @status : create
 * @param currency ISO 4217 三位大写币种
 * @param exponent ISO 小数位，范围 0 至 8
 */
public record SettlementCurrency(String currency, int exponent) {

    public SettlementCurrency {
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("settlement currency is required");
        }
        currency = currency.trim().toUpperCase(Locale.ROOT);
        if (currency.length() != 3 || exponent < 0 || exponent > 8) {
            throw new IllegalArgumentException("settlement currency or exponent is invalid");
        }
    }
}
