package com.scott.payment.payment.service;

import com.scott.payment.payment.api.internal.dto.PaymentCheckoutPaymentSubmitCommandDTO;
import com.scott.payment.payment.entity.PaymentCheckoutAttemptDO;
import com.scott.payment.payment.entity.PaymentCheckoutSessionDO;
import com.scott.payment.payment.service.dto.PaymentCheckoutThreeDsResultDTO;
import com.scott.payment.payment.service.dto.PaymentRouteResultDTO;
import com.scott.payment.channel.payment.enums.ChannelThreeDsPhase;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentCheckoutThreeDsService
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : Hosted Checkout 3DS 后端阶段服务；浏览器 Method/Challenge 与回跳恢复由后续专项流程编排。
 * @status : create
 */
public interface PaymentCheckoutThreeDsService {

    /** 兼容未预先准备核心交易的内部调用，由实现自行完成路由。 */
    default PaymentCheckoutThreeDsResultDTO authenticate(PaymentCheckoutSessionDO sessionDO,
                                                         PaymentCheckoutAttemptDO attemptDO,
                                                         PaymentCheckoutPaymentSubmitCommandDTO commandDTO,
                                                         String returnUrl) {
        return authenticate(sessionDO, attemptDO, commandDTO, returnUrl, null);
    }

    /**
     * 对一次收银台付款尝试发起 3DS 初始化。
     *
     * <p>实现只返回认证状态和质询页面信息，不直接写入收银台会话状态；状态落库由
     * {@link PaymentCheckoutService} 统一按 CAS 规则处理，避免渠道结果和平台状态机交叉覆盖。</p>
     *
     * @param sessionDO 收银台会话快照
     * @param attemptDO 本次付款尝试快照
     * @param commandDTO 付款人提交的卡信息和浏览器上下文
     * @param returnUrl 认证完成后回到平台 3DS bridge 的地址
     * @return 3DS 初始化摘要，包含 Method、失败、处理中或可继续认证状态
     */
    PaymentCheckoutThreeDsResultDTO authenticate(PaymentCheckoutSessionDO sessionDO,
                                                 PaymentCheckoutAttemptDO attemptDO,
                                                 PaymentCheckoutPaymentSubmitCommandDTO commandDTO,
                                                 String returnUrl,
                                                 PaymentRouteResultDTO preparedRoute);

    /** 使用已落库的渠道/MID 继续单个 3DS 阶段，禁止重新路由或重新评估策略。 */
    PaymentCheckoutThreeDsResultDTO continueAuthentication(PaymentCheckoutSessionDO sessionDO,
                                                           PaymentCheckoutAttemptDO attemptDO,
                                                           PaymentCheckoutPaymentSubmitCommandDTO commandDTO,
                                                           String returnUrl,
                                                           ChannelThreeDsPhase phase);
}
