package com.scott.payment.component.mq.message;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RefundExecutionMessage
 * @date : 2026-08-06 00:00
 * @email : scott_x@163.com
 * @description : 退款审批执行消息契约，只携带稳定审批标识和数据库路由时间，不携带卡数据、渠道凭据或完整报文。
 * @status : create
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class RefundExecutionMessage extends BaseMqMessage {

    private String approvalId;
    private String refundTransactionId;
    private LocalDateTime refundTransactionDateTime;
    private String sourceTransactionId;
    private LocalDateTime sourceTransactionDateTime;
    private LocalDateTime rootTransactionDateTime;
    private Integer expectedOperationVersion;
    private String eventType;
}
