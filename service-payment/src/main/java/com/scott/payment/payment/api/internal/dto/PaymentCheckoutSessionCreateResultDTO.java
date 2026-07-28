package com.scott.payment.payment.api.internal.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 创建 Hosted Checkout 会话内部结果。
 */
@Data
public class PaymentCheckoutSessionCreateResultDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String checkoutSessionId;
    private String checkoutTokenId;
    private String checkoutUrl;
    private String checkoutStatus;
    private LocalDateTime expireTime;
    private Boolean idempotentHit;
}
