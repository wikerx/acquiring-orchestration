package com.scott.payment.payment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 渠道勾兑异常案件实体，位于支付持久化层，保存脱敏证据、资金差异快照、精确分片时间和案件处置状态。
 */
@Data
@TableName("transaction_abnormal_event")
public class TransactionAbnormalEventDO implements Serializable {

    private static final long serialVersionUID = 1L;


    @TableId(type = IdType.AUTO)
    private Long id;

    private String abnormalEventId;
    /**
     * 平台交易号，由支付核心生成，用于串联主单、动作单、渠道请求、回调和通知。
     */
    private String transactionId;
    /**
     * 平台操作号，由支付核心生成，用于定位一次授权、请款、退款、撤销或回调处理动作。
     */
    private String operationId;
    /**
     * {@code abnormalType}，用于区分 {@code TransactionAbnormalEventDO} 记录的处理类别、配置维度或外部协议枚举。
     */
    private String abnormalType;
    /**
     * 持久化的{@code abnormalLevel}，用于还原当前记录的业务事实。
     */
    private String abnormalLevel;

    private String eventStatus;
    /**
     * 来源记录类型，用于区分 {@code TransactionAbnormalEventDO} 记录的处理类别、配置维度或外部协议枚举。
     */
    private String sourceRecordType;

    private String sourceRecordId;

    private String abnormalDescription;
    /**
     * 持久化的{@code rawReferenceJson}，用于还原当前记录的业务事实。
     */
    private String rawReferenceJson;
    /**
     * 持久化的{@code firstSeenTime}，用于还原当前记录的业务事实。
     */
    private LocalDateTime firstSeenTime;
    /**
     * 持久化的{@code resolvedTime}，用于还原当前记录的业务事实。
     */
    private LocalDateTime resolvedTime;
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
     * 持久化的{@code deduplicationKey}，用于还原当前记录的业务事实。
     */
    private String deduplicationKey;
    /**
     * 商户号，用于限定商户配置、交易数据、风控规则和权限归属。
     */
    private String merchantId;
    /**
     * 商户订单号，由商户生成并在同一商户范围内用于交易幂等、查询和对账。
     */
    private String merchantOrderNo;
    /**
     * 原平台交易号，用于将请款、退款、撤销、增量授权等后续动作关联到原始交易。
     */
    private String sourceTransactionId;
    /**
     * 持久化的来源交易日期时间，用于还原当前记录的业务事实。
     */
    private LocalDateTime sourceTransactionDateTime;
    /**
     * 持久化的{@code rootTransactionDateTime}，用于还原当前记录的业务事实。
     */
    private LocalDateTime rootTransactionDateTime;
    /**
     * 交易类型，标识本次动作是支付、授权、请款、退款、撤销还是增量授权，用于选择状态机和渠道能力。
     */
    private String transactionType;

    private String platformStatus;
    /**
     * 渠道编码，用于定位 MPGS、WorldPay 等渠道适配实现和路由配置。
     */
    private String channelCode;
    /**
     * 渠道订单号，由渠道返回，用于渠道查询、回调匹配和对账。
     */
    private String channelOrderNo;
    /**
     * 平台交易号，由支付核心生成，用于串联主单、动作单、渠道请求、回调和通知。
     */
    private String channelTransactionId;

    private String channelStatus;
    /**
     * 持久化的渠道匹配结果，用于还原当前记录的业务事实。
     */
    private String channelMatchResult;
    /**
     * 持久化的{@code detectSource}，用于还原当前记录的业务事实。
     */
    private String detectSource;

    private String platformCurrency;

    private BigDecimal platformAmount;

    private String channelCurrency;

    private BigDecimal channelAmount;

    private BigDecimal amountDifference;
    /**
     * 交易币种的小数位数，用于主币种单位与最小货币单位之间的精确转换。
     */
    private Integer currencyExponent;
    /**
     * 持久化的{@code lastSeenTime}，用于还原当前记录的业务事实。
     */
    private LocalDateTime lastSeenTime;

    private Integer occurrenceCount;

    private String assignedToId;

    private String assignedToName;
    /**
     * 持久化的{@code assignedTime}，用于还原当前记录的业务事实。
     */
    private LocalDateTime assignedTime;
    /**
     * {@code resolutionType}，用于区分 {@code TransactionAbnormalEventDO} 记录的处理类别、配置维度或外部协议枚举。
     */
    private String resolutionType;

    private String resolutionReferenceId;
    /**
     * 持久化的商户通知必需标识，用于还原当前记录的业务事实。
     */
    private Integer merchantNotifyRequired;

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
