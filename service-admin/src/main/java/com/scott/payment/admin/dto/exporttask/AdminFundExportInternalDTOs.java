package com.scott.payment.admin.dto.exporttask;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Data 分页读取管理端资金账户域导出源的内部契约，所有请求必须携带冻结商户范围。
 */
public final class AdminFundExportInternalDTOs {

    /** 阻止内部契约容器被实例化。 */
    private AdminFundExportInternalDTOs() {
    }

    /** 资金账户冻结查询条件。 */
    @Data
    public static class FundAccountQuery implements Serializable {
        private static final long serialVersionUID = 1L;
        private String keyword;
        private String accountStatus;
        private String settlementCurrency;
    }

    /** 余额流水冻结查询条件，账户主键为空时表示全局流水。 */
    @Data
    public static class FundLedgerQuery implements Serializable {
        private static final long serialVersionUID = 1L;
        private Long accountId;
        private String keyword;
        private String merchantId;
        private String accountNo;
        private String currency;
        private String balanceType;
        private String businessType;
        private String direction;
        private LocalDateTime postedStartTime;
        private LocalDateTime postedEndTime;
    }

    /** 充值申请冻结查询条件。 */
    @Data
    public static class FundRechargeQuery implements Serializable {
        private static final long serialVersionUID = 1L;
        private String keyword;
        private String merchantId;
        private String rechargeStatus;
    }

    /** 扣减申请冻结查询条件。 */
    @Data
    public static class FundDeductionQuery implements Serializable {
        private static final long serialVersionUID = 1L;
        private String keyword;
        private String merchantId;
        private String deductionCategory;
        private String deductionStatus;
    }

    /** Data 分页读取资金账户的内部请求。 */
    @Data
    public static class FundAccountPageRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        @NotNull
        private Long creatorAccountId;
        private boolean allMerchants;
        private Set<String> merchantIds = new LinkedHashSet<>();
        @Valid
        @NotNull
        private FundAccountQuery query;
        @NotNull
        @Min(1)
        private Integer pageNo;
        @NotNull
        @Min(1)
        @Max(500)
        private Integer pageSize;
    }

    /** Data 分页读取余额流水的内部请求。 */
    @Data
    public static class FundLedgerPageRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        @NotNull
        private Long creatorAccountId;
        private boolean allMerchants;
        private Set<String> merchantIds = new LinkedHashSet<>();
        @Valid
        @NotNull
        private FundLedgerQuery query;
        @NotNull
        @Min(1)
        private Integer pageNo;
        @NotNull
        @Min(1)
        @Max(500)
        private Integer pageSize;
    }

    /** Data 分页读取充值申请的内部请求。 */
    @Data
    public static class FundRechargePageRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        @NotNull
        private Long creatorAccountId;
        private boolean allMerchants;
        private Set<String> merchantIds = new LinkedHashSet<>();
        @Valid
        @NotNull
        private FundRechargeQuery query;
        @NotNull
        @Min(1)
        private Integer pageNo;
        @NotNull
        @Min(1)
        @Max(500)
        private Integer pageSize;
    }

    /** Data 分页读取扣减申请的内部请求。 */
    @Data
    public static class FundDeductionPageRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        @NotNull
        private Long creatorAccountId;
        private boolean allMerchants;
        private Set<String> merchantIds = new LinkedHashSet<>();
        @Valid
        @NotNull
        private FundDeductionQuery query;
        @NotNull
        @Min(1)
        private Integer pageNo;
        @NotNull
        @Min(1)
        @Max(500)
        private Integer pageSize;
    }

    /** 一页资金账户安全投影。 */
    @Data
    public static class FundAccountPageResponse implements Serializable {
        private static final long serialVersionUID = 1L;
        private long total;
        private long pageNo;
        private long pageSize;
        private List<FundAccountRow> records = new ArrayList<>();
    }

    /** 一页余额流水安全投影。 */
    @Data
    public static class FundLedgerPageResponse implements Serializable {
        private static final long serialVersionUID = 1L;
        private long total;
        private long pageNo;
        private long pageSize;
        private List<FundLedgerRow> records = new ArrayList<>();
    }

    /** 一页充值申请安全投影。 */
    @Data
    public static class FundRechargePageResponse implements Serializable {
        private static final long serialVersionUID = 1L;
        private long total;
        private long pageNo;
        private long pageSize;
        private List<FundRechargeRow> records = new ArrayList<>();
    }

    /** 一页扣减申请安全投影。 */
    @Data
    public static class FundDeductionPageResponse implements Serializable {
        private static final long serialVersionUID = 1L;
        private long total;
        private long pageNo;
        private long pageSize;
        private List<FundDeductionRow> records = new ArrayList<>();
    }

    /** 资金账户导出行，仅包含管理端列表已公开字段。 */
    @Data
    public static class FundAccountRow implements Serializable {
        private static final long serialVersionUID = 1L;
        private String accountNo;
        private String merchantId;
        private String merchantName;
        private String settlementCurrency;
        private BigDecimal availableBalance;
        private String accountStatus;
        private Integer reverseRestricted;
        private LocalDateTime updateTime;
    }

    /** 不可变余额流水导出行，金额和余额保持数据库事实语义。 */
    @Data
    public static class FundLedgerRow implements Serializable {
        private static final long serialVersionUID = 1L;
        private String ledgerNo;
        private String accountNo;
        private String merchantId;
        private String merchantName;
        private String businessType;
        private String summary;
        private String businessNo;
        private String balanceType;
        private String direction;
        private BigDecimal amount;
        private String currency;
        private BigDecimal balanceBefore;
        private BigDecimal balanceAfter;
        private Long accountSequence;
        private String operatorName;
        private String reviewerName;
        private String operationReason;
        private String reviewComment;
        private LocalDateTime businessTime;
        private LocalDateTime postedTime;
        private String requestId;
        private String idempotencyKey;
    }

    /** 充值申请导出行，不包含数据库主键和登录账号。 */
    @Data
    public static class FundRechargeRow implements Serializable {
        private static final long serialVersionUID = 1L;
        private String rechargeNo;
        private String merchantId;
        private String merchantName;
        private String accountNo;
        private BigDecimal amount;
        private String currency;
        private String rechargeStatus;
        private String remark;
        private String submitByName;
        private LocalDateTime submitTime;
        private String auditByName;
        private LocalDateTime auditTime;
        private String recheckByName;
        private LocalDateTime recheckTime;
        private String ledgerNo;
        private LocalDateTime postedTime;
    }

    /** 扣减申请导出行，不包含数据库主键和登录账号。 */
    @Data
    public static class FundDeductionRow implements Serializable {
        private static final long serialVersionUID = 1L;
        private String deductionNo;
        private String merchantId;
        private String merchantName;
        private String accountNo;
        private String deductionCategory;
        private BigDecimal amount;
        private String currency;
        private String deductionStatus;
        private String reason;
        private String submitByName;
        private LocalDateTime submitTime;
        private String auditByName;
        private LocalDateTime auditTime;
        private String recheckByName;
        private LocalDateTime recheckTime;
        private String ledgerNo;
        private LocalDateTime postedTime;
    }
}
