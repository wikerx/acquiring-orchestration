package com.scott.payment.payment.client.risk;

import com.scott.payment.payment.client.risk.dto.RiskMerchantLimitReservationClientRequestDTO;
import com.scott.payment.payment.client.risk.dto.RiskMerchantLimitReservationClientResponseDTO;
import com.scott.payment.payment.client.risk.dto.RiskPaymentEvaluateClientRequestDTO;
import com.scott.payment.payment.client.risk.dto.RiskPaymentEvaluateClientResponseDTO;
import com.scott.payment.payment.client.risk.dto.RiskThreeDsPolicyClientRequestDTO;
import com.scott.payment.payment.client.risk.dto.RiskThreeDsPolicyClientResponseDTO;

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

    /**
     * 在渠道路由完成后只读评估 3DS 策略，不创建累计限额或频控预占。
     *
     * @param requestDTO 已路由交易维度
     * @return 3DS 策略结果
     */
    default RiskThreeDsPolicyClientResponseDTO evaluateThreeDsPolicy(
            RiskThreeDsPolicyClientRequestDTO requestDTO) {
        throw new UnsupportedOperationException("3DS policy evaluation is not supported");
    }

    /**
     * 撤销支付本地事务失败后遗留的商户累计限额预占。
     *
     * @param requestDTO 只包含稳定业务标识和原因
     * @return 幂等迁移统计
     */
    default RiskMerchantLimitReservationClientResponseDTO cancelMerchantLimitReservation(
            RiskMerchantLimitReservationClientRequestDTO requestDTO) {
        throw new UnsupportedOperationException("merchant limit reservation cancellation is not supported");
    }
}
