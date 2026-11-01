package com.scott.payment.payment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 交易财务状态持久化实体，仅承载已形成的结算、费用、对账和入账事实。
 */
@Data
@TableName("transaction_finance_state")
public class TransactionFinanceStateDO implements Serializable {

    private static final long serialVersionUID = 1L;


    @TableId(type = IdType.AUTO)
    private Long id;

    private String financeStateId;
    /**
     * 平台交易号，由支付核心生成，用于串联主单、动作单、渠道请求、回调和通知。
     */
    private String transactionId;
    /**
     * 平台操作号，由支付核心生成，用于定位一次授权、请款、退款、撤销或回调处理动作。
     */
    private String operationId;
    /**
     * 交易类型，标识本次动作是支付、授权、请款、退款、撤销还是增量授权，用于选择状态机和渠道能力。
     */
    private String transactionType;

    private String settlementStatus;

    private String settlementCurrency;
    /**
     * 持久化的结算汇率，用于还原当前记录的业务事实。
     */
    private BigDecimal settlementRate;

    private BigDecimal settlementAmount;

    private BigDecimal settlementFeeAmount;
    /**
     * 持久化的{@code feeItemsJson}，用于还原当前记录的业务事实。
     */
    private String feeItemsJson;
    /**
     * 持久化的结算日期，用于还原当前记录的业务事实。
     */
    private LocalDate settlementDate;
    /**
     * 持久化的{@code settlementCycle}，用于还原当前记录的业务事实。
     */
    private String settlementCycle;
    /**
     * 持久化的结算批次编号，用于还原当前记录的业务事实。
     */
    private String settlementBatchNo;

    private String reconciliationStatus;
    /**
     * 持久化的{@code reconciliationDate}，用于还原当前记录的业务事实。
     */
    private LocalDate reconciliationDate;
    /**
     * 持久化的{@code reconciliationBatchNo}，用于还原当前记录的业务事实。
     */
    private String reconciliationBatchNo;

    private String accountingStatus;
    /**
     * 持久化的{@code accountingTime}，用于还原当前记录的业务事实。
     */
    private LocalDateTime accountingTime;

    private String channelFeeCurrency;

    private BigDecimal channelFeeAmount;

    private String platformFeeCurrency;

    private BigDecimal platformFeeAmount;

    private String merchantReceivableCurrency;

    private BigDecimal merchantReceivableAmount;

    private String reserveCurrency;

    private BigDecimal reserveAmount;

    private String netSettlementCurrency;

    private BigDecimal netSettlementAmount;
    /**
     * 交易受理时刻，按交易业务时区解释并保留毫秒精度。
     */
    private LocalDateTime transactionDateTime;
    /**
     * 交易受理时刻对应的 UTC 时间，用于跨时区排序和对账。
     */
    private LocalDateTime transactionUtcTime;
    /**
     * 交易业务时区，使用 IANA 时区标识解释本地交易时间。
     */
    private String transactionTimeZone;

    private Integer version;
    /**
     * 逻辑删除标识；0 表示有效，1 表示已删除，查询必须沿用统一软删除口径。
     */
    private Integer deleted;
    /**
     * 记录创建时刻，持久化精度为毫秒。
     */
    private LocalDateTime createTime;
    /**
     * 记录最后更新时间，持久化精度为毫秒。
     */
    private LocalDateTime updateTime;
}
