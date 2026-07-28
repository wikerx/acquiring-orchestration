package com.scott.payment.payment.service;

import com.scott.payment.payment.api.internal.dto.PaymentCheckoutPaymentSubmitCommandDTO;
import com.scott.payment.payment.entity.PaymentCheckoutAttemptDO;
import com.scott.payment.payment.entity.PaymentCheckoutSessionDO;
import com.scott.payment.payment.service.dto.PaymentCheckoutThreeDsResultDTO;

/**
 * Hosted Checkout 3DS 编排服务。
 */
public interface PaymentCheckoutThreeDsService {

    /**
     * 对一次收银台付款尝试执行 3DS 认证前置编排。
     *
     * <p>实现只返回认证状态和质询页面信息，不直接写入收银台会话状态；状态落库由
     * {@link PaymentCheckoutService} 统一按 CAS 规则处理，避免渠道结果和平台状态机交叉覆盖。</p>
     *
     * @param sessionDO 收银台会话快照
     * @param attemptDO 本次付款尝试快照
     * @param commandDTO 付款人提交的卡信息和浏览器上下文
     * @param returnUrl 认证完成后回到平台 3DS bridge 的地址
     * @return 3DS 认证摘要，包含质询、失败、处理中或免质询结果
     */
    PaymentCheckoutThreeDsResultDTO authenticate(PaymentCheckoutSessionDO sessionDO,
                                                 PaymentCheckoutAttemptDO attemptDO,
                                                 PaymentCheckoutPaymentSubmitCommandDTO commandDTO,
                                                 String returnUrl);
}
