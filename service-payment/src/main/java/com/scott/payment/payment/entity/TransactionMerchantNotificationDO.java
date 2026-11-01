package com.scott.payment.payment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 商户通知任务实体，位于 service-payment 持久化层，保存交易结果通知商户的任务状态、配置快照和重试计划。
 */
@Data
@TableName("transaction_merchant_notification")
public class TransactionMerchantNotificationDO implements Serializable {

    private static final long serialVersionUID = 1L;


    @TableId(type = IdType.AUTO)
    private Long id;


    private String notifyId;

    /**
     * 平台交易号，由支付核心生成，用于串联主单、动作单、渠道请求、回调和通知。
     */
    private String transactionId;

    /**
     * 平台操作号，由支付核心生成，用于定位一次授权、请款、退款、撤销或回调处理动作。
     */
    private String operationId;

    /**
     * 商户号，用于限定商户配置、交易数据、风控规则和权限归属。
     */
    private String merchantId;

    /**
     * 商户订单号，由商户生成并在同一商户范围内用于交易幂等、查询和对账。
     */
    private String merchantOrderNo;

    /**
     * 通知类型，用于区分交易成功、失败、处理中或补偿重试等商户通知场景。
     */
    private String notifyType;

    /**
     * {@code eventType}，用于区分 {@code TransactionMerchantNotificationDO} 记录的处理类别、配置维度或外部协议枚举。
     */
    private String eventType;


    private String notifyStatus;


    private String notifyConfigVersion;

    /**
     * 商户回调地址明文；只允许用于通知投递，日志和管理页面继续使用脱敏摘要。
     */
    private String callbackUrl;

    /**
     * 与同步 OpenAPI 响应字段口径一致的商户通知业务载荷明文 JSON。
     */
    private String payloadJson;


    private String targetUrlHash;


    private String targetUrlMasked;


    private String payloadJsonMasked;

    /**
     * {@code signType}，用于区分 {@code TransactionMerchantNotificationDO} 记录的处理类别、配置维度或外部协议枚举。
     */
    private String signType;

    /**
     * 持久化的{@code lastAttemptNo}，用于还原当前记录的业务事实。
     */
    private Integer lastAttemptNo;

    /**
     * 重试次数，用于记录 MQ、任务或商户通知当前已执行的重试轮次。
     */
    private Integer maxRetryCount;

    /**
     * 持久化的{@code nextRetryTime}，用于还原当前记录的业务事实。
     */
    private LocalDateTime nextRetryTime;

    /**
     * 持久化的成功时间，用于还原当前记录的业务事实。
     */
    private LocalDateTime successTime;

    /**
     * 持久化的{@code failReason}，用于还原当前记录的业务事实。
     */
    private String failReason;

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
