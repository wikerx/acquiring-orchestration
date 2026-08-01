package com.scott.payment.payment.api.internal.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

/**
 * Hosted Checkout 3DS 浏览器回跳内部命令。
 */
@Data
public class PaymentCheckoutThreeDsReturnCommandDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 一次性 3DS 回跳令牌摘要，禁止传递或持久化令牌明文。 */
    @NotBlank(message = "threeDsReturnTokenHash is required")
    private String threeDsReturnTokenHash;

    /** Hosted Checkout 会话号，必须与回跳令牌绑定。 */
    @NotBlank(message = "checkoutSessionId is required")
    private String checkoutSessionId;

    /** 发起本次 3DS 的支付尝试号，必须与回跳令牌绑定。 */
    @NotBlank(message = "checkoutAttemptId is required")
    private String checkoutAttemptId;

    /** 已脱敏的 3DS 认证数据 JSON，不能包含原始 CAVV 或完整协议载荷。 */
    private String authenticationDataJsonMasked;
    /** 当前调用链追踪号。 */
    private String traceId;
    /** 客户端 IP 摘要。 */
    private String clientIpHash;
    /** User-Agent 摘要。 */
    private String userAgentHash;
}
