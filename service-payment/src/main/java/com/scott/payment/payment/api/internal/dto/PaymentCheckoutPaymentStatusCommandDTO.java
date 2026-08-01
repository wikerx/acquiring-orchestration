package com.scott.payment.payment.api.internal.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

/**
 * Hosted Checkout 查询支付状态内部命令。
 */
@Data
public class PaymentCheckoutPaymentStatusCommandDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 不透明访问令牌摘要，禁止传入令牌明文。 */
    @NotBlank(message = "tokenHash is required")
    private String tokenHash;

    /** Hosted Checkout 会话号，必须与令牌摘要绑定。 */
    @NotBlank(message = "checkoutSessionId is required")
    private String checkoutSessionId;

    /** 可选支付尝试号；为空时按服务约定查询当前尝试。 */
    private String checkoutAttemptId;
    /** 当前调用链追踪号。 */
    private String traceId;
    /** 客户端 IP 摘要。 */
    private String clientIpHash;
    /** User-Agent 摘要。 */
    private String userAgentHash;
}
