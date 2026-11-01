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

    /**
     * {@code TransactionAmountChangeLogDO} 数据库主键，用于唯一标识当前记录。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 金额变更ID，用于定位 {@code TransactionAmountChangeLogDO} 关联的上游配置、渠道、账号、角色或业务记录。
     * <p>
     * 单位：由关联 currency 字段决定；格式：decimal 金额字符串或 BigDecimal；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：金额不得为负，交易金额通常必须大于 0；数据来源：数据库表记录或持久化写入对象。
     * 字段关系：必须与 currency 或同名币种字段一起解释。
     * </p>
     */
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
     * <p>
     * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：数据库表记录或持久化写入对象。
     * </p>
     */
    private String changeType;

    /**
     * 金额币种，表示当前交易、费用、限额或统计口径下的金额值。
     * <p>
     * 单位：无；格式：ISO 4217 三位大写币种代码；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值必须来自平台支持币种；数据来源：数据库表记录或持久化写入对象。
     * 字段关系：必须与 currency 或同名币种字段一起解释。
     * </p>
     */
    private String amountCurrency;

    /**
     * 变更金额，表示当前交易、费用、限额或统计口径下的金额值。
     * <p>
     * 单位：由关联 currency 字段决定；格式：decimal 金额字符串或 BigDecimal；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：金额不得为负，交易金额通常必须大于 0；数据来源：数据库表记录或持久化写入对象。
     * 字段关系：必须与 currency 或同名币种字段一起解释。
     * </p>
     */
    private BigDecimal changeAmount;

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
