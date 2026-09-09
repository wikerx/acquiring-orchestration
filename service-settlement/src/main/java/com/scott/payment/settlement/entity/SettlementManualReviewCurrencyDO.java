package com.scott.payment.settlement.entity;

import lombok.Data;

/** 手动预审范围内需要冻结汇率的来源币种及 exponent。 */
@Data
public class SettlementManualReviewCurrencyDO {
    private String currency;
    private Integer currencyExponent;
}
