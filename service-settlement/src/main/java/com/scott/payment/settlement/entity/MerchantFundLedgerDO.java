package com.scott.payment.settlement.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantFundLedgerDO
 * @date : 2026-09-01 00:00
 * @email : scott_x@163.com
 * @description : 结算侧不可变资金流水写入模型；每个批次仅追加一条净结算或冲正流水，并以 idempotencyKey 和账户序号唯一约束防止重复入账。
 * @status : create
 */
@Data
public class MerchantFundLedgerDO {
    /** 资金流水数据库主键，插入前允许为空。 */
    private Long id;
    /** 对运营展示的全局资金流水号，不允许为空。 */
    private String ledgerNo;
    /** 同一业务关联流水组号；冲正与原流水可据此归组。 */
    private String ledgerGroupNo;
    /** 被记账资金账户主键，不允许为空。 */
    private Long accountId;
    /** 资金流水所属平台商户号，不允许为空。 */
    private String merchantId;
    /** 资金业务类型，例如 SETTLEMENT 或 SETTLEMENT_REVERSAL。 */
    private String businessType;
    /** 运营可读流水摘要，不包含敏感请求信息。 */
    private String summary;
    /** 业务单号，结算场景为批次号或冲正业务号。 */
    private String businessNo;
    /** 产生该流水的正式结算批次号，不允许为空。 */
    private String settlementBatchNo;
    /** 流水 ISO 币种，必须等于账户和批次目标币种。 */
    private String currency;
    /** CREDIT 增加余额，DEBIT 扣减余额。 */
    private String direction;
    /** 非负记账发生额，单位由 currency 的 ISO exponent 决定。 */
    private BigDecimal amount;
    /** 本次记账前账户余额，与 currency 同单位。 */
    private BigDecimal balanceBefore;
    /** 本次记账后账户余额，与 currency 同单位。 */
    private BigDecimal balanceAfter;
    /** 账户内单调递增流水序号，用于审计账序。 */
    private Long accountSequence;
    /** AUTO、MANUAL_REVIEW 或 REVERSAL 等操作模式。 */
    private String operationMode;
    /** Maker 账户 ID；自动批次使用受约束系统主体。 */
    private Long operatorId;
    /** Maker 账户展示名，不包含登录凭据。 */
    private String operatorName;
    /** Checker 账户 ID；非 Maker-Checker 场景允许为空。 */
    private Long reviewerId;
    /** Checker 账户展示名；非复核场景允许为空。 */
    private String reviewerName;
    /** 人工操作原因；自动批次允许使用系统原因。 */
    private String operationReason;
    /** 复核意见；非复核场景允许为空。 */
    private String reviewComment;
    /** 资金业务实际发生时间，数据库精度为毫秒。 */
    private LocalDateTime businessTime;
    /** Maker 提交时间；自动批次允许为空。 */
    private LocalDateTime submitTime;
    /** Checker 决策时间；非复核场景允许为空。 */
    private LocalDateTime reviewTime;
    /** 余额和流水原子提交时间，不允许为空。 */
    private LocalDateTime postedTime;
    /** 贯穿命令的请求标识，用于审计追踪。 */
    private String requestId;
    /** 资金最终幂等键，数据库必须唯一。 */
    private String idempotencyKey;
    /** 冲正所引用的原资金流水主键；普通结算允许为空。 */
    private Long reversalOfLedgerId;
    /** 流水创建时间，数据库精度为毫秒。 */
    private LocalDateTime createTime;
}
