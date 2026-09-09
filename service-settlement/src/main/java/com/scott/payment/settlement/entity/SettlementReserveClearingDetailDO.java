package com.scott.payment.settlement.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SettlementReserveClearingDetailDO
 * @date : 2026-08-26 23:10
 * @email : scott_x@163.com
 * @description : 结算侧只读保证金清分事实投影；保证金仍以标签币种保存，仅在结算结果阶段使用批次统一汇率。
 * @status : create
 */
@Data
public class SettlementReserveClearingDetailDO {
    /** 保证金清分明细数据库主键。 */
    private Long id;
    /** 保证金清分明细稳定业务号。 */
    private String reserveClearingDetailNo;
    /** 清分幂等财务状态标识。 */
    private String financeStateId;
    /** 当前保证金动作所属平台交易号；纯调整允许为空。 */
    private String transactionId;
    /** 当前清分动作单号；纯调整允许为空。 */
    private String operationId;
    /** 原支付交易号，用于保证金聚合归属。 */
    private String originalTransactionId;
    /** 原支付交易分片时间；无交易来源的调整允许为空。 */
    private LocalDateTime originalTransactionDateTime;
    /** 被返还或调整的来源保证金明细号；HOLD 时允许为空。 */
    private String sourceReserveDetailNo;
    /** 保证金事实所属平台商户号。 */
    private String merchantId;
    /** 来源支付类型快照；纯调整允许为空或约定值。 */
    private String paymentType;
    /** 来源支付方式快照；纯调整允许为空或约定值。 */
    private String paymentMethod;
    /** 来源平台交易类型；纯调整不得伪造交易类型。 */
    private String transactionType;
    /** 清分修订号；非交易保证金业务使用其来源版本。 */
    private Integer clearingRevision;
    /** 同一清分修订内确定性明细行号。 */
    private Integer lineNo;
    /** HOLD、RETURN、RELEASE 或 ADJUSTMENT。 */
    private String reserveActionType;
    /** CREDIT 增加商户保证金权益，DEBIT 减少权益。 */
    private String direction;
    /** 原支付标签 ISO 币种，不在清分阶段换汇。 */
    private String reserveCurrency;
    /** 保证金币种 ISO 小数位。 */
    private Integer reserveCurrencyExponent;
    /** HOLD 扣留金额，其他动作通常为零。 */
    private BigDecimal retainedAmount;
    /** RETURN 返还金额，其他动作通常为零。 */
    private BigDecimal returnedAmount;
    /** RELEASE 到期释放金额，其他动作通常为零。 */
    private BigDecimal releasedAmount;
    /** ADJUSTMENT 非负发生额，实际增减由 direction 决定。 */
    private BigDecimal adjustmentAmount;
    /** 清分冻结舍入模式。 */
    private String roundingMode;
    /** 保证金规则和计算公式审计快照。 */
    private String formulaSnapshot;
    /** HOLD 预计可释放业务日期；其他动作允许为空。 */
    private LocalDate expectedReserveReleaseDate;
    /** 清分事实有效状态，结算只读取有效终态。 */
    private String recordStatus;
    /** 当前交易动作分片时间；纯保证金调整允许为空。 */
    private LocalDateTime transactionDateTime;
}
