package com.scott.payment.payment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : TransactionChannelCallbackLogDO
 * @date : 2026-07-14 22:18
 * @email : scott_x@163.com
 * @description : 渠道回调原始日志实体，位于 service-payment 持久化层，保存渠道回调脱敏原文、验签结果、IP 校验结果和平台响应摘要。
 * @status : create
 */
@Data
@TableName("transaction_channel_callback_log")
public class TransactionChannelCallbackLogDO implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String callbackLogId;

    private String transactionId;

    private String operationId;

    private String channelCode;

    private String callbackType;

    private String channelOrderNo;

    private String channelTransactionId;

    private String requestUri;

    private String httpMethod;

    private String sourceIp;

    private String requestHeaderJsonMasked;

    private String requestBodyJsonMasked;

    private Integer signatureValid;

    private Integer ipAllowed;

    private String platformResponseCode;

    private String platformResponseBody;

    private LocalDateTime callbackReceivedTime;

    private LocalDateTime transactionDateTime;

    private LocalDateTime transactionUtcTime;

    private String transactionTimeZone;

    private LocalDateTime createTime;
}
