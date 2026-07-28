package com.scott.payment.payment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Hosted Checkout 业务和页面事件实体。
 */
@Data
@TableName("payment_checkout_event")
public class PaymentCheckoutEventDO implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String checkoutEventId;
    private String checkoutSessionId;
    private String checkoutAttemptId;
    private String merchantId;
    private String eventType;
    private String eventStage;
    private String eventResult;
    private String checkoutStatusBefore;
    private String checkoutStatusAfter;
    private String attemptStatusBefore;
    private String attemptStatusAfter;
    private String operationId;
    private String transactionId;
    private LocalDateTime transactionDateTime;
    private String traceId;
    private String requestId;
    private String clientIpHash;
    private String userAgentHash;
    private String originHash;
    private String refererHash;
    private String eventPayloadJson;
    private LocalDateTime eventTime;
    private LocalDateTime createTime;
}
