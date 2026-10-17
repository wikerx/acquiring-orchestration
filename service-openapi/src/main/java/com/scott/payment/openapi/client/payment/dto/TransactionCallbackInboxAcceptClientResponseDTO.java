package com.scott.payment.openapi.client.payment.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : TransactionCallbackInboxAcceptClientResponseDTO
 * @date : 2026-10-17 12:00
 * @email : scott_x@163.com
 * @description : service-payment 渠道回调 Inbox 受理响应，位于 service-openapi 客户端 DTO 层，仅用于确认回调已经可靠持久化。
 * @status : create
 */
@Data
public class TransactionCallbackInboxAcceptClientResponseDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Inbox 业务号；重复回调返回首次受理记录的业务号。 */
    private String inboxId;

    /** Inbox 当前消费状态。 */
    private String inboxStatus;

    /** 受理结果：ACCEPTED 或 DUPLICATE。 */
    private String acceptResult;
}
