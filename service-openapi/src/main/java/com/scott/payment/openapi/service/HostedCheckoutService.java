package com.scott.payment.openapi.service;

import com.scott.payment.openapi.dto.body.HostedCheckoutBrowserRequestDTOs;
import com.scott.payment.openapi.dto.body.HostedCheckoutSessionCreateRequestDTO;
import com.scott.payment.openapi.vo.checkout.HostedCheckoutPaymentResultVO;
import com.scott.payment.openapi.vo.checkout.HostedCheckoutSessionCreateVO;
import com.scott.payment.openapi.vo.checkout.HostedCheckoutSessionVO;

/**
 * Hosted Checkout 开放接口服务。
 */
public interface HostedCheckoutService {

    /**
     * 创建平台 Hosted Checkout 会话，返回付款人访问 URL 而不是直接发起扣款。
     */
    HostedCheckoutSessionCreateVO createSession(String encryptedData, HostedCheckoutSessionCreateRequestDTO requestDTO);

    /**
     * 付款人打开收银台时查询会话快照和允许的支付方式。
     */
    HostedCheckoutSessionVO querySession(HostedCheckoutBrowserRequestDTOs.SessionQueryRequest requestDTO);

    /**
     * 提交付款人卡信息并推进 3DS/支付流程，卡信息只在本次链路中过境。
     */
    HostedCheckoutPaymentResultVO submitPayment(HostedCheckoutBrowserRequestDTOs.PaymentSubmitRequest requestDTO);

    /**
     * 查询付款尝试结果，供处理中页面轮询展示使用。
     */
    HostedCheckoutPaymentResultVO queryPaymentStatus(HostedCheckoutBrowserRequestDTOs.PaymentStatusRequest requestDTO);

    /**
     * 接收自有 3DS bridge 回跳并转换为 service-payment 内部状态推进命令。
     */
    HostedCheckoutPaymentResultVO handleThreeDsReturn(HostedCheckoutBrowserRequestDTOs.ThreeDsReturnRequest requestDTO);
}
