package com.scott.payment.payment.client.risk;

import com.scott.payment.payment.client.risk.dto.RiskPaymentEvaluateClientRequestDTO;
import com.scott.payment.payment.client.risk.dto.RiskPaymentEvaluateClientResponseDTO;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RiskInternalClient
 * @date : 2026-07-12 00:00
 * @email : scott_x@163.com
 * @description : service-risk 内部调用客户端契约，位于 service-payment 客户端层，用于隔离支付核心与风控服务通信细节。
 * @status : create
 */
public interface RiskInternalClient {

    /**
     * 调用 service-risk 执行支付路由前风控评估。
     *
     * @param requestDTO 风控评估请求
     * @return 风控评估响应
     */
    RiskPaymentEvaluateClientResponseDTO evaluatePayment(RiskPaymentEvaluateClientRequestDTO requestDTO);
}
