package com.scott.payment.payment.api.internal.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : TransactionChannelCallbackResultDTO
 * @date : 2026-07-14 22:38
 * @email : scott_x@163.com
 * @description : 渠道回调内部落库结果，位于 service-payment 内部接口 DTO 层，返回回调日志号、业务回调号和当前处理状态。
 * @status : create
 */
@Data
public class TransactionChannelCallbackResultDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 渠道回调原始日志 ID。
     */
    private String callbackLogId;

    /**
     * 渠道回调业务记录 ID。
     */
    private String callbackId;

    /**
     * 平台交易 ID。
     */
    private String transactionId;

    /**
     * 回调处理状态。
     */
    private String callbackStatus;

    /**
     * 回调处理结果。
     */
    private String processResult;

    /**
     * 失败原因。
     */
    private String failReason;
}
