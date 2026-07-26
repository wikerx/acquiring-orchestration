package com.scott.payment.payment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : TransactionOrderDO
 * @date : 2026-07-14 17:35
 * @email : scott_x@163.com
 * @description : 交易生命周期主单实体，位于 service-payment 持久化层，使用 operation_id 关联同一原始交易生命周期，保存授权、请款、退款等动作共享的当前状态和金额汇总。
 * @status : create
 */
@Data
@TableName("transaction_order")
public class TransactionOrderDO implements Serializable {

    /**
     * 序列化版本号，用于本地缓存、测试和后续补偿场景的对象兼容。
     */
    private static final long serialVersionUID = 1L;

    /**
     * 物理表主键 ID，季度分表可按系统分表规则设置自增起始值。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 平台内部生命周期关联标识，同一原始交易生命周期内保持不变，不返回商户。
     */
    private String operationId;

    /**
     * 生命周期内首个平台开户交易 ID。
     */
    private String rootTransactionId;

    /**
     * 生命周期内最近一次平台开户交易 ID。
     */
    private String latestTransactionId;

    /**
     * 平台商户号。
     */
    private String merchantId;

    /**
     * 商户原始订单号，用于商户查询和创建交易幂等。
     */
    private String merchantOrderNo;

    /**
     * 商户本次 API 请求唯一标识，来自 orderInfo.orderId，用于幂等和排查。
     */
    private String merchantOrderId;

    /**
     * 源平台交易 ID，用于后续补单、复制或争议扩展关联。
     */
    private String sourceTransactionId;

    /**
     * 支付方式，如 BANK_CARD、APPLE_PAY；当前卡交易默认 BANK_CARD。
     */
    private String paymentMethod;

    /**
     * 卡品牌或支付品牌，如 VISA、MASTERCARD。
     */
    private String paymentBrand;

    /**
     * 首个交易类型，对齐字典 transaction_type。
     */
    private String transactionType;

    /**
     * 生命周期当前交易状态，对齐字典 transaction_status。
     */
    private String transactionStatus;

    /**
     * 当前内部处理阶段，如 CHANNEL_PROCESSING、WAITING_3DS、FINISHED。
     */
    private String processStage;

    /**
     * 挂起原因码，仅 PENDING 状态使用。
     */
    private String pendingReasonCode;

    /**
     * 后台可见失败原因码，仅 FAILED 状态使用。
     */
    private String failReasonCode;

    /**
     * 后台可见失败原因描述，优先保存渠道或状态机真实原因。
     */
    private String failReasonMessage;

    /**
     * 商户可见失败原因描述，避免暴露过细渠道规则。
     */
    private String merchantVisibleMessage;

    /**
     * 付款人可见失败原因描述，避免暴露渠道、风控或反欺诈细节。
     */
    private String payerVisibleMessage;

    /**
     * 商户上送或页面展示的原始交易币种，ISO 4217 三位代码。
     */
    private String labelCurrency;

    /**
     * 商户上送或页面展示的原始交易金额，主币种单位。
     */
    private BigDecimal labelAmount;

    /**
     * 系统交易币种，当前未启用 DCC/EDC 时与标签币种一致。
     */
    private String transactionCurrency;

    /**
     * 系统交易金额，当前未启用 DCC/EDC 时与标签金额一致。
     */
    private BigDecimal transactionAmount;

    /**
     * 上送渠道的币种，当前首次交易与系统交易币种一致。
     */
    private String channelRequestCurrency;

    /**
     * 上送渠道的金额，当前首次交易与系统交易金额一致。
     */
    private BigDecimal channelRequestAmount;

    /**
     * 预计或最终结算币种。
     */
    private String settlementCurrency;

    /**
     * 预计或最终结算金额。
     */
    private BigDecimal settlementAmount;

    /**
     * 交易币种默认小数位精度，来自 ISO 字典。
     */
    private Integer currencyExponent;

    /**
     * 是否启用 DCC，0 否、1 是；当前骨架阶段默认不启用。
     */
    private Integer dccEnabled;

    /**
     * 是否启用 EDC，0 否、1 是；当前骨架阶段默认不启用。
     */
    private Integer edcEnabled;

    /**
     * 标签金额转交易金额使用的汇率；未换汇时为空。
     */
    private BigDecimal transactionRate;

    /**
     * 汇率来源，如 PLATFORM、CHANNEL、MERCHANT。
     */
    private String rateSource;

    /**
     * 汇率生效或报价时间。
     */
    private LocalDateTime rateTime;

    /**
     * 累计授权成功金额，交易币种单位。
     */
    private BigDecimal authorizedAmount;

    /**
     * 累计授权取消、预授权取消或未请款金额释放成功金额，交易币种单位。
     */
    private BigDecimal authorizedCancelAmount;

    /**
     * 累计请款成功金额，交易币种单位。
     */
    private BigDecimal capturedAmount;

    /**
     * 累计退款成功金额，交易币种单位。
     */
    private BigDecimal refundedAmount;

    /**
     * 累计拒付金额，交易币种单位。
     */
    private BigDecimal chargebackAmount;

    /**
     * 当前可请款金额，交易币种单位。
     */
    private BigDecimal availableCaptureAmount;

    /**
     * 当前可退款金额，交易币种单位。
     */
    private BigDecimal availableRefundAmount;

    /**
     * 渠道结果勾兑/查询确认状态。
     */
    private String channelMatchStatus;

    /**
     * 当前生命周期结算状态。
     */
    private String settlementStatus;

    /**
     * 当前生命周期对账状态。
     */
    private String reconciliationStatus;

    /**
     * 当前生命周期入账状态。
     */
    private String accountingStatus;

    /**
     * 最近一次渠道查询确认结果。
     */
    private String channelMatchResult;

    /**
     * 渠道查询确认次数。
     */
    private Integer channelMatchCount;

    /**
     * 最近一次渠道查询确认请求 ID。
     */
    private String lastChannelMatchRequestId;

    /**
     * 最近一次渠道查询确认时间。
     */
    private LocalDateTime lastChannelMatchTime;

    /**
     * 下一次渠道查询确认计划时间。
     */
    private LocalDateTime nextChannelMatchTime;

    /**
     * 最近一次渠道查询确认失败原因。
     */
    private String channelMatchFailReason;

    /**
     * 最近一次结算批次号。
     */
    private String settlementBatchNo;

    /**
     * 最近一次对账批次号。
     */
    private String reconciliationBatchNo;

    /**
     * 渠道信息 ID。
     */
    private Long channelId;

    /**
     * 渠道编码，如 MPGS。
     */
    private String channelCode;

    /**
     * 交易使用的渠道 MID 配置 ID。
     */
    private Long channelMidConfigId;

    /**
     * 渠道真实商户号或渠道 MID，来自路由结果快照。
     */
    private String channelMerchantId;

    /**
     * 渠道侧主订单号。
     */
    private String channelOrderNo;

    /**
     * 最近一次内风控决策。
     */
    private String internalRiskDecision;

    /**
     * 最近一次内风控评估流水号。
     */
    private String internalRiskRecordNo;

    /**
     * 商户通知地址 SHA-256。
     */
    private String callbackUrlHash;

    /**
     * 交易业务时间，所有交易分表统一字段，数据库类型必须为 DATETIME(3)。
     */
    private LocalDateTime transactionDateTime;

    /**
     * 交易业务时间对应 UTC 时间，用于跨时区排序和审计。
     */
    private LocalDateTime transactionUtcTime;

    /**
     * 交易业务时间所属 IANA 时区。
     */
    private String transactionTimeZone;

    /**
     * 交易发生时区偏移，如 +08:00。
     */
    private String transactionTimezoneOffset;

    /**
     * 最近一次交易状态更新时间。
     */
    private LocalDateTime lastStatusTime;

    /**
     * 乐观锁版本号，用于状态机 CAS 更新。
     */
    private Integer version;

    /**
     * 软删除标识，0 表示未删除。
     */
    private Integer deleted;

    /**
     * 记录创建时间，数据库类型必须为 DATETIME(3)。
     */
    private LocalDateTime createTime;

    /**
     * 记录最后更新时间，数据库类型必须为 DATETIME(3)。
     */
    private LocalDateTime updateTime;

}
