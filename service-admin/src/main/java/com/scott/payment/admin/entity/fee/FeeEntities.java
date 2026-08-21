package com.scott.payment.admin.entity.fee;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : FeeEntities
 * @date : 2026-08-18 00:00
 * @email : scott_x@163.com
 * @description : 费用方案、可编辑草稿、审核后不可变版本、规则、阶梯和试算记录的持久化模型集合。
 * @status : create
 */
public final class FeeEntities {

    private FeeEntities() {
    }

    /** 费用模板或商户费用方案主记录。 */
    @Data
    @TableName("fee_plan")
    public static class FeePlanDO {
        @TableId(type = IdType.AUTO)
        private Long id;
        private String planCode;
        private String planName;
        /** TEMPLATE 或 MERCHANT；商户方案必须关联 merchantId。 */
        private String planType;
        /** 商户号；模板方案为空。 */
        private String merchantId;
        /** 商户配置复制或调整时的来源模板主键，可为空。 */
        private Long sourceTemplateId;
        /** 复制时锁定的模板版本号，可为空且不随模板后续变更。 */
        private Integer sourceTemplateVersionNo;
        /** TEMPLATE、TEMPLATE_CUSTOMIZED 或 INDEPENDENT。 */
        private String originType;
        /** 当前已审核生效版本主键；未生效时为空。 */
        private Long currentVersionId;
        /** 当前已审核生效版本号；未生效时为空。 */
        private Integer currentVersionNo;
        /** ENABLED、DISABLED 或 ARCHIVED；归档代替物理删除。 */
        private String status;
        private String remark;
        private String createBy;
        private LocalDateTime createTime;
        private String updateBy;
        private LocalDateTime updateTime;
        private Long deleted;
    }

    /** 费用方案版本；草稿可编辑，提交审核后保持不可变。 */
    @Data
    @TableName("fee_plan_version")
    public static class FeePlanVersionDO {
        @TableId(type = IdType.AUTO)
        private Long id;
        private Long planId;
        /** 方案内从 1 递增且不复用的版本号。 */
        private Integer versionNo;
        /** DRAFT、PENDING_REVIEW、ACTIVE、REJECTED 或 SUPERSEDED。 */
        private String versionStatus;
        private String changeType;
        private Long sourceTemplateId;
        private Integer sourceTemplateVersionNo;
        private String originType;
        /** 滚动保证金比例，例如 10 表示 10%。 */
        private BigDecimal reserveRate;
        /** 滚动保证金留存周期单位：T 工作日、D 自然日。 */
        private String reserveDelayUnit;
        /** 滚动保证金 T/D+N 留存天数。 */
        private Integer reserveDelayDays;
        /** 商户单一结算币种快照；模板版本为空，商户版本使用 ISO 4217 三位代码。 */
        private String settlementCurrency;
        /** 首次与常规结算周期共用的单位。 */
        private String initialDelayUnit;
        /** 首次结算延迟天数，最小为 1。 */
        private Integer initialDelayDays;
        /** 常规结算延迟天数，最小为 1。 */
        private Integer regularDelayDays;
        /** DAILY、WEEKLY、BIWEEKLY 或 MONTHLY。 */
        private String settlementFrequency;
        /** 周结为 1 至 7，月结为 1 至 28，日结为空。 */
        private Integer frequencyDay;
        private String changeReason;
        /** 草稿阶段为最后保存账号，提交后为本次提交账号。 */
        private Long submitById;
        /** 草稿阶段为最后保存人，提交后为本次提交人名称快照。 */
        private String submitByName;
        /** 草稿阶段为最后保存时间，提交后为提交审核时间，不等同于生效时间。 */
        private LocalDateTime submitTime;
        /** 审核账号 ID；待审核时为空，且不能等于提交账号。 */
        private Long reviewById;
        private String reviewByName;
        private String reviewComment;
        /** 审核动作系统时间；待审核时为空。 */
        private LocalDateTime reviewTime;
        /** 审核通过时间即生效时间；未通过时为空。 */
        private LocalDateTime effectiveTime;
        /** 被后续版本替代的系统时间；当前版本为空。 */
        private LocalDateTime supersededTime;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;
        private Long deleted;
    }

    /** 单个交易匹配维度下的费用规则。 */
    @Data
    @TableName("fee_rule")
    public static class FeeRuleDO {
        @TableId(type = IdType.AUTO)
        private Long id;
        private Long planVersionId;
        /** 同一条页面多选规则展开后的分组编码；历史原子规则允许为空。 */
        private String ruleGroupCode;
        /** TRANSACTION_FEE、REFUND_FEE、RISK_FEE、DISPUTE_FEE 或 SETTLEMENT_FX_FEE。 */
        private String feeCategory;
        private String ruleName;
        private String transactionType;
        private String paymentType;
        private String paymentMethod;
        /** INTERNAL、EXTERNAL、THREE_DS；非风控费用使用 NONE。 */
        private String riskServiceType;
        /** NO_CHARGE、SUCCESS、SUCCESS_OR_FAILURE、ON_CALL；非风控费用使用 NOT_APPLICABLE。 */
        private String chargeTrigger;
        private String feeMode;
        /** 百分比数值，例如 2.3 表示 2.3%，按标签币种金额计提。 */
        private BigDecimal percentageRate;
        /** 固定费用，币种恒为 USD，不能为空。 */
        private BigDecimal fixedAmountUsd;
        /** 最低费用，币种恒为 USD；不设下限时为空。 */
        private BigDecimal minimumAmountUsd;
        /** 最高费用，币种恒为 USD；不设上限时为空。 */
        private BigDecimal maximumAmountUsd;
        /** COUNT 或 AMOUNT；标准费率时为空。 */
        private String tierMetric;
        /** 阶梯累计周期，本期固定为 MONTH；标准费率时为空。 */
        private String tierPeriod;
        private Integer sortNo;
        private String remark;
        private LocalDateTime createTime;
        private Long deleted;
    }

    /** 月累计笔数或 USD 金额阶梯。 */
    @Data
    @TableName("fee_rule_tier")
    public static class FeeRuleTierDO {
        @TableId(type = IdType.AUTO)
        private Long id;
        private Long feeRuleId;
        /** 月累计笔数或归一到 USD 的累计金额下界，包含。 */
        private BigDecimal lowerBound;
        /** 月累计笔数或归一到 USD 的累计金额上界，不包含；末档为空。 */
        private BigDecimal upperBound;
        /** 当前档百分比数值，例如 2.3 表示 2.3%。 */
        private BigDecimal percentageRate;
        /** 当前档固定费用，币种恒为 USD。 */
        private BigDecimal fixedAmountUsd;
        /** 当前档最低费用，币种恒为 USD；不设下限时为空。 */
        private BigDecimal minimumAmountUsd;
        /** 当前档最高费用，币种恒为 USD；不设上限时为空。 */
        private BigDecimal maximumAmountUsd;
        private Integer sortNo;
        private LocalDateTime createTime;
        private Long deleted;
    }

    /** 无副作用试算的输入和结果快照。 */
    @Data
    @TableName("fee_simulation_record")
    public static class FeeSimulationRecordDO {
        @TableId(type = IdType.AUTO)
        private Long id;
        private String simulationNo;
        private Long planVersionId;
        private String merchantId;
        private String feeCategory;
        private String transactionType;
        private String paymentType;
        private String paymentMethod;
        /** INTERNAL、EXTERNAL、THREE_DS；非风控费用试算使用 NONE。 */
        private String riskServiceType;
        /** 试算标签金额，币种由 labelCurrency 指定，不做展示层舍入。 */
        private BigDecimal labelAmount;
        /** 标签金额 ISO 4217 三位币种代码。 */
        private String labelCurrency;
        /** 系统选用的标签币种到 USD 正向结算汇率，禁止取反向汇率倒数。 */
        private BigDecimal labelToUsdRate;
        /** 系统业务汇率记录 ID；USD 恒等汇率允许为空。 */
        private Long settlementRateId;
        /** 本次试算选用的汇率来源编码，不包含敏感信息。 */
        private String settlementRateSource;
        /** 被选汇率的生效时间，使用系统业务时间。 */
        private LocalDateTime rateEffectiveTime;
        /** 本次试算解析汇率的估值时间，使用系统业务时间。 */
        private LocalDateTime rateValuationTime;
        /** 本次交易发生前的当月累计笔数。 */
        private Long monthlyCountBefore;
        /** 本次交易发生前的当月累计金额，已归一为 USD。 */
        private BigDecimal monthlyAmountUsdBefore;
        private Long matchedRuleId;
        private Long matchedTierId;
        /** 按标签币种计算的百分比费用，尚未换算为 USD。 */
        private BigDecimal percentageFeeLabel;
        /** 百分比费用换算并叠加固定费用后的 USD 金额，尚未应用上下限。 */
        private BigDecimal rawFeeUsd;
        /** 应用最低和最高费用后的最终 USD 试算金额。 */
        private BigDecimal finalFeeUsd;
        private BigDecimal reserveAmountUsd;
        private BigDecimal estimatedNetSettlementUsd;
        private String formulaSnapshot;
        private Long operatorId;
        private String operatorName;
        private LocalDateTime createTime;
    }
}
