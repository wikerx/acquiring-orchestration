package com.scott.payment.admin.dto.fee;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminFeeDTOs
 * @date : 2026-08-18 00:00
 * @email : scott_x@163.com
 * @description : 管理端费用模板、商户费率、审核与试算接口模型。
 * @status : create
 */
public final class AdminFeeDTOs {

    private AdminFeeDTOs() {
    }

    /** 费用方案分页条件。 */
    @Data
    public static class FeePlanQuery {
        private int pageNo = 1;
        private int pageSize = 10;
        private String keyword;
        private String status;
        private String versionStatus;

        public int safePageNo() {
            return Math.max(pageNo, 1);
        }

        public int safePageSize() {
            return Math.min(Math.max(pageSize, 1), 200);
        }
    }

    /** 阶梯费率输入，一律以月累计笔数或 USD 归一金额作为边界。 */
    @Data
    public static class FeeRuleTierRequest {
        /** 月累计笔数或 USD 归一金额下界，包含。 */
        @NotNull
        @DecimalMin("0")
        private BigDecimal lowerBound;
        /** 月累计笔数或 USD 归一金额上界，不包含；末档允许为空。 */
        @DecimalMin("0")
        private BigDecimal upperBound;
        /** 百分比数值，例如 2.3 表示 2.3%。 */
        @NotNull
        @DecimalMin("0")
        private BigDecimal percentageRate = BigDecimal.ZERO;
        /** 固定费用，币种恒为 USD。 */
        @NotNull
        @DecimalMin("0")
        private BigDecimal fixedAmountUsd = BigDecimal.ZERO;
        /** 最低费用，币种恒为 USD；不设下限时为空。 */
        @DecimalMin("0")
        private BigDecimal minimumAmountUsd;
        /** 最高费用，币种恒为 USD；不设上限时为空。 */
        @DecimalMin("0")
        private BigDecimal maximumAmountUsd;
        private Integer sortNo = 0;
    }

    /** 费用规则输入，匹配维度不包含渠道编码。 */
    @Data
    public static class FeeRuleRequest {
        /** 费用分类：TRANSACTION_FEE、REFUND_FEE、RISK_FEE 或 DISPUTE_FEE。 */
        @NotBlank
        private String feeCategory = "TRANSACTION_FEE";
        @NotBlank
        @Size(max = 128)
        private String ruleName;
        @NotBlank
        @Size(max = 64)
        private String transactionType;
        @NotBlank
        @Size(max = 64)
        private String paymentType;
        @NotBlank
        @Size(max = 64)
        private String paymentMethod = "ALL";
        @NotBlank
        private String feeMode = "STANDARD";
        /** 百分比数值，例如 2.3 表示 2.3%，按标签金额计提。 */
        @NotNull
        @DecimalMin("0")
        private BigDecimal percentageRate = BigDecimal.ZERO;
        /** 固定费用，币种恒为 USD。 */
        @NotNull
        @DecimalMin("0")
        private BigDecimal fixedAmountUsd = BigDecimal.ZERO;
        /** 最低费用，币种恒为 USD；不设下限时为空。 */
        @DecimalMin("0")
        private BigDecimal minimumAmountUsd;
        /** 最高费用，币种恒为 USD；不设上限时为空。 */
        @DecimalMin("0")
        private BigDecimal maximumAmountUsd;
        private String tierMetric;
        private String tierPeriod;
        private Integer sortNo = 0;
        @Size(max = 500)
        private String remark;
        @Valid
        private List<FeeRuleTierRequest> tiers = new ArrayList<>();
    }

    /** 新版本共同配置；保存即提交审核，不存在可原地修改的生效版本。 */
    @Data
    public static class FeeVersionSaveRequest {
        /** 滚动保证金比例，例如 10 表示按交易金额留存 10%。 */
        @NotNull
        @DecimalMin("0")
        private BigDecimal reserveRate = BigDecimal.ZERO;
        /** 保证金留存自然日天数；到期后进入下一次保证金结算。 */
        @NotNull
        @Min(1)
        private Integer reserveDelayDays = 180;
        @NotBlank
        private String initialDelayUnit = "T";
        @NotNull
        @Min(1)
        private Integer initialDelayDays = 1;
        @NotBlank
        private String regularDelayUnit = "T";
        @NotNull
        @Min(1)
        private Integer regularDelayDays = 1;
        @NotBlank
        private String settlementFrequency = "DAILY";
        @Min(1)
        @Max(28)
        private Integer frequencyDay;
        @NotBlank
        @Size(max = 500)
        private String changeReason;
        @Valid
        private List<FeeRuleRequest> rules = new ArrayList<>();
    }

    /** 新建费用模板并提交 v1 审核。 */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class FeeTemplateCreateRequest extends FeeVersionSaveRequest {
        @NotBlank
        @Size(max = 128)
        private String planName;
        @Size(max = 500)
        private String remark;
    }

    /** 费用模板启停请求；归档使用独立动作。 */
    @Data
    public static class FeeTemplateStatusRequest {
        @NotNull
        private Boolean enabled;
    }

    /** 给商户分配模板、基于模板调整或创建独立配置。 */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class MerchantFeeVersionSaveRequest extends FeeVersionSaveRequest {
        private Long templateId;
        @Size(max = 128)
        private String planName;
        @Size(max = 500)
        private String remark;
    }

    /** 商户原样复制当前模板版本的提交请求，不接受前端覆盖模板规则。 */
    @Data
    public static class MerchantTemplateAssignRequest {
        @NotNull
        private Long templateId;
        @NotBlank
        @Size(max = 500)
        private String changeReason;
        @Size(max = 128)
        private String planName;
        @Size(max = 500)
        private String remark;
    }

    /** 审核意见。 */
    @Data
    public static class FeeReviewRequest {
        @Size(max = 500)
        private String reviewComment;
    }

    /** 费用试算输入；标签币种到 USD 的直接结算汇率由后端按系统时间解析。 */
    @Data
    public static class FeeSimulationRequest {
        @NotBlank
        private String merchantId;
        /** 试算费用分类，默认交易手续费。 */
        @NotBlank
        private String feeCategory = "TRANSACTION_FEE";
        @NotBlank
        private String transactionType;
        @NotBlank
        private String paymentType;
        @NotBlank
        private String paymentMethod = "ALL";
        /** 试算标签金额，单位由 labelCurrency 指定，不做展示层舍入。 */
        @NotNull
        @DecimalMin("0.00000001")
        private BigDecimal labelAmount;
        /** 标签金额 ISO 4217 三位币种代码。 */
        @NotBlank
        @Size(min = 3, max = 3)
        private String labelCurrency;
        @NotNull
        @Min(0)
        private Long monthlyCountBefore = 0L;
        /** 本次交易前的月累计金额，已归一为 USD。 */
        @NotNull
        @DecimalMin("0")
        private BigDecimal monthlyAmountUsdBefore = BigDecimal.ZERO;
    }

    /** 费用试算记录分页条件。 */
    @Data
    public static class FeeSimulationRecordQuery {
        private int pageNo = 1;
        private int pageSize = 10;
        private String keyword;
        private String merchantId;
        private String transactionType;

        public int safePageNo() {
            return Math.max(pageNo, 1);
        }

        public int safePageSize() {
            return Math.min(Math.max(pageSize, 1), 200);
        }
    }

    /** 费用方案列表摘要。 */
    @Data
    public static class FeePlanSummaryResponse {
        private Long id;
        private String planCode;
        private String planName;
        private String planType;
        private String merchantId;
        private String merchantName;
        private Long sourceTemplateId;
        private Integer sourceTemplateVersionNo;
        private String originType;
        private Long currentVersionId;
        private Integer currentVersionNo;
        private String status;
        private String remark;
        private String pendingVersionStatus;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;
    }

    /** 费用阶梯展示。 */
    @Data
    public static class FeeRuleTierResponse {
        private Long id;
        private BigDecimal lowerBound;
        private BigDecimal upperBound;
        private BigDecimal percentageRate;
        private BigDecimal fixedAmountUsd;
        private BigDecimal minimumAmountUsd;
        private BigDecimal maximumAmountUsd;
        private Integer sortNo;
    }

    /** 费用规则展示。 */
    @Data
    public static class FeeRuleResponse {
        private Long id;
        private String feeCategory;
        private String ruleName;
        private String transactionType;
        private String paymentType;
        private String paymentMethod;
        private String feeMode;
        private BigDecimal percentageRate;
        private BigDecimal fixedAmountUsd;
        private BigDecimal minimumAmountUsd;
        private BigDecimal maximumAmountUsd;
        private String tierMetric;
        private String tierPeriod;
        private Integer sortNo;
        private String remark;
        private List<FeeRuleTierResponse> tiers = new ArrayList<>();
    }

    /** 单个不可变费用版本及审核历史。 */
    @Data
    public static class FeeVersionResponse {
        private Long id;
        private Long planId;
        private Integer versionNo;
        private String versionStatus;
        private String changeType;
        private Long sourceTemplateId;
        private Integer sourceTemplateVersionNo;
        private String originType;
        private BigDecimal reserveRate;
        private Integer reserveDelayDays;
        private String initialDelayUnit;
        private Integer initialDelayDays;
        private String regularDelayUnit;
        private Integer regularDelayDays;
        private String settlementFrequency;
        private Integer frequencyDay;
        private String changeReason;
        private Long submitById;
        private String submitByName;
        private LocalDateTime submitTime;
        private Long reviewById;
        private String reviewByName;
        private String reviewComment;
        private LocalDateTime reviewTime;
        private LocalDateTime effectiveTime;
        private LocalDateTime supersededTime;
        private List<FeeRuleResponse> rules = new ArrayList<>();
    }

    /** 费用方案详情，包含当前配置和完整版本历史。 */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class FeePlanDetailResponse extends FeePlanSummaryResponse {
        private FeeVersionResponse currentVersion;
        private List<FeeVersionResponse> versions = new ArrayList<>();
    }

    /** 待审核版本列表项。 */
    @Data
    public static class FeeReviewResponse {
        private Long versionId;
        private Long planId;
        private String planCode;
        private String planName;
        private String planType;
        private String merchantId;
        private String merchantName;
        private Integer versionNo;
        private String changeType;
        private String changeReason;
        private String submitByName;
        private LocalDateTime submitTime;
    }

    /** 无副作用试算结果，金额不做展示层舍入。 */
    @Data
    public static class FeeSimulationResponse {
        private String simulationNo;
        private Long planVersionId;
        private Long matchedRuleId;
        private Long matchedTierId;
        /** 按标签币种计算的百分比费用。 */
        private BigDecimal percentageFeeLabel;
        /** percentageFeeLabel 的 ISO 4217 三位币种代码。 */
        private String percentageFeeCurrency;
        /** 换算并叠加固定费用后的 USD 金额，尚未应用上下限。 */
        private BigDecimal rawFeeUsd;
        /** 应用最低和最高费用后的最终 USD 试算金额。 */
        private BigDecimal finalFeeUsd;
        /** 标签金额按当前正向结算汇率归一后的 USD 金额。 */
        private BigDecimal labelAmountUsd;
        /** 当前生效版本滚动保证金比例。 */
        private BigDecimal reserveRate;
        /** 本次交易预计留存的滚动保证金 USD 金额。 */
        private BigDecimal reserveAmountUsd;
        /** 扣除手续费和滚动保证金后的预计净结算 USD 金额。 */
        private BigDecimal estimatedNetSettlementUsd;
        /** 系统选用的标签币种到 USD 正向结算汇率。 */
        private BigDecimal labelToUsdRate;
        /** 系统业务汇率记录 ID；USD 恒等汇率时为空。 */
        private Long settlementRateId;
        private String settlementRateSource;
        /** 选用汇率的生效时间。 */
        private LocalDateTime rateEffectiveTime;
        /** 本次试算解析汇率的系统时间。 */
        private LocalDateTime rateValuationTime;
        /** 最终费用币种，本期恒为 USD。 */
        private String feeCurrency = "USD";
        private String appliedLimit = "NONE";
        private String formulaSnapshot;
    }

    /** 费用试算记录列表项，保留输入、匹配结果、汇率和操作人审计快照。 */
    @Data
    public static class FeeSimulationRecordResponse {
        private Long id;
        private String simulationNo;
        private Long planVersionId;
        private String merchantId;
        private String feeCategory;
        private String transactionType;
        private String paymentType;
        private String paymentMethod;
        private BigDecimal labelAmount;
        private String labelCurrency;
        private BigDecimal labelToUsdRate;
        private Long settlementRateId;
        private String settlementRateSource;
        private LocalDateTime rateEffectiveTime;
        private LocalDateTime rateValuationTime;
        private Long matchedRuleId;
        private Long matchedTierId;
        private BigDecimal finalFeeUsd;
        private BigDecimal reserveAmountUsd;
        private BigDecimal estimatedNetSettlementUsd;
        private String operatorName;
        private LocalDateTime createTime;
    }
}
