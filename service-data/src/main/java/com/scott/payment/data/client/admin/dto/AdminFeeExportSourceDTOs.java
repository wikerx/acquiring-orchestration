package com.scott.payment.data.client.admin.dto;

import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.FeePlanQuery;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.FeeSimulationQuery;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * service-data 调用管理端费用域导出来源的内部模型。
 */
public final class AdminFeeExportSourceDTOs {

    /** 阻止内部模型容器被实例化。 */
    private AdminFeeExportSourceDTOs() {
    }

    /** 费用方案类分页请求，显式携带任务创建时冻结的商户范围。 */
    @Data
    public static class FeePlanPageRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        private Long creatorAccountId;
        private boolean allMerchants;
        private Set<String> merchantIds = new LinkedHashSet<>();
        private FeePlanQuery query;
        private Integer pageNo;
        private Integer pageSize;
    }

    /** 费用试算分页请求，语言用于稳定复现现有同步导出文案。 */
    @Data
    public static class FeeSimulationPageRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        private Long creatorAccountId;
        private boolean allMerchants;
        private Set<String> merchantIds = new LinkedHashSet<>();
        private String locale;
        private FeeSimulationQuery query;
        private Integer pageNo;
        private Integer pageSize;
    }

    /** 一页费用模板安全投影。 */
    @Data
    public static class FeeTemplatePageResponse implements Serializable {
        private static final long serialVersionUID = 1L;
        private long total;
        private List<FeeTemplateRow> records = new ArrayList<>();
    }

    /** 一页商户费率安全投影。 */
    @Data
    public static class MerchantFeePageResponse implements Serializable {
        private static final long serialVersionUID = 1L;
        private long total;
        private List<MerchantFeeRow> records = new ArrayList<>();
    }

    /** 一页费率复核安全投影。 */
    @Data
    public static class FeeReviewPageResponse implements Serializable {
        private static final long serialVersionUID = 1L;
        private long total;
        private List<FeeReviewRow> records = new ArrayList<>();
    }

    /** 一页费用试算逐项安全投影。 */
    @Data
    public static class FeeSimulationPageResponse implements Serializable {
        private static final long serialVersionUID = 1L;
        private long sourceTotal;
        private List<FeeSimulationRow> records = new ArrayList<>();
    }

    /** 费用模板导出源行。 */
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

    /** 商户费率导出源行。 */
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

    /** 费率复核导出源行。 */
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

    /** 费用试算逐项导出源行。 */
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
