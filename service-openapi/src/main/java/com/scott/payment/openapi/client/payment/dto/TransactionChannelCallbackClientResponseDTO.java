package com.scott.payment.openapi.client.payment.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : TransactionChannelCallbackClientResponseDTO
 * @date : 2026-07-14 22:58
 * @email : scott_x@163.com
 * @description : service-payment 渠道回调记录内部响应 DTO，返回回调日志号、业务记录号和当前处理结果。
 * @status : create
 */
@Data
public class TransactionChannelCallbackClientResponseDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String callbackLogId;

    private String callbackId;

    private String transactionId;

    private String callbackStatus;

    private String processResult;

    private String failReason;
}
