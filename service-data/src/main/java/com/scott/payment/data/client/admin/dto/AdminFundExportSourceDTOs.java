package com.scott.payment.data.client.admin.dto;

import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.FundAccountQuery;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.FundDeductionQuery;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.FundLedgerQuery;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.FundRechargeQuery;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * service-data 调用管理端资金账户域导出来源的内部模型。
 */
public final class AdminFundExportSourceDTOs {

    /** 阻止内部模型容器被实例化。 */
    private AdminFundExportSourceDTOs() {
    }

    /** 资金账户分页请求，显式携带任务创建时冻结的商户范围。 */
    @Data
    public static class FundAccountPageRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        private Long creatorAccountId;
        private boolean allMerchants;
        private Set<String> merchantIds = new LinkedHashSet<>();
        private FundAccountQuery query;
        private Integer pageNo;
        private Integer pageSize;
    }

    /** 余额流水分页请求，账户主键为空时查询全局流水。 */
    @Data
    public static class FundLedgerPageRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        private Long creatorAccountId;
        private boolean allMerchants;
        private Set<String> merchantIds = new LinkedHashSet<>();
        private FundLedgerQuery query;
        private Integer pageNo;
        private Integer pageSize;
    }

    /** 充值申请分页请求。 */
    @Data
    public static class FundRechargePageRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        private Long creatorAccountId;
        private boolean allMerchants;
        private Set<String> merchantIds = new LinkedHashSet<>();
        private FundRechargeQuery query;
        private Integer pageNo;
        private Integer pageSize;
    }

    /** 扣减申请分页请求。 */
    @Data
    public static class FundDeductionPageRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        private Long creatorAccountId;
        private boolean allMerchants;
        private Set<String> merchantIds = new LinkedHashSet<>();
        private FundDeductionQuery query;
        private Integer pageNo;
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

    /** 资金账户导出源行。 */
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

    /** 余额流水导出源行，金额与余额保持数据库十进制事实。 */
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

    /** 充值申请导出源行。 */
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

    /** 扣减申请导出源行。 */
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
