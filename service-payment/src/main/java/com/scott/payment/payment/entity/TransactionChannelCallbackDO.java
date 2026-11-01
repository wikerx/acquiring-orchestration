package com.scott.payment.payment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 渠道回调业务记录实体，位于 service-payment 持久化层，保存回调幂等键、解析结果和状态推进结果，避免重复回调重复处理资金状态。
 */
@Data
@TableName("transaction_channel_callback")
public class TransactionChannelCallbackDO implements Serializable {

    private static final long serialVersionUID = 1L;


    @TableId(type = IdType.AUTO)
    private Long id;


    private String callbackId;


    private String callbackLogId;

    /**
     * 平台交易号，由支付核心生成，用于串联主单、动作单、渠道请求、回调和通知。
     */
    private String transactionId;

    /**
     * 平台操作号，由支付核心生成，用于定位一次授权、请款、退款、撤销或回调处理动作。
     */
    private String operationId;

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

    /**
     * 回调事件类型，用于区分渠道授权、请款、退款、撤销和状态同步事件。
     */
    private String callbackType;

    /**
     * 回调事件类型，用于区分渠道授权、请款、退款、撤销和状态同步事件。
     */
    private String channelEventType;


    private String callbackStatus;

    /**
     * 资金类请求幂等键，用于在同一商户和交易动作范围内识别重复提交。
     */
    private String idempotencyKey;


    private Integer signatureValid;

    /**
     * 持久化的{@code ipAllowed}，用于还原当前记录的业务事实。
     */
    private Integer ipAllowed;


    private String parsedTransactionStatus;


    private String previousTransactionStatus;


    private String targetTransactionStatus;

    /**
     * 持久化的{@code processResult}，用于还原当前记录的业务事实。
     */
    private String processResult;

    /**
     * 持久化的{@code failReason}，用于还原当前记录的业务事实。
     */
    private String failReason;

    /**
     * 持久化的{@code callbackReceivedTime}，用于还原当前记录的业务事实。
     */
    private LocalDateTime callbackReceivedTime;

    /**
     * 持久化的{@code processedTime}，用于还原当前记录的业务事实。
     */
    private LocalDateTime processedTime;

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
