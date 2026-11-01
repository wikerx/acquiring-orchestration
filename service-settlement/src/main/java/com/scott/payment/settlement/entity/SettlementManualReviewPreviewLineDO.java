package com.scott.payment.settlement.entity;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 按来源币种聚合的手动交易或保证金结算候选统计，不跨币种直接相加。
 */
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
    /**
     * 持久化的{@code reserveDelayUnit}，用于还原当前记录的业务事实。
     */
    private String reserveDelayUnit;
    /**
     * 持久化的{@code minimumReserveDelayDays}，用于还原当前记录的业务事实。
     */
    private Integer minimumReserveDelayDays;
    /**
     * 持久化的{@code maximumReserveDelayDays}，用于还原当前记录的业务事实。
     */
    private Integer maximumReserveDelayDays;
    /**
     * 持久化的{@code earliestExpectedReleaseDate}，用于还原当前记录的业务事实。
     */
    private java.time.LocalDate earliestExpectedReleaseDate;
    /**
     * 持久化的{@code latestExpectedReleaseDate}，用于还原当前记录的业务事实。
     */
    private java.time.LocalDate latestExpectedReleaseDate;
}
