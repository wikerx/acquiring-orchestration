package com.scott.payment.merchant.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantFinanceDTOs
 * @date : 2026-08-18 00:00
 * @email : scott_x@163.com
 * @description : 商户端当前费率和资金账户只读响应模型，不包含模板库来源元数据。
 * @status : create
 */
public final class MerchantFinanceDTOs {
    private MerchantFinanceDTOs() { }

    /** 只读明细分页条件。 */
    @Data
    public static class DetailQuery {
        /** 页码，从 1 开始。 */
        private int pageNo = 1;
        /** 每页条数，服务端限制为 1 至 200。 */
        private int pageSize = 10;
        /** 流水号、业务单号、交易号或摘要关键字，允许为空。 */
        private String keyword;
        /** AVAILABLE 或 RESERVE，允许为空。 */
        private String balanceType;
        /** 余额变动业务类型，允许为空。 */
        private String businessType;
        /** 入账时间范围起点，包含，使用系统时间。 */
        private LocalDateTime postedStartTime;
        /** 入账时间范围终点，包含，使用系统时间。 */
        private LocalDateTime postedEndTime;
        /** @return 修正为至少 1 的页码。 */
        public int safePageNo() { return Math.max(pageNo, 1); }
        /** @return 修正到 1 至 200 范围内的每页条数。 */
        public int safePageSize() { return Math.min(Math.max(pageSize, 1), 200); }
    }

    /** 费率阶梯。 */
    @Data
    public static class FeeTierResponse {
        private Long id;
        /** 月累计笔数或 USD 归一金额下界，包含。 */
        private BigDecimal lowerBound;
        /** 月累计笔数或 USD 归一金额上界，不包含；末档为空。 */
        private BigDecimal upperBound;
        /** 百分比数值，例如 2.3 表示 2.3%。 */
        private BigDecimal percentageRate;
        /** 固定费用，币种恒为 USD。 */
        private BigDecimal fixedAmountUsd;
        /** 最低费用，币种恒为 USD；不设下限时为空。 */
        private BigDecimal minimumAmountUsd;
        /** 最高费用，币种恒为 USD；不设上限时为空。 */
        private BigDecimal maximumAmountUsd;
    }

    /** 当前生效费率规则。 */
    @Data
    public static class FeeRuleResponse {
        private Long id;
        private String feeCategory;
        private String ruleName;
        private String transactionType;
        private String paymentType;
        private String paymentMethod;
        private String feeMode;
        /** 百分比数值，例如 2.3 表示 2.3%，按标签金额计提。 */
        private BigDecimal percentageRate;
        /** 固定费用，币种恒为 USD。 */
        private BigDecimal fixedAmountUsd;
        /** 最低费用，币种恒为 USD；不设下限时为空。 */
        private BigDecimal minimumAmountUsd;
        /** 最高费用，币种恒为 USD；不设上限时为空。 */
        private BigDecimal maximumAmountUsd;
        private String tierMetric;
        private String tierPeriod;
        private List<FeeTierResponse> tiers = new ArrayList<>();
    }

    /** 当前商户生效费用配置。 */
    @Data
    public static class CurrentFeeResponse {
        private String displayName;
        private Integer versionNo;
        private LocalDateTime effectiveTime;
        private BigDecimal reserveRate;
        private Integer reserveDelayDays;
        private String initialDelayUnit;
        private Integer initialDelayDays;
        private String regularDelayUnit;
        private Integer regularDelayDays;
        private String settlementFrequency;
        private Integer frequencyDay;
        private List<FeeRuleResponse> rules = new ArrayList<>();
    }

    /** 按币种独立展示的在途余额。 */
    @Data
    public static class CurrencyBalanceResponse {
        /** ISO 4217 三位币种代码。 */
        private String currency;
        /** 该币种独立汇总金额，不与其他币种直接相加。 */
        private BigDecimal amount;
    }

    /** 当前商户资金账户。 */
    @Data
    public static class FundAccountResponse {
        /** 资金账户数据库主键。 */
        private Long id;
        /** 平台资金账户号，不包含敏感银行账号信息。 */
        private String accountNo;
        /** ISO 4217 三位结算币种，一期每个商户仅一个。 */
        private String settlementCurrency;
        /** 可用余额，单位为 settlementCurrency，允许为负。 */
        private BigDecimal availableBalance;
        /** 保证金余额，单位为 settlementCurrency，不允许为负。 */
        private BigDecimal reserveBalance;
        /** 按标签币种分别统计的在途余额。 */
        private List<CurrencyBalanceResponse> pendingBalances = new ArrayList<>();
        /** 人工账户状态：NORMAL、FROZEN 或 CLOSED。 */
        private String accountStatus;
        /** 1 表示负余额限制主动逆向交易，0 表示未限制。 */
        private Integer reverseRestricted;
        /** 当前状态是否允许充值或人工入账。 */
        private Boolean creditAllowed;
        /** 当前状态是否允许资金转出。 */
        private Boolean debitAllowed;
        /** 当前状态是否允许提现。 */
        private Boolean withdrawalAllowed;
        /** 当前状态是否允许交易或保证金结算。 */
        private Boolean settlementAllowed;
        /** 账户状态和负余额规则共同判定的主动逆向交易能力。 */
        private Boolean reverseTransactionAllowed;
        /** 账户最近修改系统时间。 */
        private LocalDateTime updateTime;
    }

    /** 商户余额流水，不展示内部幂等键等运维字段。 */
    @Data
    public static class FundLedgerResponse {
        /** 余额流水数据库主键。 */
        private Long id;
        /** 平台唯一余额流水号。 */
        private String ledgerNo;
        /** AVAILABLE 或 RESERVE。 */
        private String balanceType;
        /** 余额变动业务类型。 */
        private String businessType;
        /** 面向商户核对的变动摘要。 */
        private String summary;
        /** 来源业务单号。 */
        private String businessNo;
        /** 关联交易号，非交易类变动时为空。 */
        private String transactionId;
        /** 本笔变动 ISO 4217 三位币种代码。 */
        private String currency;
        /** CREDIT 表示增加，DEBIT 表示减少。 */
        private String direction;
        /** 发生金额，单位为 currency，始终为非负数。 */
        private BigDecimal amount;
        /** 操作前余额，单位为 currency，可为负。 */
        private BigDecimal balanceBefore;
        /** 操作后余额，单位为 currency，可为负。 */
        private BigDecimal balanceAfter;
        /** 同一账户内严格递增序号，用于核对连续余额。 */
        private Long accountSequence;
        /** 原操作人名称快照。 */
        private String operatorName;
        /** 最终复核人名称快照，自动入账时允许为空。 */
        private String reviewerName;
        /** 来源业务事件发生系统时间。 */
        private LocalDateTime businessTime;
        /** 可用余额实际发生变化的系统时间。 */
        private LocalDateTime postedTime;
    }

}
