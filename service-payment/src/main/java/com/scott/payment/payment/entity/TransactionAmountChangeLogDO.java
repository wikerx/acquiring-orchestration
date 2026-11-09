package com.scott.payment.payment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 交易金额变动日志实体，位于 service-payment 持久化层，记录授权、请款、退款、撤销等动作对生命周期累计金额的影响。
 */
@Data
@TableName("transaction_amount_change_log")
public class TransactionAmountChangeLogDO implements Serializable {

    private static final long serialVersionUID = 1L;


    @TableId(type = IdType.AUTO)
    private Long id;


    private String amountChangeId;

    /**
     * 平台交易号，由支付核心生成，用于串联主单、动作单、渠道请求、回调和通知。
     */
    private String transactionId;

    /**
     * 平台操作号，由支付核心生成，用于定位一次授权、请款、退款、撤销或回调处理动作。
     */
    private String operationId;

    /**
     * 原平台交易号，用于将请款、退款、撤销、增量授权等后续动作关联到原始交易。
     */
    private String sourceTransactionId;

    /**
     * 变更类型，用于区分 {@code TransactionAmountChangeLogDO} 记录的处理类别、配置维度或外部协议枚举。
     */
    private String changeType;


    private String amountCurrency;


    private BigDecimal changeAmount;

    /**
     * 本次金额变动对应的商户标签币种。
     */
    private String labelAmountCurrency;

    /**
     * 本次金额变动对应的商户标签金额。
     */
    private BigDecimal labelChangeAmount;

    /**
     * 变动前累计标签授权金额。
     */
    private BigDecimal labelAuthorizedBefore;

    /**
     * 变动后累计标签授权金额。
     */
    private BigDecimal labelAuthorizedAfter;

    /**
     * 变动前累计标签请款金额。
     */
    private BigDecimal labelCapturedBefore;

    /**
     * 变动后累计标签请款金额。
     */
    private BigDecimal labelCapturedAfter;

    /**
     * 变动前累计标签退款金额。
     */
    private BigDecimal labelRefundedBefore;

    /**
     * 变动后累计标签退款金额。
     */
    private BigDecimal labelRefundedAfter;

    /**
     * 变动前标签可请款金额。
     */
    private BigDecimal labelAvailableCaptureBefore;

    /**
     * 变动后标签可请款金额。
     */
    private BigDecimal labelAvailableCaptureAfter;

    /**
     * 变动前标签可退款金额。
     */
    private BigDecimal labelAvailableRefundBefore;

    /**
     * 变动后标签可退款金额。
     */
    private BigDecimal labelAvailableRefundAfter;

    /**
     * 持久化的{@code authorizedBefore}，用于还原当前记录的业务事实。
     */
    private BigDecimal authorizedBefore;

    /**
     * 持久化的{@code authorizedAfter}，用于还原当前记录的业务事实。
     */
    private BigDecimal authorizedAfter;

    /**
     * 持久化的{@code capturedBefore}，用于还原当前记录的业务事实。
     */
    private BigDecimal capturedBefore;

    /**
     * 持久化的{@code capturedAfter}，用于还原当前记录的业务事实。
     */
    private BigDecimal capturedAfter;

    /**
     * 持久化的{@code refundedBefore}，用于还原当前记录的业务事实。
     */
    private BigDecimal refundedBefore;

    /**
     * 持久化的{@code refundedAfter}，用于还原当前记录的业务事实。
     */
    private BigDecimal refundedAfter;

    /**
     * 持久化的{@code availableCaptureBefore}，用于还原当前记录的业务事实。
     */
    private BigDecimal availableCaptureBefore;

    /**
     * 持久化的{@code availableCaptureAfter}，用于还原当前记录的业务事实。
     */
    private BigDecimal availableCaptureAfter;

    /**
     * 持久化的{@code availableRefundBefore}，用于还原当前记录的业务事实。
     */
    private BigDecimal availableRefundBefore;

    /**
     * 持久化的{@code availableRefundAfter}，用于还原当前记录的业务事实。
     */
    private BigDecimal availableRefundAfter;

    /**
     * 持久化的变更原因，用于还原当前记录的业务事实。
     */
    private String changeReason;

    /**
     * 持久化的变更时间，用于还原当前记录的业务事实。
     */
    private LocalDateTime changeTime;

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

    /**
     * 记录创建时刻，持久化精度为毫秒。
     */
    private LocalDateTime createTime;
}
