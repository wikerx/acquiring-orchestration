package com.scott.payment.settlement.entity;

import lombok.Data;

import java.time.LocalTime;

/** 手动结算预览所需的结算档案、资金账户和当前交易周期只读投影。 */
@Data
public class SettlementManualReviewProfileDO {
    private Long settlementProfileId;
    private String merchantId;
    private Long settlementAccountId;
    private String settlementAccountNo;
    private String targetCurrency;
    private Integer targetCurrencyExponent;
    private String businessTimeZone;
    private LocalTime dailyCutoffTime;
    private String initialDelayUnit;
    private Integer initialDelayDays;
    private Integer regularDelayDays;
    private String settlementFrequency;
    private Integer frequencyDay;
}
