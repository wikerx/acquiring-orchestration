package com.scott.payment.payment.api.internal.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : TransactionCallbackInboxAcceptResultDTO
 * @date : 2026-10-17 12:00
 * @email : scott_x@163.com
 * @description : 渠道回调 Inbox 内部受理结果，位于 service-payment 内部 DTO 层，只表示持久化受理或数据库幂等命中，不表示交易已完成。
 * @status : create
 */
@Data
public class TransactionCallbackInboxAcceptResultDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Inbox 业务号；重复回调返回首次受理记录的业务号。 */
    private String inboxId;

    /** Inbox 当前状态，不代表平台交易状态。 */
    private String inboxStatus;

    /** 受理结果：ACCEPTED 或 DUPLICATE。 */
    private String acceptResult;
}
