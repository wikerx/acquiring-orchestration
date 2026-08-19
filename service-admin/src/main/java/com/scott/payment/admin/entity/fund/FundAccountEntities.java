package com.scott.payment.admin.entity.fund;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : FundAccountEntities
 * @date : 2026-08-18 00:00
 * @email : scott_x@163.com
 * @description : 商户可用余额、保证金和不可变流水持久化模型，以及从交易数据实时计算的在途余额投影。
 * @status : create
 */
public final class FundAccountEntities {

    private FundAccountEntities() {
    }

    /** 商户单结算币种资金账户。 */
    @Data
    @TableName("merchant_fund_account")
    public static class MerchantFundAccountDO {
        /** 资金账户数据库主键。 */
        @TableId(type = IdType.AUTO)
        private Long id;
        /** 平台资金账户号，不包含敏感银行账号信息。 */
        private String accountNo;
        /** 账户所属商户号。 */
        private String merchantId;
        /** 当前账户 ISO 4217 三位结算币种；一期每个商户仅一币种。 */
        private String settlementCurrency;
        /** 商户可提现的可用余额，单位为 settlementCurrency，允许为负。 */
        private BigDecimal availableBalance;
        /** NORMAL、FROZEN 或 CLOSED；负余额限制不混入人工状态。 */
        private String accountStatus;
        /** 1 表示负余额触发主动逆向交易限制，0 表示不限制。 */
        private Integer reverseRestricted;
        /** 资金变更乐观锁版本号。 */
        private Long accountVersion;
        /** 开户操作人名称快照。 */
        private String createBy;
        /** 账户创建系统时间。 */
        private LocalDateTime createTime;
        /** 最近修改人及状态变更原因摘要。 */
        private String updateBy;
        /** 账户最近修改系统时间。 */
        private LocalDateTime updateTime;
        /** 逻辑删除标识，零表示有效。 */
        private Long deleted;
    }

    /** 可用余额或保证金的不可变变动明细。 */
    @Data
    @TableName("merchant_fund_ledger")
    public static class MerchantFundLedgerDO {
        /** 余额流水数据库主键。 */
        @TableId(type = IdType.AUTO)
        private Long id;
        /** 平台唯一余额流水号。 */
        private String ledgerNo;
        /** 同一业务产生的关联流水组号，允许为空。 */
        private String ledgerGroupNo;
        /** 流水所属资金账户主键。 */
        private Long accountId;
        /** 流水所属商户号。 */
        private String merchantId;
        /** AVAILABLE 或 RESERVE。 */
        private String balanceType;
        /** 余额变动业务类型。 */
        private String businessType;
        /** 面向财务核对的变动摘要。 */
        private String summary;
        /** 来源业务单号，不允许为空。 */
        private String businessNo;
        /** 关联交易号，非交易类变动时为空。 */
        private String transactionId;
        /** 关联结算批次号，非结算类变动时为空。 */
        private String settlementBatchNo;
        /** 关联费用明细号，非费用类变动时为空。 */
        private String feeDetailNo;
        /** 本笔变动的 ISO 4217 三位币种，必须与余额类型对应账户一致。 */
        private String currency;
        /** CREDIT 表示余额增加，DEBIT 表示余额减少。 */
        private String direction;
        /** 发生金额，单位为 currency，始终以非负数保存。 */
        private BigDecimal amount;
        /** 入账前余额，单位为 currency，可为负。 */
        private BigDecimal balanceBefore;
        /** 入账后余额，单位为 currency，可为负。 */
        private BigDecimal balanceAfter;
        /** 同一账户内严格递增，用于核对连续余额。 */
        private Long accountSequence;
        /** 计费产生变动时锁定的费率版本 ID；非费用业务可为空。 */
        private Long feeVersionId;
        /** 涉及换汇时使用的汇率快照 ID；不换汇时为空。 */
        private Long rateSnapshotId;
        /** AUTO 或 MANUAL，标识系统自动入账或人工调整。 */
        private String operationMode;
        /** 原操作人账号主键，系统自动入账时允许为空。 */
        private Long operatorId;
        /** 原操作人名称快照。 */
        private String operatorName;
        /** 最终复核人账号主键，自动入账时允许为空。 */
        private Long reviewerId;
        /** 最终复核人名称快照，允许为空。 */
        private String reviewerName;
        /** 人工操作原因，自动入账时允许为空。 */
        private String operationReason;
        /** 审核和复核意见摘要，允许为空。 */
        private String reviewComment;
        /** 原业务事件发生的系统时间。 */
        private LocalDateTime businessTime;
        /** 人工调整提交时间；自动入账时为空。 */
        private LocalDateTime submitTime;
        /** 人工调整审核时间；自动入账或待审核时为空。 */
        private LocalDateTime reviewTime;
        /** 余额实际发生变化的系统时间。 */
        private LocalDateTime postedTime;
        /** 客户端请求号，未提供时为空。 */
        private String requestId;
        /** 资金入账数据库唯一幂等键，不允许为空。 */
        private String idempotencyKey;
        /** 跨服务追踪号，可为空。 */
        private String traceId;
        /** 被本笔冲正的原流水 ID；非冲正流水为空。 */
        private Long reversalOfLedgerId;
        /** 流水创建系统时间。 */
        private LocalDateTime createTime;
    }

    /** 按商户号和标签币种聚合的在途余额查询投影，不映射独立数据库表。 */
    @Data
    public static class PendingBalanceAggregate {
        /** 商户号，不允许为空。 */
        private String merchantId;
        /** 标签金额 ISO 4217 三位币种，不允许为空。 */
        private String currency;
        /** 成功未结算正向金额减退款、拒付金额后的在途净额，单位由 currency 决定。 */
        private BigDecimal amount;
    }

    /** 保证金留存与释放跟踪明细。 */
    @Data
    @TableName("merchant_reserve_item")
    public static class MerchantReserveItemDO {
        /** 保证金明细数据库主键。 */
        @TableId(type = IdType.AUTO)
        private Long id;
        /** 平台唯一保证金明细号。 */
        private String reserveNo;
        /** 保证金归属资金账户主键。 */
        private Long accountId;
        /** 保证金所属商户号。 */
        private String merchantId;
        /** 产生保证金的来源交易号，允许为空。 */
        private String sourceTransactionId;
        /** 产生保证金的来源业务单号。 */
        private String sourceBusinessNo;
        /** 保证金 ISO 4217 三位币种，等于资金账户结算币种。 */
        private String currency;
        /** 原始留存金额，单位为 currency，必须大于等于零。 */
        private BigDecimal retainedAmount;
        /** 累计已释放金额，单位为 currency，范围为 0 至 retainedAmount。 */
        private BigDecimal releasedAmount;
        /** HELD、RELEASABLE、RELEASED、FROZEN 或 DEDUCTED。 */
        private String reserveStatus;
        /** 按滚动保证金周期计算的预计释放日期，未计算时为空。 */
        private LocalDate expectedReleaseDate;
        /** 保证金结算批次号，尚未结算时为空。 */
        private String releaseBatchNo;
        /** 保证金明细创建系统时间。 */
        private LocalDateTime createTime;
        /** 保证金明细最近修改系统时间。 */
        private LocalDateTime updateTime;
    }

    /** 管理端充值申请及三段式审批记录。 */
    @Data
    @TableName("merchant_fund_recharge")
    public static class MerchantFundRechargeDO {
        /** 充值申请数据库主键。 */
        @TableId(type = IdType.AUTO)
        private Long id;
        /** 平台唯一充值申请号。 */
        private String rechargeNo;
        /** 充值目标资金账户主键。 */
        private Long accountId;
        /** 账户所属商户号。 */
        private String merchantId;
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
        /** 提交人登录账号快照，仅用于 admin 自审边界审计。 */
        private String submitLoginAccount;
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
        /** 客户端唯一请求号，数据库唯一键防止重复提交。 */
        private String requestId;
        /** 最终入账流水号，未完成复核时为空。 */
        private String ledgerNo;
        /** 最终入账系统时间，未完成复核时为空。 */
        private LocalDateTime postedTime;
        /** 充值申请创建系统时间。 */
        private LocalDateTime createTime;
        /** 充值申请最近修改系统时间。 */
        private LocalDateTime updateTime;
        /** 逻辑删除标识，零表示有效。 */
        private Long deleted;
    }
}
