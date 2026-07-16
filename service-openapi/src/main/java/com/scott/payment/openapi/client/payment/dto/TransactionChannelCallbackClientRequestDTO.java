package com.scott.payment.openapi.client.payment.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : TransactionChannelCallbackClientRequestDTO
 * @date : 2026-07-14 22:56
 * @email : scott_x@163.com
 * @description : service-openapi 转发渠道回调到 service-payment 的内部请求 DTO，保存安全校验结果和脱敏前原文，由支付核心统一脱敏落库。
 * @status : create
 */
@Data
public class TransactionChannelCallbackClientRequestDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String channelCode;

    private String callbackType;

    private String transactionId;

    private String channelOrderNo;

    private String channelTransactionId;

    private String channelEventType;

    private String requestUri;

    private String httpMethod;

    private String sourceIp;

    private Map<String, String> requestHeaders;

    private String requestBody;

    private Boolean signatureValid;

    private Boolean ipAllowed;

    private LocalDateTime receivedTime;
}
