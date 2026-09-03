package com.scott.payment.clearing.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ClearingTransactionFinanceStateDO
 * @date : 2026-08-26 09:12
 * @email : scott_x@163.com
 * @description : 动作级清分权威状态持久化实体；状态更新只能通过带分片时间、当前状态和版本的 Mapper CAS。
 * @status : create
 */
@Data
@TableName("transaction_finance_state")
public class ClearingTransactionFinanceStateDO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 数据库自增主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 动作财务状态业务号。 */
    private String financeStateId;
    /** 动作交易号，与分片时间共同定位逻辑表记录。 */
    private String transactionId;
    /** 动作操作号。 */
    private String operationId;
    /** 平台商户号。 */
    private String merchantId;
    /** 关联源交易号；无源动作时为空。 */
    private String sourceTransactionId;
    /** 商户标签 ISO 币种。 */
    private String labelCurrency;
    /** 平台统一交易类型。 */
    private String transactionType;
    /** 动作清分权威状态。 */
    private String clearingStatus;
    /** 当前有效清分修订号。 */
    private Integer clearingRevision;
    /** PROCESSING 租约拥有者；非处理中状态为空。 */
    private String processingOwner;
    /** PROCESSING 租约截止 UTC 时间；非处理中状态为空。 */
    private LocalDateTime processingDeadline;
    /** 业务延时重试累计次数。 */
    private Integer clearingRetryCount;
    /** 下一次允许业务重试 UTC 时间；无需重试时为空。 */
    private LocalDateTime nextRetryTime;
    /** 最近一次稳定失败码。 */
    private String lastFailureCode;
    /** 已限制长度的非敏感失败摘要。 */
    private String lastFailureMessage;
    /** 动作冻结费用方案 ID。 */
    private Long feePlanId;
    /** 动作冻结不可变费用方案版本 ID。 */
    private Long feePlanVersionId;
    /** 动作冻结费用版本号。 */
    private Integer feePlanVersionNo;
    /** 规范化费用快照 SHA-256。 */
    private String feeSnapshotHash;
    /** 清分本金标签金额，标签币种十进制主单位。 */
    private BigDecimal grossLabelAmount;
    /** 当前修订产生的费用组件币种数量。 */
    private Integer feeComponentCurrencyCount;
    /** 费用限额求值状态摘要。 */
    private String feeEvaluationStatus;
    /** 动作结算状态；清分只初始化和读取，不推进结算状态机。 */
    private String settlementStatus;
    /** 档案目标结算币种；清分不在该字段保存汇率或已换汇金额。 */
    private String settlementCurrency;
    /** 最早可结算业务日。 */
    private LocalDate settlementEligibleDate;
    /** 平台费用汇总币种；仅在单币种可汇总时有值。 */
    private String platformFeeCurrency;
    /** 平台费用汇总金额，十进制主单位；多币种不可汇总时为空。 */
    private BigDecimal platformFeeAmount;
    /** 费用返还汇总金额，与 platformFeeCurrency 同币种；不可汇总时为空。 */
    private BigDecimal feeReversalAmount;
    /** 商户应收汇总币种；仅在单币种可汇总时有值。 */
    private String merchantReceivableCurrency;
    /** 商户应收汇总金额，十进制主单位；多币种不可汇总时为空。 */
    private BigDecimal merchantReceivableAmount;
    /** 保证金标签币种；无保证金事实时为空。 */
    private String reserveCurrency;
    /** 本动作扣留保证金金额，标签币种十进制主单位。 */
    private BigDecimal reserveAmount;
    /** 本动作返还保证金金额，标签币种十进制主单位。 */
    private BigDecimal reserveReversalAmount;
    /** 当前动作预计保证金释放业务日；无扣留时为空。 */
    private LocalDate expectedReserveReleaseDate;
    /** 净结算摘要币种；只用于兼容查询，不是正式结算结果币种。 */
    private String netSettlementCurrency;
    /** 净结算摘要金额；正式统一汇率换汇和入账结果以 settlement 服务明细为准。 */
    private BigDecimal netSettlementAmount;
    /** 动作季度分片时间。 */
    private LocalDateTime transactionDateTime;
    /** 动作 UTC 业务时间。 */
    private LocalDateTime transactionUtcTime;
    /** 动作业务 IANA 时区。 */
    private String transactionTimeZone;
    /** 财务状态 CAS 版本。 */
    private Integer version;
}
