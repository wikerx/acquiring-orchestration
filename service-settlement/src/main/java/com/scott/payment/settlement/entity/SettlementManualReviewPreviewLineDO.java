package com.scott.payment.settlement.entity;

import lombok.Data;

import java.math.BigDecimal;

/** 按来源币种聚合的手动交易或保证金结算候选统计，不跨币种直接相加。 */
@Data
public class SettlementManualReviewPreviewLineDO {
    private String sourceCurrency;
    private Integer sourceCurrencyExponent;
    private Long transactionCount;
    private BigDecimal grossAmount;
    private BigDecimal platformFeeAmount;
    private BigDecimal reserveAmount;
    private BigDecimal releasedReserveAmount;
    private BigDecimal netSettlementAmount;
    private Long pendingFeeCount;
    private String reserveDelayUnit;
    private Integer minimumReserveDelayDays;
    private Integer maximumReserveDelayDays;
    private java.time.LocalDate earliestExpectedReleaseDate;
    private java.time.LocalDate latestExpectedReleaseDate;
}
