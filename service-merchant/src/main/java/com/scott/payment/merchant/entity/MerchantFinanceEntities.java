package com.scott.payment.merchant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 商户端当前费率、资金账户持久化查询模型，以及从交易数据实时计算的在途余额投影。
 */
public final class MerchantFinanceEntities {

    /**
     * 禁止实例化商户资金实体聚合类。
     */
    private MerchantFinanceEntities() {
    }

    /**
     * 当前商户费用方案。
     */
    @Data @TableName("fee_plan")
    public static class FeePlanDO {
        @TableId(type = IdType.AUTO) private Long id;
        private String planName;
        private String planType;
        private String merchantId;
        private Long currentVersionId;

        private Integer currentVersionNo;
        private String status;
        /**
         * 逻辑删除标识；0 表示有效，1 表示已删除，查询必须沿用统一软删除口径。
         */
        private Long deleted;
    }

    /**
     * 当前生效费用版本。
     */
    @Data @TableName("fee_plan_version")
    public static class FeePlanVersionDO {
        @TableId(type = IdType.AUTO) private Long id;
        private Long planId;
        /**
         * 业务版本号，用于区分同一配置或方案的不可变版本。
         */
        private Integer versionNo;
        private String versionStatus;
        /**
         * 持久化的保证金汇率，用于还原当前记录的业务事实。
         */
        private BigDecimal reserveRate;
        /**
         * 持久化的保证金延迟天数，用于还原当前记录的业务事实。
         */
        private Integer reserveDelayDays;
        private String initialDelayUnit;
        /**
         * 持久化的初始延迟天数，用于还原当前记录的业务事实。
         */
        private Integer initialDelayDays;
        private Integer regularDelayDays;
        /**
         * 持久化的结算频率，用于还原当前记录的业务事实。
         */
        private String settlementFrequency;
        /**
         * 持久化的频率日，用于还原当前记录的业务事实。
         */
        private Integer frequencyDay;
        /**
         * 业务配置或汇率开始生效的具体时刻。
         */
        private LocalDateTime effectiveTime;
        /**
         * 逻辑删除标识；0 表示有效，1 表示已删除，查询必须沿用统一软删除口径。
         */
        private Long deleted;
    }

    /**
     * 当前版本费用规则。
     */
    @Data @TableName("fee_rule")
    public static class FeeRuleDO {
        @TableId(type = IdType.AUTO) private Long id;
        private Long planVersionId;
        /**
         * 同一条页面多选规则展开后的分组编码；历史原子规则允许为空。
         */
        private String ruleGroupCode;
        /**
         * 费用类别，用于区分交易手续费、退款费、风控费、争议费和结算换汇费。
         */
        private String feeCategory;
        /**
         * 费用规则名称，用于运营识别同一费用版本内的原子匹配规则。
         */
        private String ruleName;
        /**
         * 交易类型，标识本次动作是支付、授权、请款、退款、撤销还是增量授权，用于选择状态机和渠道能力。
         */
        private String transactionType;
        private String paymentType;
        private String paymentMethod;
        /**
         * 风控服务类型，用于区分内部风控、外部风控和 3DS 服务费用。
         */
        private String riskServiceType;
        /**
         * 计费触发点，明确费用在请求、成功、失败或其它受控事件发生时计提。
         */
        private String chargeTrigger;
        /**
         * 费用计算模式，决定当前规则采用标准费率还是阶梯费率。
         */
        private String feeMode;
        /**
         * 百分比数值，例如 2.3 表示 2.3%，按标签金额计提。
         */
        private BigDecimal percentageRate;
        /**
         * 固定费用，币种恒为 USD。
         */
        private BigDecimal fixedAmountUsd;
        /**
         * 最低费用，币种恒为 USD；不设下限时为空。
         */
        private BigDecimal minimumAmountUsd;
        /**
         * 最高费用，币种恒为 USD；不设上限时为空。
         */
        private BigDecimal maximumAmountUsd;
        /**
         * 阶梯累计指标，用于区分月累计交易笔数和 USD 归一交易金额。
         */
        private String tierMetric;
        /**
         * 阶梯累计周期，当前用于声明费用阶梯按哪个统计周期重置。
         */
        private String tierPeriod;
        /**
         * 排序号，数值越小越优先展示或匹配。
         */
        private Integer sortNo;
        /**
         * 逻辑删除标识；0 表示有效，1 表示已删除，查询必须沿用统一软删除口径。
         */
        private Long deleted;
    }

    /**
     * 当前规则阶梯。
     */
    @Data @TableName("fee_rule_tier")
    public static class FeeRuleTierDO {
        @TableId(type = IdType.AUTO) private Long id;
        private Long feeRuleId;
        /**
         * 月累计笔数或 USD 归一金额下界，包含。
         */
        private BigDecimal lowerBound;
        /**
         * 月累计笔数或 USD 归一金额上界，不包含；末档为空。
         */
        private BigDecimal upperBound;
        /**
         * 当前档百分比数值，例如 2.3 表示 2.3%。
         */
        private BigDecimal percentageRate;
        /**
         * 当前档固定费用，币种恒为 USD。
         */
        private BigDecimal fixedAmountUsd;
        /**
         * 当前档最低费用，币种恒为 USD；不设下限时为空。
         */
        private BigDecimal minimumAmountUsd;
        /**
         * 当前档最高费用，币种恒为 USD；不设上限时为空。
         */
        private BigDecimal maximumAmountUsd;
        /**
         * 排序号，数值越小越优先展示或匹配。
         */
        private Integer sortNo;
        /**
         * 逻辑删除标识；0 表示有效，1 表示已删除，查询必须沿用统一软删除口径。
         */
        private Long deleted;
    }

    /**
     * 当前商户资金账户。
     */
    @Data @TableName("merchant_fund_account")
    public static class FundAccountDO {
        /**
         * 资金账户数据库主键。
         */
        @TableId(type = IdType.AUTO) private Long id;
        private String accountNo;
        /**
         * 账户所属商户号，所有商户端查询必须以此隔离。
         */
        private String merchantId;
        /**
         * ISO 4217 三位结算币种；一期每个商户仅一个资金账户。
         */
        private String settlementCurrency;
        /**
         * 商户可提现余额，单位为 settlementCurrency，允许为负。
         */
        private BigDecimal availableBalance;
        /**
         * 人工账户状态：NORMAL、FROZEN 或 CLOSED。
         */
        private String accountStatus;
        /**
         * 账户并发版本号，商户端仅展示不修改。
         */
        private Long accountVersion;
        /**
         * 账户创建系统时间。
         */
        private LocalDateTime createTime;
        /**
         * 账户最近修改系统时间。
         */
        private LocalDateTime updateTime;
        /**
         * 逻辑删除标识，零表示有效。
         */
        private Long deleted;
    }

    /**
     * 当前商户不可变余额流水。
     */
    @Data @TableName("merchant_fund_ledger")
    public static class FundLedgerDO {
        /**
         * 余额流水数据库主键。
         */
        @TableId(type = IdType.AUTO) private Long id;
        private String ledgerNo;
        /**
         * 流水所属商户号，商户端查询必须精确匹配认证商户号。
         */
        private String merchantId;
        /**
         * 流水所属资金账户主键。
         */
        private Long accountId;
        /**
         * 余额变动业务类型。
         */
        private String businessType;
        /**
         * 面向商户核对的变动摘要。
         */
        private String summary;
        /**
         * 来源业务单号。
         */
        private String businessNo;
        /**
         * 关联交易号，非交易类变动时为空。
         */
        private String transactionId;
        /**
         * ISO 4217 三位币种代码。
         */
        private String currency;
        /**
         * CREDIT 表示余额增加，DEBIT 表示余额减少。
         */
        private String direction;
        /**
         * 发生金额，单位为 currency，始终以非负数保存。
         */
        private BigDecimal amount;
        /**
         * 入账前余额，单位为 currency，可为负。
         */
        private BigDecimal balanceBefore;
        /**
         * 入账后余额，单位为 currency，可为负。
         */
        private BigDecimal balanceAfter;
        /**
         * 账户内严格递增序号，用于商户核对连续余额。
         */
        private Long accountSequence;
        /**
         * 原操作人名称快照。
         */
        private String operatorName;
        /**
         * 最终复核人名称快照，自动入账时允许为空。
         */
        private String reviewerName;
        /**
         * 人工余额变动的完整业务原因，自动入账时允许为空。
         */
        private String operationReason;
        /**
         * 审核和复核意见摘要，自动入账时允许为空。
         */
        private String reviewComment;
        /**
         * 来源业务事件发生系统时间。
         */
        private LocalDateTime businessTime;
        /**
         * 可用余额实际发生变化的系统时间。
         */
        private LocalDateTime postedTime;
        /**
         * 链路追踪号，允许为空且不向商户响应暴露。
         */
        private String traceId;
    }

    /**
     * 按标签币种聚合的当前商户在途余额查询投影，不映射独立数据库表。
     */
    @Data
    public static class PendingBalanceAggregate {
        /**
         * 标签金额 ISO 4217 三位币种，不允许为空。
         */
        private String currency;
        /**
         * 成功未结算正向金额减退款、拒付金额后的在途净额，单位由 currency 决定。
         */
        private BigDecimal amount;
    }

    /**
     * 按原标签币种聚合的未完成保证金结算余额查询投影。
     */
    @Data
    public static class ReserveBalanceAggregate {
        /**
         * 保证金原标签 ISO 4217 三位币种。
         */
        private String currency;
        /**
         * 尚未完成保证金结算入账的净额。
         */
        private BigDecimal amount;
    }

    /**
     * 当前商户保证金明细。
     */
    @Data @TableName("merchant_reserve_item")
    public static class ReserveItemDO {
        /**
         * 保证金明细数据库主键。
         */
        @TableId(type = IdType.AUTO) private Long id;
        private String reserveNo;
        /**
         * 保证金归属资金账户主键。
         */
        private Long accountId;
        /**
         * 保证金所属商户号。
         */
        private String merchantId;
        /**
         * 产生保证金的来源交易号，允许为空。
         */
        private String sourceTransactionId;
        /**
         * 产生保证金的来源业务单号。
         */
        private String sourceBusinessNo;
        /**
         * 保证金 ISO 4217 三位币种，等于账户结算币种。
         */
        private String currency;
        /**
         * 原始留存金额，单位为 currency。
         */
        private BigDecimal retainedAmount;
        /**
         * 累计释放金额，单位为 currency。
         */
        private BigDecimal releasedAmount;
        /**
         * HELD、RELEASABLE、RELEASED、FROZEN 或 DEDUCTED。
         */
        private String reserveStatus;
        /**
         * 按滚动保证金周期计算的预计释放日期，未计算时为空。
         */
        private LocalDate expectedReleaseDate;
        /**
         * 保证金结算批次号，尚未结算时为空。
         */
        private String releaseBatchNo;
        /**
         * 保证金明细创建系统时间。
         */
        private LocalDateTime createTime;
        /**
         * 保证金明细最近修改系统时间。
         */
        private LocalDateTime updateTime;
    }
}
