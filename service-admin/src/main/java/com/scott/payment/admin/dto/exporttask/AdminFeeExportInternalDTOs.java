package com.scott.payment.admin.dto.exporttask;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Data 分页读取管理端费用域导出源的内部契约，只暴露现有 Excel 字段。
 */
public final class AdminFeeExportInternalDTOs {

    /** 阻止内部契约容器被实例化。 */
    private AdminFeeExportInternalDTOs() {
    }

    /** 费用方案冻结筛选条件，不包含浏览器分页参数。 */
    @Data
    public static class FeePlanQuery implements Serializable {
        private static final long serialVersionUID = 1L;
        @Size(max = 128)
        private String keyword;
        @Size(max = 32)
        private String status;
        @Size(max = 32)
        private String versionStatus;
    }

    /** 费用试算记录冻结筛选条件，不包含浏览器分页参数。 */
    @Data
    public static class FeeSimulationQuery implements Serializable {
        private static final long serialVersionUID = 1L;
        @Size(max = 128)
        private String keyword;
        @Size(max = 64)
        private String merchantId;
        @Size(max = 64)
        private String transactionType;
    }

    /** 费用方案类导出分页请求。 */
    @Data
    public static class FeePlanPageRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        @NotNull
        private Long creatorAccountId;
        private boolean allMerchants;
        private Set<String> merchantIds = new LinkedHashSet<>();
        @Valid
        @NotNull
        private FeePlanQuery query;
        @NotNull
        @Min(1)
        private Integer pageNo;
        @NotNull
        @Min(1)
        @Max(500)
        private Integer pageSize;
    }

    /** 费用试算记录导出分页请求，语言用于稳定复现字典和状态文案。 */
    @Data
    public static class FeeSimulationPageRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        @NotNull
        private Long creatorAccountId;
        private boolean allMerchants;
        private Set<String> merchantIds = new LinkedHashSet<>();
        @Size(max = 16)
        private String locale;
        @Valid
        @NotNull
        private FeeSimulationQuery query;
        @NotNull
        @Min(1)
        private Integer pageNo;
        @NotNull
        @Min(1)
        @Max(500)
        private Integer pageSize;
    }

    /** 一页费用模板导出安全投影。 */
    @Data
    public static class FeeTemplatePageResponse implements Serializable {
        private static final long serialVersionUID = 1L;
        private long total;
        private List<FeeTemplateRow> records = new ArrayList<>();
    }

    /** 一页商户费率导出安全投影。 */
    @Data
    public static class MerchantFeePageResponse implements Serializable {
        private static final long serialVersionUID = 1L;
        private long total;
        private List<MerchantFeeRow> records = new ArrayList<>();
    }

    /** 一页费率复核导出安全投影。 */
    @Data
    public static class FeeReviewPageResponse implements Serializable {
        private static final long serialVersionUID = 1L;
        private long total;
        private List<FeeReviewRow> records = new ArrayList<>();
    }

    /** 一页费用试算逐项导出安全投影。 */
    @Data
    public static class FeeSimulationPageResponse implements Serializable {
        private static final long serialVersionUID = 1L;
        /** 匹配筛选条件的试算主记录数，用于控制来源分页。 */
        private long sourceTotal;
        private List<FeeSimulationRow> records = new ArrayList<>();
    }

    /** 费用模板导出行。 */
    @Data
    public static class FeeTemplateRow implements Serializable {
        private static final long serialVersionUID = 1L;
        private String planCode;
        private String planName;
        private Integer currentVersionNo;
        private String status;
        private String remark;
        private LocalDateTime updateTime;
    }

    /** 商户费率导出行。 */
    @Data
    public static class MerchantFeeRow implements Serializable {
        private static final long serialVersionUID = 1L;
        private String merchantId;
        private String merchantName;
        private String planCode;
        private String originType;
        private Integer currentVersionNo;
        private String status;
        private LocalDateTime updateTime;
    }

    /** 费率复核导出行。 */
    @Data
    public static class FeeReviewRow implements Serializable {
        private static final long serialVersionUID = 1L;
        private String planCode;
        private String planName;
        private String planType;
        private String merchantId;
        private Integer versionNo;
        private String changeReason;
        private String submitByName;
        private LocalDateTime submitTime;
    }

    /** 费用试算逐项导出行，字段与现有同步导出契约保持一致。 */
    @Data
    public static class FeeSimulationRow implements Serializable {
        private static final long serialVersionUID = 1L;
        private String simulationNo;
        private Integer lineNo;
        private String merchantId;
        private Long planVersionId;
        private String transactionType;
        private String paymentType;
        private String paymentMethod;
        private String selectedRiskServices;
        private BigDecimal labelAmount;
        private String labelCurrency;
        private BigDecimal labelToUsdRate;
        private String itemType;
        private String feeCategory;
        private String calculationStatus;
        private String includedInFeeTotal;
        private String riskServiceType;
        private String ruleName;
        private String feeMode;
        private String chargeTrigger;
        private String appliedLimit;
        private BigDecimal rawFeeUsd;
        private BigDecimal detailFeeUsd;
        private String detailFormula;
        private BigDecimal finalFeeUsd;
        private BigDecimal reserveAmountUsd;
        private BigDecimal netSettlementUsd;
        private String feeTotalFormula;
        private String netSettlementFormula;
        private String rateSource;
        private LocalDateTime rateEffectiveTime;
        private LocalDateTime rateValuationTime;
        private String detailSnapshotStatus;
        private String operatorName;
        private LocalDateTime createTime;
    }
}
