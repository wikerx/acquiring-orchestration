package com.scott.payment.settlement.entity;

import lombok.Data;

import java.time.LocalTime;

/**
 * 手动结算预览所需的结算档案、资金账户和当前交易周期只读投影。
 */
@Data
public class SettlementManualReviewProfileDO {

    private Long settlementProfileId;
    /**
     * 商户号，用于限定商户配置、交易数据、风控规则和权限归属。
     */
    private String merchantId;

    private Long settlementAccountId;
    /**
     * 持久化的结算账号编号，用于还原当前记录的业务事实。
     */
    private String settlementAccountNo;

    private String targetCurrency;

    private Integer targetCurrencyExponent;
    /**
     * {@code businessTimeZone}，使用 IANA 时区标识解释关联的本地日期时间。
     */
    private String businessTimeZone;
    /**
     * 持久化的{@code dailyCutoffTime}，用于还原当前记录的业务事实。
     */
    private LocalTime dailyCutoffTime;
    /**
     * 持久化的{@code initialDelayUnit}，用于还原当前记录的业务事实。
     */
    private String initialDelayUnit;
    /**
     * 持久化的初始延迟天数，用于还原当前记录的业务事实。
     */
    private Integer initialDelayDays;
    /**
     * 持久化的{@code regularDelayDays}，用于还原当前记录的业务事实。
     */
    private Integer regularDelayDays;
    /**
     * 持久化的结算频率，用于还原当前记录的业务事实。
     */
    private String settlementFrequency;
    /**
     * 持久化的频率日，用于还原当前记录的业务事实。
     */
    private Integer frequencyDay;
}
