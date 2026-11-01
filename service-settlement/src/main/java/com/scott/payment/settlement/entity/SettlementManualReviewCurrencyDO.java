package com.scott.payment.settlement.entity;

import lombok.Data;

/**
 * 手动预审范围内需要冻结汇率的来源币种及 exponent。
 */
@Data
public class SettlementManualReviewCurrencyDO {

    private String currency;
    /**
     * 交易币种的小数位数，用于主币种单位与最小货币单位之间的精确转换。
     */
    private Integer currencyExponent;
}
