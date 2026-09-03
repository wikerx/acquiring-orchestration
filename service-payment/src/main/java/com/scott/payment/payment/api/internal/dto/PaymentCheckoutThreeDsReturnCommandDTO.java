package com.scott.payment.payment.api.internal.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentCheckoutThreeDsReturnCommandDTO
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : Hosted Checkout 3DS 浏览器回跳内部命令。
 * @status : create
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
    /** 浏览器使用新 nonce 重新加密的卡数据；服务器不持久化 PAN/CVV。 */
    @Valid
    @NotNull(message = "cardDataEnvelope is required")
    private PaymentCheckoutPaymentSubmitCommandDTO.CardDataEnvelopeDTO cardDataEnvelope;
    /** 解密后的卡数据只存在当前续传调用栈，禁止从 JSON 绑定。 */
    @JsonIgnore
    private PaymentCheckoutPaymentSubmitCommandDTO.CardInfoDTO cardInfo;
    /** 继续 AUTHENTICATE/PAY 所需的账单资料。 */
    @Valid
    private PaymentCheckoutPaymentSubmitCommandDTO.BillingCardHolderInfoDTO billingCardHolderInfo;
    /** 当前浏览器环境摘要。 */
    private String browserInfoJson;
    /** 当前调用链追踪号。 */
    private String traceId;
    /** 客户端 IP 摘要。 */
    private String clientIpHash;
    /** 继续 3DS 认证需要的真实 IP，仅允许在当前请求调用栈使用。 */
    private String payerIp;
    /** User-Agent 摘要。 */
    private String userAgentHash;
}
