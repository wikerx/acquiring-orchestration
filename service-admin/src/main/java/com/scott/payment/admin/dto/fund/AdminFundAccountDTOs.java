package com.scott.payment.admin.dto.fund;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminFundAccountDTOs
 * @date : 2026-08-18 00:00
 * @email : scott_x@163.com
 * @description : 管理端商户资金账户、余额流水、充值和扣减审核模型；在途与保证金仅返回余额汇总。
 * @status : create
 */
public final class AdminFundAccountDTOs {

    private AdminFundAccountDTOs() {
    }

    /** 资金账户分页条件。 */
    @Data
    public static class FundAccountQuery {
        /** 页码，从 1 开始。 */
        private int pageNo = 1;
        /** 每页条数，服务端限制为 1 至 200。 */
        private int pageSize = 10;
        /** 商户号、商户名称或资金账户号关键字，允许为空。 */
        private String keyword;
        /** 人工账户状态：NORMAL、FROZEN 或 CLOSED，允许为空。 */
        private String accountStatus;
        /** ISO 4217 三位结算币种，允许为空。 */
        private String settlementCurrency;

        /** @return 修正为至少 1 的页码。 */
        public int safePageNo() { return Math.max(pageNo, 1); }
        /** @return 修正到 1 至 200 范围内的每页条数。 */
        public int safePageSize() { return Math.min(Math.max(pageSize, 1), 200); }
    }

    /** 账户明细通用分页条件。 */
    @Data
    public static class FundDetailQuery {
        /** 页码，从 1 开始。 */
        private int pageNo = 1;
        /** 每页条数，服务端限制为 1 至 200。 */
        private int pageSize = 10;
        /** 流水号、业务单号、交易号或摘要关键字，允许为空。 */
        private String keyword;
        /** 全局流水查询的商户号；账户详情查询时由路径账户限定。 */
        private String merchantId;
        /** 全局流水查询的资金账户号，支持精确匹配。 */
        private String accountNo;
        /** 全局流水查询的 ISO 4217 三位币种代码。 */
        private String currency;
        /** AVAILABLE 或 RESERVE，允许为空。 */
        private String balanceType;
        /** 余额变动业务类型，值来自 fund_ledger_business_type 数据字典。 */
        private String businessType;
        /** CREDIT 表示增加，DEBIT 表示减少。 */
        private String direction;
        /** 入账时间范围起点，包含，使用系统时间。 */
        private LocalDateTime postedStartTime;
        /** 入账时间范围终点，包含，使用系统时间。 */
        private LocalDateTime postedEndTime;

        /** @return 修正为至少 1 的页码。 */
        public int safePageNo() { return Math.max(pageNo, 1); }
        /** @return 修正到 1 至 200 范围内的每页条数。 */
        public int safePageSize() { return Math.min(Math.max(pageSize, 1), 200); }
    }

    /** 账户冻结、解冻、关闭或恢复请求。 */
    @Data
    public static class FundAccountStatusRequest {
        /** 页面读取到的账户版本号，用于拒绝并发覆盖。 */
        @NotNull
        private Long accountVersion;
        /** 状态变更原因，写入系统操作日志且不允许为空。 */
        @NotBlank
        @Size(max = 500)
        private String reason;
    }

    /** 充值申请分页条件。 */
    @Data
    public static class FundRechargeQuery {
        /** 页码，从 1 开始。 */
        private int pageNo = 1;
        /** 每页条数，服务端限制为 1 至 200。 */
        private int pageSize = 10;
        /** 充值申请号、请求号或商户号关键字，允许为空。 */
        private String keyword;
        /** 精确匹配的商户号，允许为空。 */
        private String merchantId;
        /** PENDING_AUDIT、PENDING_RECHECK、POSTED 或 REJECTED，允许为空。 */
        private String rechargeStatus;

        /** @return 修正为至少 1 的页码。 */
        public int safePageNo() { return Math.max(pageNo, 1); }
        /** @return 修正到 1 至 200 范围内的每页条数。 */
        public int safePageSize() { return Math.min(Math.max(pageSize, 1), 200); }
    }

    /** 管理端充值申请输入。 */
    @Data
    public static class FundRechargeCreateRequest {
        /** 待充值资金账户主键，不允许为空。 */
        @NotNull
        private Long accountId;
        /** 充值金额使用账户结算币种，允许范围为 100 至 100,000,000。 */
        @NotNull
        @DecimalMin("100")
        @DecimalMax("100000000")
        private BigDecimal amount;
        /** 客户端请求唯一标识，用于防止重复提交。 */
        @NotBlank
        @Size(max = 64)
        private String requestId;
        /** 充值原因或凭证摘要，不允许为空，最长 500 字符。 */
        @NotBlank
        @Size(max = 500)
        private String remark;
    }

    /** 充值审核或复核意见。 */
    @Data
    public static class FundRechargeReviewRequest {
        /** 审核或复核意见，允许为空，最长 500 字符。 */
        @Size(max = 500)
        private String comment;
    }

    /** 充值驳回意见。 */
    @Data
    public static class FundRechargeRejectRequest {
        /** 驳回原因，不允许为空，最长 500 字符。 */
        @NotBlank
        @Size(max = 500)
        private String comment;
    }

    /** 账户扣减申请分页条件。 */
    @Data
    public static class FundDeductionQuery {
        /** 页码，从 1 开始。 */
        private int pageNo = 1;
        /** 每页条数，服务端限制为 1 至 200。 */
        private int pageSize = 10;
        /** 扣减申请号、请求号或商户号关键字，允许为空。 */
        private String keyword;
        /** 精确匹配的商户号，允许为空。 */
        private String merchantId;
        /** ACCOUNT_CORRECTION、EXTRA_FEE、PENALTY 或 OTHER，允许为空。 */
        private String deductionCategory;
        /** PENDING_AUDIT、PENDING_RECHECK、POSTED 或 REJECTED，允许为空。 */
        private String deductionStatus;

        /** @return 修正为至少 1 的页码。 */
        public int safePageNo() { return Math.max(pageNo, 1); }
        /** @return 修正到 1 至 200 范围内的每页条数。 */
        public int safePageSize() { return Math.min(Math.max(pageSize, 1), 200); }
    }

    /** 管理端账户扣减申请输入。 */
    @Data
    public static class FundDeductionCreateRequest {
        /** 待扣减资金账户主键，不允许为空。 */
        @NotNull
        private Long accountId;
        /** 扣减类型：ACCOUNT_CORRECTION、EXTRA_FEE、PENALTY 或 OTHER。 */
        @NotBlank
        private String deductionCategory;
        /** 扣减金额使用账户结算币种，必须大于零且不超过 100,000,000。 */
        @NotNull
        @DecimalMin(value = "0", inclusive = false)
        @DecimalMax("100000000")
        private BigDecimal amount;
        /** 客户端请求唯一标识，用于防止重复提交。 */
        @NotBlank
        @Size(max = 64)
        private String requestId;
        /** 商户可见的完整扣减说明，不允许为空，最长 500 字符。 */
        @NotBlank
        @Size(max = 500)
        private String reason;
    }

    /** 账户扣减审核或复核意见。 */
    @Data
    public static class FundDeductionReviewRequest {
        /** 审核或复核意见，允许为空，最长 500 字符。 */
        @Size(max = 500)
        private String comment;
    }

    /** 账户扣减驳回意见。 */
    @Data
    public static class FundDeductionRejectRequest {
        /** 驳回原因，不允许为空，最长 500 字符。 */
        @NotBlank
        @Size(max = 500)
        private String comment;
    }

    /** 单币种金额汇总；不同币种不会直接相加。 */
    @Data
    public static class CurrencyBalanceResponse {
        /** ISO 4217 三位币种代码。 */
        private String currency;
        /** 该币种独立汇总金额，不与其他币种直接相加。 */
        private BigDecimal amount;
    }

    /** 资金账户摘要。 */
    @Data
    public static class FundAccountResponse {
        /** 资金账户数据库主键。 */
        private Long id;
        /** 平台资金账户号，不包含敏感银行账号信息。 */
        private String accountNo;
        /** 账户所属商户号。 */
        private String merchantId;
        /** 商户名称，仅用于管理端展示。 */
        private String merchantName;
        /** ISO 4217 三位结算币种，一期每个商户仅一个。 */
        private String settlementCurrency;
        /** 可用余额，单位为 settlementCurrency，允许为负。 */
        private BigDecimal availableBalance;
        /** 保证金余额，单位为 settlementCurrency，不允许为负。 */
        private BigDecimal reserveBalance;
        /** 按标签币种分别统计的在途余额，禁止跨币种直接相加。 */
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
        /** 当前状态是否允许生成交易或保证金结算入账。 */
        private Boolean settlementAllowed;
        /** 账户状态和负余额规则共同判定的主动逆向交易能力。 */
        private Boolean reverseTransactionAllowed;
        /** 账户并发版本号，状态变更时必须原样回传。 */
        private Long accountVersion;
        /** 账户创建系统时间。 */
        private LocalDateTime createTime;
        /** 账户最近修改系统时间。 */
        private LocalDateTime updateTime;
    }

    /** 不可变余额流水展示。 */
    @Data
    public static class FundLedgerResponse {
        /** 余额流水数据库主键。 */
        private Long id;
        /** 平台唯一余额流水号。 */
        private String ledgerNo;
        /** 同一业务产生的关联流水组号，允许为空。 */
        private String ledgerGroupNo;
        /** 流水所属资金账户号。 */
        private String accountNo;
        /** 流水所属商户号。 */
        private String merchantId;
        /** 流水所属商户名称快照，仅用于管理端展示。 */
        private String merchantName;
        /** AVAILABLE 或 RESERVE。 */
        private String balanceType;
        /** 余额变动业务类型，值来自 fund_ledger_business_type 数据字典。 */
        private String businessType;
        /** 面向财务核对的变动摘要。 */
        private String summary;
        /** 来源业务单号。 */
        private String businessNo;
        /** 关联交易号，非交易类变动时为空。 */
        private String transactionId;
        /** 关联结算批次号，非结算类变动时为空。 */
        private String settlementBatchNo;
        /** 关联费用明细号，非费用类变动时为空。 */
        private String feeDetailNo;
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
        /** 费用变动锁定的费率版本主键，非费用类变动时为空。 */
        private Long feeVersionId;
        /** 涉及换汇时的汇率快照主键，不换汇时为空。 */
        private Long rateSnapshotId;
        /** AUTO 或 MANUAL。 */
        private String operationMode;
        /** 原操作人账号主键，系统自动入账时允许为空。 */
        private Long operatorId;
        /** 原操作人名称快照。 */
        private String operatorName;
        /** 最终复核人账号主键，自动入账或未复核时为空。 */
        private Long reviewerId;
        /** 最终复核人名称快照，允许为空。 */
        private String reviewerName;
        /** 人工操作原因，自动入账时允许为空。 */
        private String operationReason;
        /** 审核和复核意见摘要，允许为空。 */
        private String reviewComment;
        /** 来源业务事件发生系统时间。 */
        private LocalDateTime businessTime;
        /** 人工申请提交系统时间，自动入账时为空。 */
        private LocalDateTime submitTime;
        /** 人工审核或复核系统时间，自动入账时为空。 */
        private LocalDateTime reviewTime;
        /** 可用余额实际发生变化的系统时间。 */
        private LocalDateTime postedTime;
        /** 客户端请求号，未提供时为空。 */
        private String requestId;
        /** 数据库唯一资金幂等键，仅管理端核对使用。 */
        private String idempotencyKey;
        /** 链路追踪号，允许为空。 */
        private String traceId;
        /** 被本笔冲正的原流水主键，非冲正流水为空。 */
        private Long reversalOfLedgerId;
        /** 充值流水关联的完整充值审批快照；非充值流水为空。 */
        private FundRechargeResponse rechargeDetail;
        /** 账户扣减流水关联的完整审批快照；非扣减流水为空。 */
        private FundDeductionResponse deductionDetail;
    }

    /** 充值申请、审核、复核和入账审计快照。 */
    @Data
    public static class FundRechargeResponse {
        /** 充值申请数据库主键。 */
        private Long id;
        /** 平台唯一充值申请号。 */
        private String rechargeNo;
        /** 充值目标资金账户主键。 */
        private Long accountId;
        /** 充值目标资金账户号。 */
        private String accountNo;
        /** 账户所属商户号。 */
        private String merchantId;
        /** 商户名称，仅用于管理端展示。 */
        private String merchantName;
        /** 充值账户 ISO 4217 三位结算币种。 */
        private String currency;
        /** 充值金额，单位为 currency，范围 100 至 100,000,000。 */
        private BigDecimal amount;
        /** PENDING_AUDIT、PENDING_RECHECK、POSTED 或 REJECTED。 */
        private String rechargeStatus;
        /** 充值原因或凭证摘要。 */
        private String remark;
        /** 提交人账号主键。 */
        private Long submitById;
        /** 提交人名称快照。 */
        private String submitByName;
        /** 提交系统时间。 */
        private LocalDateTime submitTime;
        /** 审核人账号主键，待审核时为空。 */
        private Long auditById;
        /** 审核人名称快照，待审核时为空。 */
        private String auditByName;
        /** 审核意见，允许为空。 */
        private String auditComment;
        /** 审核系统时间，待审核时为空。 */
        private LocalDateTime auditTime;
        /** 复核人账号主键，未复核时为空。 */
        private Long recheckById;
        /** 复核人名称快照，未复核时为空。 */
        private String recheckByName;
        /** 复核意见，允许为空。 */
        private String recheckComment;
        /** 复核系统时间，未复核时为空。 */
        private LocalDateTime recheckTime;
        /** 驳回人账号主键，非驳回状态为空。 */
        private Long rejectById;
        /** 驳回人名称快照，非驳回状态为空。 */
        private String rejectByName;
        /** 驳回原因，非驳回状态为空。 */
        private String rejectComment;
        /** 驳回系统时间，非驳回状态为空。 */
        private LocalDateTime rejectTime;
        /** 客户端唯一请求号，用于防止重复提交。 */
        private String requestId;
        /** 最终入账流水号，未完成复核时为空。 */
        private String ledgerNo;
        /** 最终入账系统时间，未完成复核时为空。 */
        private LocalDateTime postedTime;
        /** 充值申请创建系统时间。 */
        private LocalDateTime createTime;
        /** 充值申请最近修改系统时间。 */
        private LocalDateTime updateTime;
    }

    /** 账户扣减申请、审核、复核和入账审计快照。 */
    @Data
    public static class FundDeductionResponse {
        /** 账户扣减申请数据库主键。 */
        private Long id;
        /** 平台唯一账户扣减申请号。 */
        private String deductionNo;
        /** 扣减目标资金账户主键。 */
        private Long accountId;
        /** 扣减目标资金账户号。 */
        private String accountNo;
        /** 账户所属商户号。 */
        private String merchantId;
        /** 商户名称，仅用于管理端展示。 */
        private String merchantName;
        /** 扣减账户 ISO 4217 三位结算币种。 */
        private String currency;
        /** 扣减金额，单位为 currency，始终为正数。 */
        private BigDecimal amount;
        /** ACCOUNT_CORRECTION、EXTRA_FEE、PENALTY 或 OTHER。 */
        private String deductionCategory;
        /** PENDING_AUDIT、PENDING_RECHECK、POSTED 或 REJECTED。 */
        private String deductionStatus;
        /** 商户可见的完整扣减说明。 */
        private String reason;
        /** 提交人账号主键。 */
        private Long submitById;
        /** 提交人名称快照。 */
        private String submitByName;
        /** 提交系统时间。 */
        private LocalDateTime submitTime;
        /** 审核人账号主键，待审核时为空。 */
        private Long auditById;
        /** 审核人名称快照，待审核时为空。 */
        private String auditByName;
        /** 审核意见，允许为空。 */
        private String auditComment;
        /** 审核系统时间，待审核时为空。 */
        private LocalDateTime auditTime;
        /** 复核人账号主键，未复核时为空。 */
        private Long recheckById;
        /** 复核人名称快照，未复核时为空。 */
        private String recheckByName;
        /** 复核意见，允许为空。 */
        private String recheckComment;
        /** 复核系统时间，未复核时为空。 */
        private LocalDateTime recheckTime;
        /** 驳回人账号主键，非驳回状态为空。 */
        private Long rejectById;
        /** 驳回人名称快照，非驳回状态为空。 */
        private String rejectByName;
        /** 驳回原因，非驳回状态为空。 */
        private String rejectComment;
        /** 驳回系统时间，非驳回状态为空。 */
        private LocalDateTime rejectTime;
        /** 客户端唯一请求号，用于防止重复提交。 */
        private String requestId;
        /** 最终扣减余额流水号，未完成复核时为空。 */
        private String ledgerNo;
        /** 最终入账系统时间，未完成复核时为空。 */
        private LocalDateTime postedTime;
        /** 扣减申请创建系统时间。 */
        private LocalDateTime createTime;
        /** 扣减申请最近修改系统时间。 */
        private LocalDateTime updateTime;
    }

}
