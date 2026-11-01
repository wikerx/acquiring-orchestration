package com.scott.payment.payment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 渠道回调原始日志实体，位于 service-payment 持久化层，保存渠道回调脱敏原文、验签结果、IP 校验结果和平台响应摘要。
 */
@Data
@TableName("transaction_channel_callback_log")
public class TransactionChannelCallbackLogDO implements Serializable {

    private static final long serialVersionUID = 1L;


    @TableId(type = IdType.AUTO)
    private Long id;


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
     * 回调事件类型，用于区分渠道授权、请款、退款、撤销和状态同步事件。
     */
    private String callbackType;

    /**
     * 渠道订单号，由渠道返回，用于渠道查询、回调匹配和对账。
     */
    private String channelOrderNo;

    /**
     * 平台交易号，由支付核心生成，用于串联主单、动作单、渠道请求、回调和通知。
     */
    private String channelTransactionId;

    /**
     * 持久化的{@code requestUri}，用于还原当前记录的业务事实。
     */
    private String requestUri;


    private String httpMethod;

    /**
     * 持久化的{@code sourceIp}，用于还原当前记录的业务事实。
     */
    private String sourceIp;


    private String requestHeaderJsonMasked;


    private String requestBodyJsonMasked;


    private Integer signatureValid;

    /**
     * 持久化的{@code ipAllowed}，用于还原当前记录的业务事实。
     */
    private Integer ipAllowed;


    private String platformResponseCode;


    private String platformResponseBody;

    /**
     * 持久化的{@code callbackReceivedTime}，用于还原当前记录的业务事实。
     */
    private LocalDateTime callbackReceivedTime;

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
